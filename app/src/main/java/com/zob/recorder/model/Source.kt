package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Source {
    abstract val id: String
    abstract val name: String
    abstract val positionX: Float
    abstract val positionY: Float
    abstract val width: Float
    abstract val height: Float
    abstract val opacity: Float
    abstract val zOrder: Int
    abstract val isVisible: Boolean
}

@Serializable
data class ScreenSource(
    override val id: String,
    override val name: String = "Screen",
    override val positionX: Float = 0f,
    override val positionY: Float = 0f,
    override val width: Float = 1920f,
    override val height: Float = 1080f,
    override val opacity: Float = 1f,
    override val zOrder: Int = 0,
    override val isVisible: Boolean = true
) : Source()

@Serializable
data class TextSource(
    override val id: String,
    override val name: String,
    val text: String = "Text",
    val fontSize: Int = 24,
    val color: Long = 0xFFFFFFFF,
    override val positionX: Float = 100f,
    override val positionY: Float = 100f,
    override val width: Float = 400f,
    override val height: Float = 100f,
    override val opacity: Float = 1f,
    override val zOrder: Int = 1,
    override val isVisible: Boolean = true
) : Source()

@Serializable
data class ImageSource(
    override val id: String,
    override val name: String,
    val imageUri: String = "",
    val scaleType: ImageScaleType = ImageScaleType.FIT,
    override val positionX: Float = 100f,
    override val positionY: Float = 100f,
    override val width: Float = 400f,
    override val height: Float = 300f,
    override val opacity: Float = 1f,
    override val zOrder: Int = 1,
    override val isVisible: Boolean = true
) : Source()

@Serializable
enum class ImageScaleType { FIT, CROP, STRETCH }

@Serializable
enum class TransitionType { CUT, FADE }
