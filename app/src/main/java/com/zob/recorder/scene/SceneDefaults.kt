package com.zob.recorder.scene

import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.Scene
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.TextSource
import com.zob.recorder.model.TransitionType
import java.util.UUID

/**
 * Factory functions for creating default [Scene] and [com.zob.recorder.model.Source] instances.
 *
 * Used by [SceneManager] during initialisation and by the UI layer (e.g. "Add Scene" flow).
 */
object SceneDefaults {

    /**
     * Creates a default [ScreenSource] targeting the full viewport.
     */
    fun defaultScreenSource(): ScreenSource = ScreenSource(
        id = UUID.randomUUID().toString()
    )

    /**
     * Creates a single-scene preset with only a [ScreenSource].
     *
     * This is the recommended default for new installations.
     */
    fun createDefaultScene(): Scene = Scene(
        id = UUID.randomUUID().toString(),
        name = "Scene 1",
        sources = listOf(defaultScreenSource()),
        transitionType = TransitionType.CUT,
        sortOrder = 0
    )

    /**
     * Creates a scene with a [ScreenSource] plus a [TextSource] overlay.
     *
     * @param name     Scene name (default: "Text Scene").
     * @param text     The text to display (default: "Hello, Zob!").
     * @param fontSize Font size in pixels (default: 36).
     * @param sortOrder Insertion order (default: 0).
     */
    fun createSceneWithText(
        name: String = "Text Scene",
        text: String = "Hello, Zob!",
        fontSize: Int = 36,
        sortOrder: Int = 0
    ): Scene {
        val screenSource = defaultScreenSource()
        val textSource = TextSource(
            id = UUID.randomUUID().toString(),
            name = "Text Overlay",
            text = text,
            fontSize = fontSize,
            zOrder = 1 // rendered above the screen capture
        )
        return Scene(
            id = UUID.randomUUID().toString(),
            name = name,
            sources = listOf(screenSource, textSource),
            sortOrder = sortOrder
        )
    }

    /**
     * Creates a scene with a [ScreenSource] plus an empty [ImageSource] overlay
     * (caller must set [ImageSource.imageUri] before the image will render).
     *
     * @param name      Scene name (default: "Image Scene").
     * @param sortOrder Insertion order (default: 0).
     */
    fun createSceneWithImage(
        name: String = "Image Scene",
        sortOrder: Int = 0
    ): Scene {
        val screenSource = defaultScreenSource()
        val imageSource = ImageSource(
            id = UUID.randomUUID().toString(),
            name = "Image Overlay",
            zOrder = 1 // rendered above the screen capture
        )
        return Scene(
            id = UUID.randomUUID().toString(),
            name = name,
            sources = listOf(screenSource, imageSource),
            sortOrder = sortOrder
        )
    }

    /**
     * Returns a list containing a single [createDefaultScene] — useful as a
     * starting preset when no persisted scenes exist.
     */
    fun createDefaultScenes(): List<Scene> = listOf(
        createDefaultScene()
    )
}
