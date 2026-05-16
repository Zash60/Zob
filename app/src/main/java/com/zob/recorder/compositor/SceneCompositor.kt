package com.zob.recorder.compositor

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.Scene
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.Source
import com.zob.recorder.model.TextSource
import com.zob.recorder.model.TransitionType
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenGL ES 3.0 scene compositor engine.
 *
 * Renders a scene graph (screen capture + text overlays + image overlays) to
 * an offscreen [Surface] that feeds into [com.zob.recorder.encoder.StreamEncoder]'s
 * MediaCodec input.
 *
 * ## Architecture
 * ```
 * VirtualDisplay ──screen──→ SurfaceTexture ──→ SceneCompositor ──→ Surface ──→ StreamEncoder
 *                    (OES)                      (GL thread 30fps)    (EGL)
 * ```
 *
 * ## Thread model
 * - **GL thread**: owns EGL context, performs all rendering, runs at ~30fps.
 * - **Caller thread**: calls [requestScene], [updateImageTexture], etc.
 * - Synchronisation: thread-safe queues for scene changes and texture uploads;
 *   [AtomicBoolean] for lifecycle control.
 *
 * ## Lifecycle
 * 1. Call [start] to initialise EGL and begin rendering.
 * 2. Call [requestScene] to set/change the active scene.
 * 3. Call [updateImageTexture] to provide Bitmaps for [ImageSource] overlays.
 * 4. Call [stop] to tear down EGL and stop the GL thread.
 */
class SceneCompositor {

    // ── EGL state (GL-thread only) ─────────────────────────────────────────

    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: Surface? = null

    // ── Compositor sub-renderers (GL-thread only) ──────────────────────────

    private var textureRenderer: TextureRenderer? = null
    private var overlayRenderer: OverlayRenderer? = null
    private var transitionRenderer: TransitionRenderer? = null

    // ── Screen capture (GL-thread only) ────────────────────────────────────

    private var screenTextureId = 0
    private var screenSurfaceTexture: SurfaceTexture? = null
    private val screenTexMatrix = FloatArray(16)

    // ── Viewport dimensions (set once at start) ────────────────────────────

    private var viewportWidth = 1920
    private var viewportHeight = 1080

    // ── Scene state (thread-safe) ──────────────────────────────────────────

    private val sceneLock = Any()

    /** The currently active scene (read by GL thread, written under lock). */
    private var currentScene: Scene? = null

    /** The next scene queued for transition (written by caller, read by GL thread). */
    private var pendingScene: Scene? = null

    /** Transition type for the pending scene change. */
    private var pendingTransitionType: TransitionType? = null

    // ── Fade transition helpers (GL-thread only) ──────────────────────────

    /**
     * When true, the GL thread must render the current (old) scene one more
     * time so that [TransitionRenderer] can snapshot it via glBlitFramebuffer.
     */
    private var fadeSnapshotNeeded = false

    /** The scene to render for the snapshot (i.e. the pre-transition scene). */
    private var fadeFromScene: Scene? = null

    /** Duration of the current fade transition. */
    private var fadeDurationMs: Long = 300

    // ── Texture upload queue (thread-safe) ────────────────────────────────

    private val pendingTextureUploads = ConcurrentLinkedQueue<Pair<String, Bitmap>>()

    // ── Lifecycle (thread-safe) ───────────────────────────────────────────

    private val isRunning = AtomicBoolean(false)

    /** The dedicated GL compositing thread. */
    private var glThread: Thread? = null

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "SceneCompositor"

        /** Target frame interval in nanoseconds (~33.3ms for 30fps). */
        private const val FRAME_INTERVAL_NS = 1_000_000_000L / 30

