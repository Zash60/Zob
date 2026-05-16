package com.zob.recorder.ui.screens.sceneeditor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zob.recorder.model.ImageScaleType
import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.Scene
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.Source
import com.zob.recorder.model.TextSource
import com.zob.recorder.model.TransitionType
import com.zob.recorder.scene.SceneManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SceneEditorUiState(
    val scene: Scene? = null,
    val selectedSourceId: String? = null,
    val isDirty: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class SceneEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sceneManager: SceneManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneEditorUiState())
    val uiState: StateFlow<SceneEditorUiState> = _uiState.asStateFlow()

    private var sceneId: String? = null

    init {
        observeSceneChanges()
    }

    fun loadScene(id: String) {
        sceneId = id
        val scene = sceneManager.scenes.value.find { it.id == id }
        _uiState.update {
            it.copy(
                scene = scene,
                isLoading = false,
                selectedSourceId = scene?.sources?.firstOrNull()?.id
            )
        }
    }

    private fun observeSceneChanges() {
        viewModelScope.launch {
            sceneManager.scenes
                .distinctUntilChanged()
                .collect { scenes ->
                    val currentId = sceneId ?: return@collect
                    val updatedScene = scenes.find { it.id == currentId }
                    _uiState.update { currentState ->
                        currentState.copy(scene = updatedScene)
                    }
                }
        }
    }

    // ── Source Actions ──────────────────────────────────────────────────────

    fun addTextSource() {
        val id = sceneId ?: return
        val source = TextSource(
            id = UUID.randomUUID().toString(),
            name = "Text ${nextSourceNumber(TextSource::class)}",
            text = "Text",
            fontSize = 24,
            positionX = 100f,
            positionY = 100f,
            width = 400f,
            height = 100f,
            opacity = 1f,
            zOrder = nextZOrder()
        )
        sceneManager.addSource(id, source)
        _uiState.update { it.copy(selectedSourceId = source.id, isDirty = true) }
    }

    fun addImageSource(uri: Uri) {
        val id = sceneId ?: return
        val source = ImageSource(
            id = UUID.randomUUID().toString(),
            name = "Image ${nextSourceNumber(ImageSource::class)}",
            imageUri = uri.toString(),
            positionX = 100f,
            positionY = 100f,
            width = 400f,
            height = 300f,
            opacity = 1f,
            zOrder = nextZOrder()
        )
        sceneManager.addSource(id, source)
        _uiState.update { it.copy(selectedSourceId = source.id, isDirty = true) }
    }

    fun removeSource(sourceId: String) {
        val id = sceneId ?: return
        sceneManager.removeSource(id, sourceId)
        val currentScene = _uiState.value.scene
        val remainingSources = currentScene?.sources?.filter { it.id != sourceId }
        _uiState.update {
            it.copy(
                selectedSourceId = if (it.selectedSourceId == sourceId) {
                    remainingSources?.firstOrNull()?.id
                } else {
                    it.selectedSourceId
                },
                isDirty = true
            )
        }
    }

    fun updateSourcePosition(sourceId: String, x: Float, y: Float) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        val source = scene.sources.find { it.id == sourceId } ?: return
        val updated = when (source) {
            is ScreenSource -> source.copy(positionX = x, positionY = y)
            is TextSource -> source.copy(positionX = x, positionY = y)
            is ImageSource -> source.copy(positionX = x, positionY = y)
        }
        sceneManager.updateSource(id, updated)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun updateSourceSize(sourceId: String, width: Float, height: Float) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        val source = scene.sources.find { it.id == sourceId } ?: return
        val updated = when (source) {
            is ScreenSource -> source.copy(width = width, height = height)
            is TextSource -> source.copy(width = width, height = height)
            is ImageSource -> source.copy(width = width, height = height)
        }
        sceneManager.updateSource(id, updated)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun updateSourceOpacity(sourceId: String, opacity: Float) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        val source = scene.sources.find { it.id == sourceId } ?: return
        val updated = when (source) {
            is ScreenSource -> source.copy(opacity = opacity)
            is TextSource -> source.copy(opacity = opacity)
            is ImageSource -> source.copy(opacity = opacity)
        }
        sceneManager.updateSource(id, updated)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun updateTextSourceConfig(sourceId: String, text: String? = null, fontSize: Int? = null, color: Long? = null) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        val source = scene.sources.find { it.id == sourceId } as? TextSource ?: return
        val updated = source.copy(
            text = text ?: source.text,
            fontSize = fontSize ?: source.fontSize,
            color = color ?: source.color
        )
        sceneManager.updateSource(id, updated)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun updateImageSourceConfig(sourceId: String, uri: String? = null, scaleType: ImageScaleType? = null) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        val source = scene.sources.find { it.id == sourceId } as? ImageSource ?: return
        val updated = source.copy(
            imageUri = uri ?: source.imageUri,
            scaleType = scaleType ?: source.scaleType
        )
        sceneManager.updateSource(id, updated)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun reorderSources(sourceIds: List<String>) {
        val id = sceneId ?: return
        sceneManager.reorderSources(id, sourceIds)
        _uiState.update { it.copy(isDirty = true) }
    }

    fun setTransition(type: TransitionType) {
        val id = sceneId ?: return
        val scene = _uiState.value.scene ?: return
        sceneManager.updateScene(scene.copy(transitionType = type))
        _uiState.update { it.copy(isDirty = true) }
    }

    fun selectSource(sourceId: String?) {
        _uiState.update { it.copy(selectedSourceId = sourceId) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun nextZOrder(): Int {
        val scene = _uiState.value.scene ?: return 1
        return (scene.sources.maxOfOrNull { it.zOrder } ?: 0) + 1
    }

    private fun <T : Source> nextSourceNumber(clazz: Class<T>): Int {
        val scene = _uiState.value.scene ?: return 1
        val count = scene.sources.count { clazz.isInstance(it) }
        return count + 1
    }
}
