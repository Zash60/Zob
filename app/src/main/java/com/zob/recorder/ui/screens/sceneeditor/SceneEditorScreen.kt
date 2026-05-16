package com.zob.recorder.ui.screens.sceneeditor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zob.recorder.model.ImageSource
import com.zob.recorder.model.ScreenSource
import com.zob.recorder.model.Source
import com.zob.recorder.model.TextSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(
    sceneId: String,
    navController: NavController,
    viewModel: SceneEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showConfigSheet by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addImageSource(it) }
        showAddMenu = false
    }

    LaunchedEffect(sceneId) {
        viewModel.loadScene(sceneId)
    }

    LaunchedEffect(uiState.selectedSourceId) {
        showConfigSheet = uiState.selectedSourceId != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.scene?.name ?: "Edit Scene",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                DraggablePreview(
                    scene = uiState.scene!!,
                    selectedSourceId = uiState.selectedSourceId,
                    onSourceSelected = viewModel::selectSource,
                    onPositionChanged = viewModel::updateSourcePosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .background(MaterialTheme.colorScheme.background)
                )

                SourceListPanel(
                    sources = uiState.scene!!.sources,
                    selectedSourceId = uiState.selectedSourceId,
                    onSourceSelected = viewModel::selectSource,
                    onAddTextSource = viewModel::addTextSource,
                    onAddImageSource = { imagePickerLauncher.launch("image/*") },
                    onReorderSources = viewModel::reorderSources,
                    onRemoveSource = viewModel::removeSource,
                    showAddMenu = showAddMenu,
                    onAddMenuToggle = { showAddMenu = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                )
            }
        }

        if (showConfigSheet && uiState.selectedSourceId != null) {
            val selectedSource = uiState.scene?.sources?.find { it.id == uiState.selectedSourceId }
            if (selectedSource != null) {
                ModalBottomSheet(
                    onDismissRequest = {
                        viewModel.selectSource(null)
                        showConfigSheet = false
                    },
                    sheetState = sheetState
                ) {
                    SourceConfigPanel(
                        source = selectedSource,
                        onPositionChanged = viewModel::updateSourcePosition,
                        onSizeChanged = viewModel::updateSourceSize,
                        onOpacityChanged = viewModel::updateSourceOpacity,
                        onTextConfigChanged = viewModel::updateTextSourceConfig,
                        onImageConfigChanged = viewModel::updateImageSourceConfig,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceListPanel(
    sources: List<Source>,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
    onAddTextSource: () -> Unit,
    onAddImageSource: () -> Unit,
    onReorderSources: (List<String>) -> Unit,
    onRemoveSource: (String) -> Unit,
    showAddMenu: Boolean,
    onAddMenuToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sources (${sources.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box {
                IconButton(onClick = { onAddMenuToggle(true) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Source"
                    )
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { onAddMenuToggle(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Screen") },
                        onClick = {
                            onAddMenuToggle(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Text") },
                        onClick = {
                            onAddTextSource()
                            onAddMenuToggle(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.TextFields, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Image") },
                        onClick = {
                            onAddImageSource()
                            onAddMenuToggle(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Image, contentDescription = null)
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = sources,
                key = { _, source -> source.id }
            ) { index, source ->
                SourceListItem(
                    source = source,
                    isSelected = source.id == selectedSourceId,
                    index = index,
                    totalItems = sources.size,
                    onClick = { onSourceSelected(source.id) },
                    onMoveUp = {
                        if (index > 0) {
                            val newOrder = sources.map { it.id }.toMutableList()
                            val temp = newOrder[index]
                            newOrder[index] = newOrder[index - 1]
                            newOrder[index - 1] = temp
                            onReorderSources(newOrder)
                        }
                    },
                    onMoveDown = {
                        if (index < sources.size - 1) {
                            val newOrder = sources.map { it.id }.toMutableList()
                            val temp = newOrder[index]
                            newOrder[index] = newOrder[index + 1]
                            newOrder[index + 1] = temp
                            onReorderSources(newOrder)
                        }
                    },
                    onDelete = { onRemoveSource(source.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SourceListItem(
    source: Source,
    isSelected: Boolean,
    index: Int,
    totalItems: Int,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, label) = when (source) {
        is ScreenSource -> Icons.Default.Videocam to "Screen"
        is TextSource -> Icons.Default.TextFields to source.text.take(20)
        is ImageSource -> Icons.Default.Image to "Image"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        AnimatedVisibility(visible = totalItems > 1) {
            Row {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move up",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalItems - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move down",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete source",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
