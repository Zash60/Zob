package com.zob.recorder.compositor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Static utility helpers for the OpenGL ES 3.0 compositor pipeline.
 *
 * Responsibilities:
 * - Compile and link GL shader programs ([compileShader], [createProgram])
 * - Upload [Bitmap] data to `GL_TEXTURE_2D` ([createTexture])
 * - Create OES (External) textures for [android.graphics.SurfaceTexture] ([createOESTexture])
 * - Render text to a [Bitmap] via [Canvas] ([drawTextToBitmap])
 * - Create Framebuffer Objects for render-to-texture snapshots ([createFBO])
 * - Build standard full-screen quad VBO/EBO ([createQuadVBO], [createQuadEBO])
 * - Cleanup helpers ([deleteTexture], [deleteFBO], [deleteProgram])
 *
 * All methods are thread-safe in the sense that they operate purely on the
 * calling thread's current GL context (they hold no shared state).
 */
object GLCanvasUtil {

    private const val TAG = "GLCanvasUtil"

    // ── Quad geometry (NDC [-1,1]², z=0 for all vertices) ──────────────────
    // Vertex layout: position.xyz (3 floats) + texCoord.st (2 floats)
    // = 5 floats per vertex × 4 vertices = 80 bytes.

    private val QUAD_VERTICES = floatArrayOf(
        // x      y     z     s     t
        -1f, -1f, 0f, 0f, 0f,  // bottom-left
         1f, -1f, 0f, 1f, 0f,  // bottom-right
        -1f,  1f, 0f, 0f, 1f,  // top-left
         1f,  1f, 0f, 1f, 1f,  // top-right
    )

    private val QUAD_INDICES = shortArrayOf(
        0, 1, 2,  // first triangle
        1, 3, 2,  // second triangle
    )

    // ── Identity matrix (used for overlay tex transform) ───────────────────

    /** 4×4 identity matrix in column-major order. */
    val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    // ── Vertex layout constants ────────────────────────────────────────────

    /** Number of floats per vertex (pos.xyz + tex.st). */
    const val FLOATS_PER_VERTEX = 5

    /** Number of quad vertices. */
    const val QUAD_VERTEX_COUNT = 4

    /** Number of quad indices (two triangles). */
    const val QUAD_INDEX_COUNT = 6

    /** Stride in bytes for interleaved vertex data. */
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

    /** Byte offset of the position attribute within a vertex. */
    const val POSITION_OFFSET = 0

    /** Byte offset of the texture-coordinate attribute within a vertex. */
    const val TEXCOORD_OFFSET = 3 * 4

    /** Number of components in the position attribute (x, y, z). */
    const val POSITION_SIZE = 3

    /** Number of components in the tex-coord attribute (s, t). */
    const val TEXCOORD_SIZE = 2

    // ── Shader compilation / linking ───────────────────────────────────────

