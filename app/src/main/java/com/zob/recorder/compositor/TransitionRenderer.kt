package com.zob.recorder.compositor

import android.opengl.GLES30
import android.util.Log
import com.zob.recorder.model.TransitionType

/**
 * Manages scene transition state and renders cross-fade / cut transitions.
 *
 * ## Cut transition
 * Instant swap — no frame blending required.
 *
 * ## Fade transition
 * Cross-fade that blends from the old scene to the new scene over
 * [durationMs] milliseconds using an FBO snapshot:
 *
 * 1. [startTransition] is called — the current frame is snapshotted into an
 *    FBO texture via [glBlitFramebuffer].
 * 2. Each subsequent frame, [renderTransitionFrame] draws the snapshot
 *    (old scene) with decreasing alpha and expects the caller to draw the
 *    new scene on top with increasing alpha.
 * 3. When [getProgress] reaches 1.0 the transition is complete.
 *
 * ## Lifecycle
 * 1. Call [setup] once after the GL context is current.
 * 2. Call [startTransition] when a scene change is triggered.
 * 3. Call [renderTransitionFrame] at the start of each frame during a fade.
 * 4. Call [release] to free GL resources.
 */
class TransitionRenderer {

    private var snapshotFboId = 0
    private var snapshotTexId = 0
    private var programId = 0
    private var vboId = 0
    private var eboId = 0

    // Uniform locations
    private var uTexMatrixLoc = -1
    private var uTextureLoc = -1
    private var uAlphaLoc = -1

    private var isSetup = false

    // ── Transition state ──────────────────────────────────────────────────

    @Volatile
    private var active = false

    @Volatile
    private var transitionType = TransitionType.CUT

    private var startTimeNs = 0L
    private var durationMs = 0L
    private var viewportWidth = 0
    private var viewportHeight = 0

    // ── Shader sources (ES 3.0, plain 2D sampler) ────────────────────────