        /** EGL attribute: red, green, blue, alpha bits. */
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    // ═════════════════════════════════════════════════════════════════════�
    // Public API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Starts the compositor.
     *
     * Creates the GL thread, initialises EGL with the given [outputSurface],
     * and attaches the [screenCaptureTexture] for consuming VirtualDisplay
     * frames.
     *
     * @param outputSurface        The [Surface] from
     *                             [com.zob.recorder.encoder.StreamEncoder.getInputSurface].
     * @param screenCaptureTexture The [SurfaceTexture] created by the
     *                             [android.hardware.display.VirtualDisplay].
     * @param width                Rendering width in pixels (typically 1920).
     * @param height               Rendering height in pixels (typically 1080).
     */
    fun start(
        outputSurface: Surface,
        screenCaptureTexture: SurfaceTexture,
        width: Int = 1920,
        height: Int = 1080,
    ) {
        if (isRunning.get()) {
            Log.w(TAG, "start() called while already running — ignoring")
            return
        }

        this.outputSurface = outputSurface
        this.screenSurfaceTexture = screenCaptureTexture
        this.viewportWidth = width
        this.viewportHeight = height

        isRunning.set(true)
        glThread = Thread(::glLoop, "GL-Compositor").apply { start() }
        Log.i(TAG, "Compositor started: ${width}x${height} @ 30fps")
    }

    /**
     * Stops the compositor and releases all GL resources.
     *
     * Safe to call when not running. Blocks until the GL thread exits.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        glThread?.join(2000)
        glThread = null
        Log.i(TAG, "Compositor stopped")
    }

    /**
     * Requests a scene transition.
     *
     * If no scene is currently active, [scene] becomes the active scene
     * immediately (no transition). Otherwise a transition (cut or fade)
     * is applied according to [scene.transitionType].
     *
     * This method is thread-safe and returns immediately.
     */
    fun requestScene(scene: Scene) {
        synchronized(sceneLock) {
            pendingScene = scene
            pendingTransitionType = scene.transitionType
        }
    }

    /**
     * Provides a [Bitmap] for an [ImageSource] overlay.
     *
     * The bitmap is uploaded to a GL texture on the compositing thread.
     * After calling this, the image source will be rendered in subsequent
     * frames.
     *
     * @param sourceId The [Source.id] of the [ImageSource].
     * @param bitmap   The image data (recycled after upload).
     */
    fun updateImageTexture(sourceId: String, bitmap: Bitmap) {
        if (!isRunning.get()) return
        pendingTextureUploads.add(Pair(sourceId, bitmap))
    }

    /**
     * Invalidates the cached overlay texture for [sourceId], forcing a
     * re-render on the next frame (used when a text source content changes).
     */
    fun invalidateSourceTexture(sourceId: String) {
        if (!isRunning.get()) return
        // Invalidate is done on the GL thread via the pending textures queue.
        // We use a sentinel: null bitmap signals invalidation.
        pendingTextureUploads.add(Pair(sourceId, null))
    }

    /**
     * Returns the currently active scene, or null if none has been set.
     */
    fun getCurrentScene(): Scene? = currentScene

    // ═════════════════════════════════════════════════════════════════════
    // GL thread entry point
    // ═════════════════════════════════════════════════════════════════════