    /**
     * Compiles a GL shader of the given [type] from [source].
     *
     * @return The shader object ID, or 0 on failure.
     */
    fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "glCreateShader failed for type=$type")
            return 0
        }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val infoLog = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile error (type=$type): $infoLog")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /**
     * Links a GL program from [vertexSource] and [fragmentSource].
     *
     * @return The program object ID, or 0 on failure.
     */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vs == 0) return 0
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fs == 0) {
            GLES30.glDeleteShader(vs)
            return 0
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "glCreateProgram failed")
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return 0
        }

        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val infoLog = GLES30.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link error: $infoLog")
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return 0
        }

        // Shaders successfully linked — intermediate objects no longer needed
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    /**
     * Deletes a GL program. Safe to call with 0.
     */
    fun deleteProgram(programId: Int) {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
        }
    }

    // ── Texture creation ───────────────────────────────────────────────────

    /**
     * Creates a `GL_TEXTURE_2D` from [bitmap] and returns the texture ID.
     *
     * The [bitmap] is recycled after upload (caller should not use it further).
     *
     * @return texture ID, or 0 on failure.
     */
    fun createTexture(bitmap: Bitmap): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        if (texIds[0] == 0) {
            Log.e(TAG, "glGenTextures failed for 2D texture")
            return 0
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return texIds[0]
    }

    /**
     * Creates an empty `GL_TEXTURE_2D` with the given dimensions and no pixel
     * data. Used as a colour attachment for Framebuffer Objects.
     *
     * @return texture ID, or 0 on failure.
     */
    fun createEmptyTexture(width: Int, height: Int): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        if (texIds[0] == 0) {
            Log.e(TAG, "glGenTextures failed for empty texture")
            return 0
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texIds[0]
    }

    /**
     * Creates an OES (External) texture for use with
     * [android.graphics.SurfaceTexture].
     *
     * This texture type (`GL_TEXTURE_EXTERNAL_OES`) is required by
     * [android.graphics.SurfaceTexture] because it consumes buffers from
     * `Surface` in a non-contiguous layout that standard `sampler2D` cannot
     * sample.
     *
     * @return texture ID, or 0 on failure.
     */
    fun createOESTexture(): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        if (texIds[0] == 0) {
            Log.e(TAG, "glGenTextures failed for OES texture")
            return 0
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_EXTERNAL_OES, texIds[0])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_EXTERNAL_OES, 0)
        return texIds[0]
    }

    /**
     * Deletes a texture. Safe to call with 0.
     */
    fun deleteTexture(textureId: Int) {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }

    // ── Framebuffer Object (render-to-texture) ────────────────────────────

    /**
     * Creates an FBO with a single `GL_COLOR_ATTACHMENT0` texture of the
     * given [width] × [height].
     *
     * @return `Pair(fboId, textureId)`, either of which may be 0 on failure.
     */
    fun createFBO(width: Int, height: Int): Pair<Int, Int> {
        val texId = createEmptyTexture(width, height)
        if (texId == 0) return Pair(0, 0)

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        if (fboIds[0] == 0) {
            Log.e(TAG, "glGenFramebuffers failed")
            deleteTexture(texId)
            return Pair(0, 0)
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texId,
            0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO not complete: status=0x${status.toHexString()}")
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboIds[0]), 0)
            deleteTexture(texId)
            return Pair(0, 0)
        }

        return Pair(fboIds[0], texId)
    }

    /**
     * Deletes an FBO. Safe to call with 0.
     */
    fun deleteFBO(fboId: Int) {
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        }
    }

    // ── Quad buffer creation ──────────────────────────────────────────────

    /**
     * Creates a VBO containing the quad vertex data.
     *
     * @return VBO ID, or 0 on failure.
     */
    fun createQuadVBO(): Int {
        val buf = createDirectFloatBuffer(QUAD_VERTICES)

        val vboIds = IntArray(1)
        GLES30.glGenBuffers(1, vboIds, 0)
        if (vboIds[0] == 0) {
            Log.e(TAG, "glGenBuffers failed for VBO")
            return 0
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return vboIds[0]
    }

    /**
     * Creates an EBO containing the quad index data.
     *
     * @return EBO ID, or 0 on failure.
     */
    fun createQuadEBO(): Int {
        val buf = createDirectShortBuffer(QUAD_INDICES)

        val eboIds = IntArray(1)
        GLES30.glGenBuffers(1, eboIds, 0)
        if (eboIds[0] == 0) {
            Log.e(TAG, "glGenBuffers failed for EBO")
            return 0
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboIds[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, QUAD_INDICES.size * 2, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        return eboIds[0]
    }

    /**
     * Deletes a buffer (VBO or EBO). Safe to call with 0.
     */
    fun deleteBuffer(bufferId: Int) {
        if (bufferId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(bufferId), 0)
        }
    }

    // ── Text rendering to Bitmap ──────────────────────────────────────────

    /**
     * Renders [text] to a newly-created [Bitmap] suitable for GL texture upload.
     *
     * The bitmap dimensions are [width] × [height] with config
     * [Bitmap.Config.ARGB_8888]. The text is drawn using a default sans-serif
     * typeface at [fontSize] pixels in the given ARGB [color], centred
     * vertically within the bitmap.
     *
     * @return A new [Bitmap] that can be passed to [createTexture].
     */
    fun drawTextToBitmap(
        text: String,
        fontSize: Int,
        color: Int,
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            this.textSize = fontSize.toFloat()
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        // Centre text vertically within the bitmap
        val fm = paint.fontMetricsInt
        val yOffset = (height - fm.bottom - fm.top) / 2
        canvas.drawText(text, 0f, yOffset.toFloat(), paint)
        return bitmap
    }

    // ── Buffer helpers ────────────────────────────────────────────────────

    /**
     * Allocates a direct [FloatBuffer] with native byte order containing the
     * given array contents, positioned at 0.
     */
    private fun createDirectFloatBuffer(array: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(array)
            .also { it.position(0) }
    }

    /**
     * Allocates a direct [ShortBuffer] with native byte order containing the
     * given array contents, positioned at 0.
     */
    private fun createDirectShortBuffer(array: ShortArray): ShortBuffer {
        return ByteBuffer.allocateDirect(array.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(array)
            .also { it.position(0) }
    }

    // ── Small helpers ─────────────────────────────────────────────────────

    /** Formats an integer as a hex string for diagnostic logging. */
    private fun Int.toHexString(): String = "0x%08x".format(this)
}