    companion object {
        private const val TAG = "TransitionRenderer"

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 1.0);
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec2 vTexCoord;
            out vec4 fragColor;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            void main() {
                fragColor = texture(uTexture, vTexCoord) * uAlpha;
            }
        """
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Creates the shader program, quad buffers, and FBO.
     *
     * Must be called on the GL thread with the EGL context current.
     *
     * @param width  Viewport width in pixels (used for FBO dimensions).
     * @param height Viewport height in pixels.
     * @return `true` on success.
     */
    fun setup(width: Int, height: Int): Boolean {
        viewportWidth = width
        viewportHeight = height

        // Shader program for rendering the snapshot texture
        programId = GLCanvasUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programId == 0) {
            Log.e(TAG, "Failed to create snapshot shader program")
            return false
        }

        vboId = GLCanvasUtil.createQuadVBO()
        if (vboId == 0) {
            Log.e(TAG, "Failed to create quad VBO")
            release()
            return false
        }

        eboId = GLCanvasUtil.createQuadEBO()
        if (eboId == 0) {
            Log.e(TAG, "Failed to create quad EBO")
            release()
            return false
        }

        uTexMatrixLoc = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        uTextureLoc = GLES30.glGetUniformLocation(programId, "uTexture")
        uAlphaLoc = GLES30.glGetUniformLocation(programId, "uAlpha")

        // FBO for snapshot
        val (fboId, texId) = GLCanvasUtil.createFBO(width, height)
        if (fboId == 0 || texId == 0) {
            Log.e(TAG, "Failed to create snapshot FBO")
            release()
            return false
        }
        snapshotFboId = fboId
        snapshotTexId = texId

        isSetup = true
        return true
    }

    // ── Transition control ────────────────────────────────────────────────

    /**
     * Starts a scene transition. For [TransitionType.FADE], the current
     * framebuffer is immediately snapshotted.
     *
     * Must be called on the GL thread with the EGL context current and the
     * desired "old scene" already rendered to the default framebuffer.
     *
     * @param type       The transition type.
     * @param durationMs Duration of the transition in milliseconds (ignored
     *                   for [TransitionType.CUT]).
     */
    fun startTransition(type: TransitionType, durationMs: Long) {
        if (!isSetup) return

        transitionType = type

        when (type) {
            TransitionType.CUT -> {
                // Instant — no snapshot needed, no per-frame work.
                active = false
            }

            TransitionType.FADE -> {
                // Snapshot the current framebuffer before rendering the new scene.
                takeSnapshot()
                startTimeNs = System.nanoTime()
                this.durationMs = durationMs.coerceAtLeast(16) // minimum ~1 frame
                active = true
                Log.d(TAG, "Fade transition started, duration=${durationMs}ms")
            }
        }
    }

    /**
     * Returns the current transition progress in [0f..1f].
     * Returns 1f if no transition is active.
     */
    fun getProgress(): Float {
        if (!active) return 1f
        val elapsed = (System.nanoTime() - startTimeNs) / 1_000_000L
        val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        if (progress >= 1f) {
            active = false
        }
        return progress
    }

    /**
     * Whether a transition is currently in progress.
     */
    fun isActive(): Boolean = active

    /**
     * The transition type for the current (or most recent) transition.
     */
    fun getTransitionType(): TransitionType = transitionType

    // ── Rendering ─────────────────────────────────────────────────────────

    /**
     * Renders the snapshot texture (the "old scene") as a full-screen quad
     * with the given [alpha].
     *
     * This should be called at the start of each frame during a fade
     * transition, **before** the new scene is drawn. The caller then draws
     * the new scene on top (with blending enabled) to achieve a cross-fade.
     *
     * Calling this when no snapshot is available or when idle is a no-op.
     */
    fun renderSnapshot(alpha: Float) {
        if (!isSetup || snapshotTexId == 0 || !active) return

        GLES30.glUseProgram(programId)

        // Vertex attributes
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            GLCanvasUtil.POSITION_SIZE,
            GLES30.GL_FLOAT,
            false,
            GLCanvasUtil.STRIDE_BYTES,
            GLCanvasUtil.POSITION_OFFSET,
        )

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            GLCanvasUtil.TEXCOORD_SIZE,
            GLES30.GL_FLOAT,
            false,
            GLCanvasUtil.STRIDE_BYTES,
            GLCanvasUtil.TEXCOORD_OFFSET,
        )

        // Uniforms — identity tex matrix (snapshot is already oriented)
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, GLCanvasUtil.IDENTITY_MATRIX, 0)
        GLES30.glUniform1f(uAlphaLoc, alpha.coerceIn(0f, 1f))

        // Bind snapshot texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, snapshotTexId)
        GLES30.glUniform1i(uTextureLoc, 0)

        // Draw
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            GLCanvasUtil.QUAD_INDEX_COUNT,
            GLES30.GL_UNSIGNED_SHORT,
            0,
        )

        // Cleanup
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glUseProgram(0)
    }

    // ── Snapshot ──────────────────────────────────────────────────────────

    /**
     * Copies the current default framebuffer (read buffer) into the snapshot
     * FBO texture using [GLES30.glBlitFramebuffer].
     *
     * Must be called AFTER the old scene is rendered and BEFORE the new
     * scene is drawn.
     */
    private fun takeSnapshot() {
        if (!isSetup || snapshotFboId == 0) return

        // Bind FBO as draw target, default framebuffer as read source
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, snapshotFboId)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)

        GLES30.glBlitFramebuffer(
            0, 0, viewportWidth, viewportHeight,
            0, 0, viewportWidth, viewportHeight,
            GLES30.GL_COLOR_BUFFER_BIT,
            GLES30.GL_LINEAR,
        )

        // Restore
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.d(TAG, "Snapshot taken: ${viewportWidth}x${viewportHeight}")
    }

    // ── Release ───────────────────────────────────────────────────────────

    /**
     * Deletes all GL resources. Safe to call multiple times.
     */
    fun release() {
        isSetup = false
        active = false
        GLCanvasUtil.deleteFBO(snapshotFboId)
        GLCanvasUtil.deleteTexture(snapshotTexId)
        GLCanvasUtil.deleteProgram(programId)
        GLCanvasUtil.deleteBuffer(vboId)
        GLCanvasUtil.deleteBuffer(eboId)
        snapshotFboId = 0
        snapshotTexId = 0
        programId = 0
        vboId = 0
        eboId = 0
    }
}
