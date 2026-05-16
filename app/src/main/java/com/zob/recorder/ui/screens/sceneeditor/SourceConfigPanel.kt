package com.zob.recorder.ui.screens.sceneeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zob.recorder.model.ImageScaleType
import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.Source
import com.zob.recorder.model.TextSource

@Composable
fun SourceConfigPanel(
    source: Source,
    onPositionChanged: (String, Float, Float) -> Unit,
    onSizeChanged: (String, Float, Float) -> Unit,
    onOpacityChanged: (String, Float) -> Unit,
    onTextConfigChanged: (String, String?, Int?, Long?) -> Unit,
    onImageConfigChanged: (String, String?, ImageScaleType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = source.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        PositionFields(
            source = source,
            onPositionChanged = onPositionChanged
        )

        Spacer(modifier = Modifier.height(12.dp))

        SizeFields(
            source = source,
            onSizeChanged = onSizeChanged
        )

        Spacer(modifier = Modifier.height(12.dp))

        OpacitySlider(
            source = source,
            onOpacityChanged = onOpacityChanged
        )

        when (source) {
            is TextSource -> TextSourceConfig(
                source = source,
                onConfigChanged = onTextConfigChanged
            )

            is ImageSource -> ImageSourceConfig(
                source = source,
                onConfigChanged = onImageConfigChanged
            )

            is ScreenSource -> ScreenSourceInfo()
        }
    }
}

@Composable
private fun PositionFields(
    source: Source,
    onPositionChanged: (String, Float, Float) -> Unit
) {
    var xValue by remember(source.positionX) { mutableFloatStateOf(source.positionX) }
    var yValue by remember(source.positionY) { mutableFloatStateOf(source.positionY) }

    Text(
        text = "Position",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NumericStepper(
            label = "X",
            value = xValue,
            onValueChange = {
                xValue = it
                onPositionChanged(source.id, it, yValue)
            },
            modifier = Modifier.weight(1f)
        )

        NumericStepper(
            label = "Y",
            value = yValue,
            onValueChange = {
                yValue = it
                onPositionChanged(source.id, xValue, it)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SizeFields(
    source: Source,
    onSizeChanged: (String, Float, Float) -> Unit
) {
    var widthValue by remember(source.width) { mutableFloatStateOf(source.width) }
    var heightValue by remember(source.height) { mutableFloatStateOf(source.height) }

    Text(
        text = "Size",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NumericStepper(
            label = "W",
            value = widthValue,
            onValueChange = {
                widthValue = it
                onSizeChanged(source.id, it, heightValue)
            },
            modifier = Modifier.weight(1f)
        )

        NumericStepper(
            label = "H",
            value = heightValue,
            onValueChange = {
                heightValue = it
                onSizeChanged(source.id, widthValue, it)
            },
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Slider(
        value = widthValue,
        onValueChange = {
            widthValue = it
            onSizeChanged(source.id, it, heightValue)
        },
        valueRange = 10f..1920f,
        modifier = Modifier.fillMaxWidth()
    )

    Slider(
        value = heightValue,
        onValueChange = {
            heightValue = it
            onSizeChanged(source.id, widthValue, it)
        },
        valueRange = 10f..1080f,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumericStepper(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )

        IconButton(
            onClick = { onValueChange((value - 10f).coerceAtLeast(0f)) },
            modifier = Modifier.width(32.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }

        OutlinedTextField(
            value = value.toInt().toString(),
            onValueChange = { newText ->
                newText.toFloatOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        IconButton(
            onClick = { onValueChange(value + 10f) },
            modifier = Modifier.width(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
private fun OpacitySlider(
    source: Source,
    onOpacityChanged: (String, Float) -> Unit
) {
    Text(
        text = "Opacity: ${(source.opacity * 100).toInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    Slider(
        value = source.opacity,
        onValueChange = { onOpacityChanged(source.id, it) },
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TextSourceConfig(
    source: TextSource,
    onConfigChanged: (String, String?, Int?, Long?) -> Unit
) {
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Text Content",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
        value = source.text,
        onValueChange = { onConfigChanged(source.id, it, null, null) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Text") }
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Font Size: ${source.fontSize}px",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    Slider(
        value = source.fontSize.toFloat(),
        onValueChange = { onConfigChanged(source.id, null, it.toInt(), null) },
        valueRange = 8f..120f,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Color",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    ColorPickerRow(
        color = source.color,
        onColorSelected = { onConfigChanged(source.id, null, null, it) }
    )
}

@Composable
private fun ColorPickerRow(
    color: Long,
    onColorSelected: (Long) -> Unit
) {
    val presetColors = listOf(
        0xFFFFFFFFL, 0xFF000000L, 0xFFFF0000L, 0xFF00FF00L,
        0xFF0000FFL, 0xFFFFFF00L, 0xFFFF00FFL, 0xFF00FFFFL,
        0xFFFFA500L, 0xFF800080L
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presetColors.forEach { presetColor ->
            Card(
                onClick = { onColorSelected(presetColor) },
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(presetColor)
                )
            ) {}
        }
    }
}

@Composable
private fun ImageSourceConfig(
    source: ImageSource,
    onConfigChanged: (String, String?, ImageScaleType?) -> Unit
) {
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Image URI",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
        value = source.imageUri,
        onValueChange = { onConfigChanged(source.id, it, null) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Image URI") },
        maxLines = 2
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Scale Type",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageScaleType.entries.forEach { scaleType ->
            Card(
                onClick = { onConfigChanged(source.id, null, scaleType) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (source.scaleType == scaleType) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = scaleType.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ScreenSourceInfo() {
    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = "Screen capture source — no configuration needed",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
