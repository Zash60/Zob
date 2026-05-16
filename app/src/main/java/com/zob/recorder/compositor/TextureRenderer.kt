package com.zob.recorder.compositor

import android.opengl.GLES30
import android.util.Log

/**
 * Renders an OES (External) texture as a full-screen quad.
 *
 * This is used to render the screen capture from a
 * [android.graphics.SurfaceTexture] that is fed by the
 * [android.hardware.display.VirtualDisplay].
 *
 * The shader program uses `samplerExternalOES` which requires the
 * `GL_OES_EGL_image_external_essl3` extension (ES 3.0 variant).
 *
 * ## Lifecycle
 * 1. Call [setup] once after the GL context is current.
 * 2. Call [draw] each frame to render the OES texture.
 * 3. Call [release] to free GL resources.
 */
class TextureRenderer {

    private var programId = 0
    private var vboId = 0
    private var eboId = 0

    // Uniform locations
    private var uTexMatrixLoc = -1
    private var uTextureLoc = -1
    private var uAlphaLoc = -1

    private var isSetup = false

    // ── Shader sources (ES 3.0) ───────────────────────────────────────────

    companion object {
        private const val TAG = "TextureRenderer"

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
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 vTexCoord;
            out vec4 fragColor;
            uniform samplerExternalOES uTexture;
            uniform float uAlpha;
            void main() {
                fragColor = texture(uTexture, vTexCoord) * uAlpha;
            }
        """
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Creates the shader program and vertex buffers.
     *
     * Must be called on the GL thread with the EGL context current.
     *
     * @return `true` on success.
     */
    fun setup(): Boolean {
        programId = GLCanvasUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programId == 0) {
            Log.e(TAG, "Failed to create OES shader program")
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

        isSetup = true
        return true
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    /**
     * Renders the given OES [textureId] as a full-screen quad.
     *
     * @param textureId  OES texture ID (from [GLCanvasUtil.createOESTexture]).
     * @param texMatrix  4×4 texture transform matrix (from
     *                   [android.graphics.SurfaceTexture.getTransformMatrix]).
     * @param alpha      Opacity multiplier in [0f..1f].
     */
    fun draw(textureId: Int, texMatrix: FloatArray, alpha: Float) {
        if (!isSetup) return

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

        // Uniforms
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES30.glUniform1f(uAlphaLoc, alpha.coerceIn(0f, 1f))

        // Bind OES texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(uTextureLoc, 0)

        // Draw
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            GLCanvasUtil.QUAD_INDEX_COUNT,
            GLES30.GL_UNSIGNED_SHORT,
            0,
        )

        // Cleanup attribute state
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glUseProgram(0)
    }

    // ── Release ───────────────────────────────────────────────────────────

    /**
     * Deletes all GL resources owned by this renderer.
     * Safe to call multiple times.
     */
    fun release() {
        isSetup = false
        GLCanvasUtil.deleteProgram(programId)
        GLCanvasUtil.deleteBuffer(vboId)
        GLCanvasUtil.deleteBuffer(eboId)
        programId = 0
        vboId = 0
        eboId = 0
    }
}
