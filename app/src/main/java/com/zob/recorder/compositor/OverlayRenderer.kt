package com.zob.recorder.compositor

import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import java.util.LinkedHashMap

/**
 * Renders 2D texture overlays (text, images) onto the scene.
 *
 * Each overlay is a textured quad positioned/sized according to a
 * [com.zob.recorder.model.Source] configuration (positionX, positionY,
 * width, height, opacity).
 *
 * The shader program uses a regular `sampler2D` and applies a model matrix
 * to transform the unit quad to the source's position in NDC space.
 *
 * ## Texture cache
 * Text and image textures are cached by source ID. Call [invalidateCache]
 * or [invalidateSource] when source content changes.
 *
 * ## Lifecycle
 * 1. Call [setup] once after the GL context is current.
 * 2. Call [renderOverlay] for each visible source each frame.
 * 3. Call [release] to free GL resources.
 */
class OverlayRenderer {

    private var programId = 0
    private var vboId = 0
    private var eboId = 0

    // Uniform locations
    private var uModelMatrixLoc = -1
    private var uTexMatrixLoc = -1
    private var uTextureLoc = -1
    private var uAlphaLoc = -1

    private var isSetup = false

    /**
     * Cache of source-ID → texture-ID for overlay textures.
     *
     * Uses [LinkedHashMap] with access-order eviction (max 32 entries) so
     * that recently-used textures stay resident while stale ones are dropped.
     */
    private val textureCache = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>): Boolean {
            if (size > MAX_CACHE_ENTRIES) {
                GLCanvasUtil.deleteTexture(eldest.value)
                return true
            }
            return false
        }
    }

    // ── Shader sources (ES 3.0) ───────────────────────────────────────────

    companion object {
        private const val TAG = "OverlayRenderer"

        /** Maximum number of cached overlay textures. */
        private const val MAX_CACHE_ENTRIES = 32

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            uniform mat4 uModelMatrix;
            uniform mat4 uTexMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = uModelMatrix * vec4(aPosition, 1.0);
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
     * Creates the shader program and vertex buffers.
     *
     * Must be called on the GL thread with the EGL context current.
     *
     * @return `true` on success.
     */
    fun setup(): Boolean {
        programId = GLCanvasUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programId == 0) {
            Log.e(TAG, "Failed to create 2D overlay shader program")
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

        uModelMatrixLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uTexMatrixLoc = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        uTextureLoc = GLES30.glGetUniformLocation(programId, "uTexture")
        uAlphaLoc = GLES30.glGetUniformLocation(programId, "uAlpha")

        isSetup = true
        return true
    }

    // ── Texture management ────────────────────────────────────────────────

    /**
     * Sets or updates the cached texture for [sourceId] from a [bitmap].
     *
     * If the source already has a cached texture it is replaced (old texture
     * deleted). The [bitmap] is recycled after upload.
     */
    fun setTexture(sourceId: String, bitmap: Bitmap) {
        val newTexId = GLCanvasUtil.createTexture(bitmap)
        if (newTexId == 0) {
            Log.w(TAG, "Failed to create texture for source $sourceId")
            return
        }
        // Replace existing entry (old texture deleted by cache eviction or here)
        val old = textureCache.put(sourceId, newTexId)
        if (old != null && old != newTexId) {
            GLCanvasUtil.deleteTexture(old)
        }
    }

    /**
     * Removes a single source from the texture cache, deleting its texture.
     */
    fun invalidateSource(sourceId: String) {
        textureCache.remove(sourceId)?.let { GLCanvasUtil.deleteTexture(it) }
    }

    /**
     * Clears the entire texture cache, deleting all textures.
     */
    fun invalidateCache() {
        val iterator = textureCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            GLCanvasUtil.deleteTexture(entry.value)
            iterator.remove()
        }
    }

    /**
     * Returns the cached texture ID for [sourceId], or 0 if not cached.
     */
    fun getTexture(sourceId: String): Int {
        return textureCache[sourceId] ?: 0
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    /**
     * Renders a single overlay quad.
     *
     * @param textureId    The GL texture ID to render (from [getTexture]).
     * @param sourceX      Source left position in screen pixels.
     * @param sourceY      Source top position in screen pixels (Y-down).
     * @param sourceWidth  Source width in screen pixels.
     * @param sourceHeight Source height in screen pixels.
     * @param viewportW    Viewport width in pixels.
     * @param viewportH    Viewport height in pixels.
     * @param alpha        Opacity multiplier in [0f..1f].
     */
    fun renderOverlay(
        textureId: Int,
        sourceX: Float,
        sourceY: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        viewportW: Int,
        viewportH: Int,
        alpha: Float,
    ) {
        if (!isSetup || textureId == 0) return

        GLES30.glUseProgram(programId)

        // ── Vertex attributes ──────────────────────────────────────────────
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

        // ── Compute model matrix ──────────────────────────────────────────
        //
        // The source position uses Android screen coordinates (Y-down, origin
        // at top-left). We convert to OpenGL NDC (Y-up, [-1,1]²).
        //
        //   NDC half-extent  = sourceDimension / viewportDimension
        //   NDC center       = transformed position + half-extent

        val vpW = viewportW.toFloat()
        val vpH = viewportH.toFloat()
        val halfW = sourceWidth / vpW
        val halfH = sourceHeight / vpH
        val centerX = (sourceX / vpW) * 2f - 1f + halfW
        val centerY = 1f - (sourceY / vpH) * 2f - halfH

        val modelMatrix = floatArrayOf(
            halfW,  0f,    0f, 0f,
            0f,    halfH,  0f, 0f,
            0f,    0f,     1f, 0f,
            centerX, centerY, 0f, 1f,
        )

        // ── Uniforms ──────────────────────────────────────────────────────
        GLES30.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, GLCanvasUtil.IDENTITY_MATRIX, 0)
        GLES30.glUniform1f(uAlphaLoc, alpha.coerceIn(0f, 1f))

        // ── Bind 2D texture ───────────────────────────────────────────────
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTextureLoc, 0)

        // ── Draw ──────────────────────────────────────────────────────────
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            GLCanvasUtil.QUAD_INDEX_COUNT,
            GLES30.GL_UNSIGNED_SHORT,
            0,
        )

        // ── Cleanup ───────────────────────────────────────────────────────
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glUseProgram(0)
    }

    // ── Release ───────────────────────────────────────────────────────────

    /**
     * Deletes all GL resources and clears the texture cache.
     * Safe to call multiple times.
     */
    fun release() {
        isSetup = false
        invalidateCache()
        GLCanvasUtil.deleteProgram(programId)
        GLCanvasUtil.deleteBuffer(vboId)
        GLCanvasUtil.deleteBuffer(eboId)
        programId = 0
        vboId = 0
        eboId = 0
    }
}
