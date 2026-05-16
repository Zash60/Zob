package com.zob.recorder.ui.screens.sceneeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.Scene
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.Source
import com.zob.recorder.model.TextSource

private const val SCENE_WIDTH = 1920f
private const val SCENE_HEIGHT = 1080f
private const val GRID_SIZE = 50f

@Composable
fun DraggablePreview(
    scene: Scene,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
    onPositionChanged: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingSourceId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scene.sources, selectedSourceId) {
                    detectTapGestures(
                        onTap = { offset ->
                            val hitSource = findSourceAtOffset(
                                offset = offset,
                                sources = scene.sources,
                                size = size,
                                selectedSourceId = selectedSourceId
                            )
                            onSourceSelected(hitSource?.id)
                        }
                    )
                }
                .pointerInput(scene.sources, selectedSourceId) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val hitSource = findSourceAtOffset(
                                offset = offset,
                                sources = scene.sources,
                                size = size,
                                selectedSourceId = selectedSourceId
                            )
                            if (hitSource != null) {
                                draggingSourceId = hitSource.id
                                dragOffset = offset
                                onSourceSelected(hitSource.id)
                            }
                        },
                        onDragEnd = {
                            draggingSourceId = null
                        },
                        onDragCancel = {
                            draggingSourceId = null
                        },
                        onDrag = { change, dragAmount ->
                            val sourceId = draggingSourceId ?: return@detectDragGestures
                            change.consume()

                            val scale = calculateScale(size)
                            val newX = scene.sources.find { it.id == sourceId }?.positionX
                                ?.plus(dragAmount.x / scale) ?: 0f
                            val newY = scene.sources.find { it.id == sourceId }?.positionY
                                ?.plus(dragAmount.y / scale) ?: 0f

                            onPositionChanged(sourceId, newX, newY)
                        }
                    )
                }
        ) {
            val scale = calculateScale(size)
            val offsetX = (size.width - SCENE_WIDTH * scale) / 2
            val offsetY = (size.height - SCENE_HEIGHT * scale) / 2

            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale)
            }) {
                drawRect(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    size = androidx.compose.ui.geometry.Size(SCENE_WIDTH, SCENE_HEIGHT)
                )

                if (draggingSourceId != null) {
                    drawGrid()
                }

                scene.sources.forEach { source ->
                    if (!source.isVisible) return@forEach

                    val isSelected = source.id == selectedSourceId
                    val isDragging = source.id == draggingSourceId

                    when (source) {
                        is ScreenSource -> drawScreenSource(source, isSelected, isDragging)
                        is TextSource -> drawTextSource(source, isSelected, isDragging)
                        is ImageSource -> drawImageSource(source, isSelected, isDragging)
                    }
                }
            }
        }
    }
}

private fun calculateScale(canvasSize: androidx.compose.ui.geometry.Size): Float {
    val scaleX = canvasSize.width / SCENE_WIDTH
    val scaleY = canvasSize.height / SCENE_HEIGHT
    return minOf(scaleX, scaleY) * 0.9f
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid() {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    for (x in 0L..SCENE_WIDTH.toLong() step GRID_SIZE.toLong()) {
        drawLine(
            color = gridColor,
            start = Offset(x.toFloat(), 0f),
            end = Offset(x.toFloat(), SCENE_HEIGHT),
            strokeWidth = 1f
        )
    }
    for (y in 0L..SCENE_HEIGHT.toLong() step GRID_SIZE.toLong()) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y.toFloat()),
            end = Offset(SCENE_WIDTH, y.toFloat()),
            strokeWidth = 1f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScreenSource(
    source: ScreenSource,
    isSelected: Boolean,
    isDragging: Boolean
) {
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    drawRect(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        topLeft = Offset(source.positionX, source.positionY),
        size = androidx.compose.ui.geometry.Size(source.width, source.height)
    )

    if (borderColor != Color.Transparent) {
        drawRect(
            color = borderColor,
            topLeft = Offset(source.positionX, source.positionY),
            size = androidx.compose.ui.geometry.Size(source.width, source.height),
            style = Stroke(width = 3f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextSource(
    source: TextSource,
    isSelected: Boolean,
    isDragging: Boolean
) {
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    val bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = source.opacity * 0.6f)

    drawRoundRect(
        color = bgColor,
        topLeft = Offset(source.positionX, source.positionY),
        size = androidx.compose.ui.geometry.Size(source.width, source.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    if (borderColor != Color.Transparent) {
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(source.positionX, source.positionY),
            size = androidx.compose.ui.geometry.Size(source.width, source.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageSource(
    source: ImageSource,
    isSelected: Boolean,
    isDragging: Boolean
) {
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    val bgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = source.opacity * 0.4f)

    drawRoundRect(
        color = bgColor,
        topLeft = Offset(source.positionX, source.positionY),
        size = androidx.compose.ui.geometry.Size(source.width, source.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    if (borderColor != Color.Transparent) {
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(source.positionX, source.positionY),
            size = androidx.compose.ui.geometry.Size(source.width, source.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )
    }
}

private fun findSourceAtOffset(
    offset: Offset,
    sources: List<Source>,
    size: androidx.compose.ui.geometry.Size,
    selectedSourceId: String?
): Source? {
    val scale = calculateScale(size)
    val offsetX = (size.width - SCENE_WIDTH * scale) / 2
    val offsetY = (size.height - SCENE_HEIGHT * scale) / 2

    val sceneX = (offset.x - offsetX) / scale
    val sceneY = (offset.y - offsetY) / scale

    val sortedSources = sources.sortedByDescending { it.zOrder }

    for (source in sortedSources) {
        if (!source.isVisible) continue
        val rect = Rect(
            left = source.positionX,
            top = source.positionY,
            right = source.positionX + source.width,
            bottom = source.positionY + source.height
        )
        if (rect.contains(Offset(sceneX, sceneY))) {
            return source
        }
    }

    return null
}