    private fun glLoop() {
        try {
            if (!initEGL()) return
            if (!initRenderers()) return
            attachScreenTexture()

            // Main rendering loop
            while (isRunning.get()) {
                val frameStart = System.nanoTime()

                processPendingTextureUploads()
                processPendingTransitions()
                renderFrame()

                EGL14.eglSwapBuffers(eglDisplay!!, eglSurface!!)

                paceFrame(frameStart)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in GL loop", e)
        } finally {
            releaseGL()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // EGL initialisation / teardown
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Initialises EGL 1.4 with an offscreen rendering context and a window
     * surface backed by [outputSurface].
     */
    private fun initEGL(): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return false
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            return false
        }

        // Choose config
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1, // required for MediaCodec input
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            Log.e(TAG, "eglChooseConfig failed")
            EGL14.eglTerminate(display)
            return false
        }

        // Create ES 3.0 context (min API 29 guarantees ES 3.0 support)
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE,
        )
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed for ES 3.0")
            EGL14.eglTerminate(display)
            return false
        }

        // Create window surface from the encoder's input Surface
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglWinSurface = EGL14.eglCreateWindowSurface(display, configs[0], outputSurface!!, surfaceAttribs, 0)
        if (eglWinSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed")
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return false
        }

        // Make current
        if (!EGL14.eglMakeCurrent(display, eglWinSurface, eglWinSurface, context)) {
            Log.e(TAG, "eglMakeCurrent failed")
            EGL14.eglDestroySurface(display, eglWinSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return false
        }

        eglDisplay = display
        eglContext = context
        eglSurface = eglWinSurface

        Log.i(TAG, "EGL initialized: ${viewportWidth}x${viewportHeight}")
        return true
    }

    /**
     * Initialises all sub-renderers.
     */
    private fun initRenderers(): Boolean {
        val tr = TextureRenderer()
        if (!tr.setup()) {
            Log.e(TAG, "TextureRenderer.setup() failed")
            return false
        }
        textureRenderer = tr

        val or = OverlayRenderer()
        if (!or.setup()) {
            Log.e(TAG, "OverlayRenderer.setup() failed")
            return false
        }
        overlayRenderer = or

        val txnr = TransitionRenderer()
        if (!txnr.setup(viewportWidth, viewportHeight)) {
            Log.e(TAG, "TransitionRenderer.setup() failed")
            return false
        }
        transitionRenderer = txnr

        // Clear to black
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        EGL14.eglSwapBuffers(eglDisplay!!, eglSurface!!)

        Log.i(TAG, "Renderers initialized")
        return true
    }

    /**
     * Attaches the screen capture [SurfaceTexture] to our GL context.
     *
     * The SurfaceTexture was created by [com.zob.recorder.service.ScreenRecorderService]
     * with `SurfaceTexture(0)`. We create an OES texture and attach it to
     * our GL context.
     */
    private fun attachScreenTexture() {
        val st = screenSurfaceTexture ?: return

        screenTextureId = GLCanvasUtil.createOESTexture()
        if (screenTextureId == 0) {
            Log.e(TAG, "Failed to create OES texture for screen capture")
            return
        }

        try {
            st.attachToGLContext(screenTextureId)
            Log.i(TAG, "Screen SurfaceTexture attached to GL context")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach SurfaceTexture to GL context", e)
            GLCanvasUtil.deleteTexture(screenTextureId)
            screenTextureId = 0
        }
    }

    /**
     * Tears down EGL and releases all GL resources.
     */
    private fun releaseGL() {
        // Detach SurfaceTexture
        try {
            screenSurfaceTexture?.detachFromGLContext()
        } catch (_: Exception) {
            // Ignore — context may already be destroyed
        }
        screenSurfaceTexture = null
        GLCanvasUtil.deleteTexture(screenTextureId)
        screenTextureId = 0

        // Release renderers
        textureRenderer?.release()
        overlayRenderer?.release()
        transitionRenderer?.release()
        textureRenderer = null
        overlayRenderer = null
        transitionRenderer = null

        // Destroy EGL
        val display = eglDisplay ?: return
        val context = eglContext ?: return
        val surface = eglSurface ?: return

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)

        eglDisplay = null
        eglContext = null
        eglSurface = null
        outputSurface = null
    }

    // ═════════════════════════════════════════════════════════════════════
    // Scene / transition management
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Processes a pending scene transition set by [requestScene].
     *
     * Must be called at the **start** of each frame, before [renderFrame].
     *
     * - **First scene**: set immediately, no transition.
     * - **Cut**: switch immediately, invalidate overlay cache.
     * - **Fade**: save the old scene for snapshot, switch to new scene,
     *   set [fadeSnapshotNeeded] so that the next frame captures the old
     *   scene into the transition FBO.
     */
    private fun processPendingTransitions() {
        val newScene: Scene?
        val transitionType: TransitionType?
        synchronized(sceneLock) {
            newScene = pendingScene
            transitionType = pendingTransitionType
            pendingScene = null
            pendingTransitionType = null
        }

        if (newScene == null) return

        val type = transitionType ?: TransitionType.CUT

        if (currentScene == null) {
            // First scene — set immediately
            currentScene = newScene
            Log.d(TAG, "First scene set: ${newScene.name}")
            return
        }

        when (type) {
            TransitionType.CUT -> {
                currentScene = newScene
                overlayRenderer?.invalidateCache()
                Log.d(TAG, "Cut to scene: ${newScene.name}")
            }

            TransitionType.FADE -> {
                // Save old scene for snapshot, switch to new scene
                fadeFromScene = currentScene
                fadeDurationMs = newScene.transitionDurationMs.coerceAtLeast(16)
                fadeSnapshotNeeded = true
                currentScene = newScene
                Log.d(TAG, "Fade to scene: ${newScene.name}, duration=${fadeDurationMs}ms")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Per-frame rendering
    // ═════════════════════════════════════════════════════════════════════

    /**
     * The core rendering pipeline for a single frame.
     *
     * Handles three rendering modes:
     * 1. **Fade snapshot frame**: render the old scene, take a snapshot
     *    into the [TransitionRenderer] FBO, then render the first
     *    cross-fade frame (old snapshot + new scene).
     * 2. **Active fade**: render the snapshot (old scene) with decreasing
     *    alpha, then the new scene with increasing alpha using blending.
     * 3. **Normal**: render the current scene at full opacity.
     */
    private fun renderFrame() {
        // ── Mode 1: Fade snapshot frame ──────────────────────────────────
        if (fadeSnapshotNeeded && fadeFromScene != null) {
            renderFadeSnapshot()
            return
        }

        // ── Mode 2: Active fade transition ───────────────────────────────
        val txn = transitionRenderer ?: return
        if (txn.isActive()) {
            val progress = txn.getProgress()
            if (progress < 1f) {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                txn.renderSnapshot(1f - progress)

                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                renderCurrentScene(progress)
                GLES30.glDisable(GLES30.GL_BLEND)
            } else {
                // Transition completed this frame
                renderCurrentScene(1f)
            }
            return
        }

        // ── Mode 3: Normal rendering ─────────────────────────────────────
        renderCurrentScene(1f)
    }

    /**
     * Renders the old scene for the fade snapshot, captures it via
     * [TransitionRenderer.startTransition], then renders the first
     * cross-fade frame.
     */
    private fun renderFadeSnapshot() {
        val txn = transitionRenderer ?: return
        val savedScene = currentScene

        // Step 1: Render the old scene to the back buffer
        currentScene = fadeFromScene
        renderCurrentScene(1f)

        // Step 2: Capture snapshot (blits back buffer → FBO texture)
        txn.startTransition(TransitionType.FADE, fadeDurationMs)

        // Step 3: Restore new scene and invalidate overlay textures
        currentScene = savedScene
        overlayRenderer?.invalidateCache()

        // Step 4: Clear and render the first cross-fade frame
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        txn.renderSnapshot(1f) // old scene at full opacity

        // Progress is essentially 0 here, but we blend the new scene at
        // a very low alpha so the transition starts smoothly.
        val initialProgress = 0.01f
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        renderCurrentScene(initialProgress)
        GLES30.glDisable(GLES30.GL_BLEND)

        fadeSnapshotNeeded = false
        fadeFromScene = null
    }

    /**
     * Renders all visual elements of [currentScene] into the current
     * framebuffer.
     *
     * @param globalAlpha Multiplier applied to all source opacities (used
     *                    during fade transitions).
     */
    private fun renderCurrentScene(globalAlpha: Float) {
        val scene = currentScene
        if (scene == null) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }

        // Update screen capture frame
        updateScreenCapture()

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // Sort sources by z-order (ascending — lower z renders first)
        val sortedSources = scene.sources
            .filter { it.isVisible }
            .sortedBy { it.zOrder }

        for (source in sortedSources) {
            val alpha = (source.opacity * globalAlpha).coerceIn(0f, 1f)
            if (alpha <= 0f) continue

            when (source) {
                is ScreenSource -> renderScreenSource(source, alpha)
                is TextSource -> renderTextSource(source, alpha)
                is ImageSource -> renderImageSource(source, alpha)
            }
        }
    }

    // ── Screen capture ─────────────────────────────────────────────────────

    /**
     * Attempts to update the screen capture texture from the SurfaceTexture.
     *
     * If the SurfaceTexture is not yet attached or produces an error, the
     * last known good texture image is reused.
     */
    private fun updateScreenCapture() {
        val st = screenSurfaceTexture ?: return
        if (screenTextureId == 0) return

        try {
            st.updateTexImage()
            st.getTransformMatrix(screenTexMatrix)
        } catch (e: Exception) {
            // SurfaceTexture may not have a frame yet, or was released
            Log.v(TAG, "updateTexImage failed (expected during startup): ${e.message}")
        }
    }

    private fun renderScreenSource(source: ScreenSource, alpha: Float) {
        val tr = textureRenderer ?: return
        if (screenTextureId == 0) return

        tr.draw(screenTextureId, screenTexMatrix, alpha)
    }

    // ── Text sources ──────────────────────────────────────────────────────

    /**
     * Renders a [TextSource] by:
     * 1. Checking the overlay texture cache for [source.id].
     * 2. If not cached: rendering the text to a [Bitmap] via [Canvas],
     *    uploading to a GL texture, and caching it.
     * 3. Drawing the overlay quad at the configured position/size/opacity.
     */
    private fun renderTextSource(source: TextSource, alpha: Float) {
        val or = overlayRenderer ?: return
        if (source.text.isBlank()) return

        if (source.width <= 0f || source.height <= 0f) return

        var texId = or.getTexture(source.id)

        if (texId == 0) {
            // Render text to bitmap and upload
            val bitmap = GLCanvasUtil.drawTextToBitmap(
                text = source.text,
                fontSize = source.fontSize,
                color = source.color.toInt(),
                width = source.width.toInt().coerceAtLeast(1),
                height = source.height.toInt().coerceAtLeast(1),
            )
            or.setTexture(source.id, bitmap)
            texId = or.getTexture(source.id)
        }

        if (texId == 0) return

        or.renderOverlay(
            textureId = texId,
            sourceX = source.positionX,
            sourceY = source.positionY,
            sourceWidth = source.width,
            sourceHeight = source.height,
            viewportW = viewportWidth,
            viewportH = viewportHeight,
            alpha = alpha,
        )
    }

    // ── Image sources ──────────────────────────────────────────────────────

    /**
     * Renders an [ImageSource] if its texture has been cached via
     * [updateImageTexture]. If no texture is available the source is
     * silently skipped.
     */
    private fun renderImageSource(source: ImageSource, alpha: Float) {
        val or = overlayRenderer ?: return
        if (source.width <= 0f || source.height <= 0f) return

        val texId = or.getTexture(source.id)
        if (texId == 0) return // bitmap not yet provided by caller

        or.renderOverlay(
            textureId = texId,
            sourceX = source.positionX,
            sourceY = source.positionY,
            sourceWidth = source.width,
            sourceHeight = source.height,
            viewportW = viewportWidth,
            viewportH = viewportHeight,
            alpha = alpha,
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    // Async work processing
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Processes queued texture uploads and cache invalidations.
     *
     * A non-null [Bitmap] triggers an upload; a null bitmap triggers a
     * cache invalidation for the given source ID.
     */
    private fun processPendingTextureUploads() {
        val or = overlayRenderer ?: return

        while (true) {
            val item = pendingTextureUploads.poll() ?: break
            val (sourceId, bitmap) = item

            if (bitmap != null) {
                or.setTexture(sourceId, bitmap)
            } else {
                or.invalidateSource(sourceId)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Frame pacing
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Sleeps for the remainder of the frame interval to maintain ~30fps.
     */
    private fun paceFrame(frameStartNs: Long) {
        val elapsed = System.nanoTime() - frameStartNs
        val remainingUs = (FRAME_INTERVAL_NS - elapsed) / 1000
        if (remainingUs > 0) {
            try {
                Thread.sleep(remainingUs / 1000, (remainingUs % 1000).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
