package com.zob.recorder.scene

import android.content.Context
import com.zob.recorder.model.Scene
import com.zob.recorder.model.Source
import com.zob.recorder.model.TransitionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository for scene CRUD, active-scene tracking, source management
 * per scene, and debounced JSON persistence.
 *
 * ## Thread safety
 * All state mutations happen on the caller's thread (typically the UI thread
 * when called from Compose). [MutableStateFlow] provides thread-safe value
 * reads/writes. The debounced save writes to a background file via
 * [Dispatchers.IO].
 *
 * ## Persistence
 * Scenes are saved as a single JSON file in `context.filesDir/scenes/scenes.json`
 * using [kotlinx.serialization]. Writes are debounced at 500 ms to avoid
 * excessive I/O during rapid updates.
 *
 * ## Integration
 * [SceneCompositor.requestScene] should be called with the active scene from
 * [activeScene] whenever a new scene becomes active.
 */
@Singleton
class SceneManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── JSON engine ─────────────────────────────────────────────────────────

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // ── Coroutine scope for background save ─────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    // ── Observable state ────────────────────────────────────────────────────

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    private val _activeSceneId = MutableStateFlow<String?>(null)
    val activeSceneId: StateFlow<String?> = _activeSceneId.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    /**
     * Derives the full [Scene] object matching [activeSceneId] from [scenes].
     * Emits `null` when no scene is active or the active ID no longer exists.
     */
    val activeScene: StateFlow<Scene?> = combine(
        _scenes, _activeSceneId
    ) { allScenes, id ->
        allScenes.find { it.id == id }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // ── Initialisation ──────────────────────────────────────────────────────

    init {
        loadScenes()
        if (_scenes.value.isEmpty()) {
            val default = SceneDefaults.createDefaultScene()
            _scenes.value = listOf(default)
            _activeSceneId.value = default.id
            saveToJson()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scene CRUD
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new [Scene] with the given [name] and optional [sources].
     * The scene is appended to the list and assigned the next [Scene.sortOrder].
     *
     * @return The newly created [Scene] (with an auto-generated [Scene.id]).
     */
    fun createScene(
        name: String,
        sources: List<Source> = listOf(SceneDefaults.defaultScreenSource())
    ): Scene {
        val scene = Scene(
            id = UUID.randomUUID().toString(),
            name = name,
            sources = sources,
            sortOrder = _scenes.value.size
        )
        _scenes.value = _scenes.value + scene
        scheduleSave()
        return scene
    }

    /**
     * Deletes the scene identified by [id].
     * If the deleted scene was the active scene, the first remaining scene
     * becomes active (or `null` if no scenes remain).
     */
    fun deleteScene(id: String) {
        _scenes.value = _scenes.value.filter { it.id != id }
        if (_activeSceneId.value == id) {
            _activeSceneId.value = _scenes.value.firstOrNull()?.id
        }
        scheduleSave()
    }

    /**
     * Replaces an entire scene with the provided [scene] (matched by [Scene.id]).
     */
    fun updateScene(scene: Scene) {
        _scenes.value = _scenes.value.map { if (it.id == scene.id) scene else it }
        scheduleSave()
    }

    /**
     * Re-orders all scenes according to [sceneIds].
     * Each scene's [Scene.sortOrder] is updated to match its index in the list.
     */
    fun reorderScenes(sceneIds: List<String>) {
        val sceneMap = _scenes.value.associateBy { it.id }
        _scenes.value = sceneIds.mapIndexed { index, id ->
            sceneMap[id]?.copy(sortOrder = index)
                ?: error("Scene $id not found during reorder")
        }
        scheduleSave()
    }

    /**
     * Sets the active scene to the one identified by [id].
     *
     * @param id         The target [Scene.id].
     * @param transition Optional [TransitionType] to apply. When non-null,
     *                   [isTransitioning] is set to `true`. Call
     *                   [onTransitionComplete] when the transition finishes.
     */
    fun setActiveScene(id: String, transition: TransitionType? = null) {
        if (_scenes.value.none { it.id == id }) return
        _activeSceneId.value = id
        if (transition != null) {
            _isTransitioning.value = true
        }
        scheduleSave()
    }

    /**
     * Marks the current scene transition as complete (resets [isTransitioning]).
     * Typically called by [com.zob.recorder.compositor.SceneCompositor] after
     * a fade transition finishes.
     */
    fun onTransitionComplete() {
        _isTransitioning.value = false
    }

    /**
     * Duplicates an existing scene, appending " (Copy)" to its name.
     *
     * @return The newly created [Scene], or `null` if [id] was not found.
     */
    fun duplicateScene(id: String): Scene? {
        val original = _scenes.value.find { it.id == id } ?: return null
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            sortOrder = _scenes.value.size
        )
        _scenes.value = _scenes.value + duplicate
        scheduleSave()
        return duplicate
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Source management (nested under a scene)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Adds a [source] to the scene identified by [sceneId].
     */
    fun addSource(sceneId: String, source: Source) {
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == sceneId) {
                scene.copy(sources = scene.sources + source)
            } else scene
        }
        scheduleSave()
    }

    /**
     * Removes the source with [sourceId] from the scene identified by [sceneId].
     */
    fun removeSource(sceneId: String, sourceId: String) {
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == sceneId) {
                scene.copy(sources = scene.sources.filter { it.id != sourceId })
            } else scene
        }
        scheduleSave()
    }

    /**
     * Replaces the source (matched by [Source.id]) within the scene identified
     * by [sceneId] with the provided [source].
     */
    fun updateSource(sceneId: String, source: Source) {
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == sceneId) {
                scene.copy(sources = scene.sources.map { if (it.id == source.id) source else it })
            } else scene
        }
        scheduleSave()
    }

    /**
     * Re-orders the sources within a scene according to [sourceIds].
     * The list must contain all source IDs for that scene.
     */
    fun reorderSources(sceneId: String, sourceIds: List<String>) {
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == sceneId) {
                val sourceMap = scene.sources.associateBy { it.id }
                scene.copy(sources = sourceIds.mapNotNull { sourceMap[it] })
            } else scene
        }
        scheduleSave()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON persistence
    // ═══════════════════════════════════════════════════════════════════════

    private val scenesDir: File
        get() = File(context.filesDir, "scenes").also { it.mkdirs() }

    /**
     * Schedules a debounced JSON write. Multiple mutations within
     * [SAVE_DEBOUNCE_MS] are collapsed into a single file write.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveToJson()
        }
    }

    internal fun saveToJson() {
        try {
            val file = File(scenesDir, SCENES_FILE_NAME)
            val data = SceneData(
                scenes = _scenes.value,
                activeSceneId = _activeSceneId.value
            )
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save scenes", e)
        }
    }

    internal fun loadScenes() {
        try {
            val file = File(scenesDir, SCENES_FILE_NAME)
            if (file.exists()) {
                val data = json.decodeFromString<SceneData>(file.readText())
                _scenes.value = data.scenes
                _activeSceneId.value = data.activeSceneId
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load scenes", e)
        }
    }

    /**
     * Cancels the internal coroutine scope. Call when the manager is no longer
     * needed (e.g. during instrumented tests).
     */
    fun onCleared() {
        scope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Nested types & constants
    // ═══════════════════════════════════════════════════════════════════════

    @Serializable
    data class SceneData(
        val scenes: List<Scene>,
        val activeSceneId: String? = null
    )

    companion object {
        private const val TAG = "SceneManager"
        private const val SAVE_DEBOUNCE_MS = 500L
        private const val SCENES_FILE_NAME = "scenes.json"
    }
}
