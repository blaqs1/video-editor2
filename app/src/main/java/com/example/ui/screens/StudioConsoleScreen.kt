package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.OverlayEntity
import com.example.data.db.ScriptEntity
import com.example.data.db.SlideEntity
import com.example.data.db.TimelineEventEntity
import com.example.data.db.VideoEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.StudioViewModel
import java.io.File

/**
 * StudioConsoleScreen - CapCut Layout Architecture:
 * Zone 1: Top Bar (Close X, Resolution Pill 1080P 30FPS, Bordeaux Red EXPORT Button)
 * Zone 2: Video Player (16:9 / 9:16 Video Viewport surrounded by Deep Slate #0C0C10, rounded 16.dp, floating timecode & controls)
 * Zone 3: Multi-Track Timeline (TimelineView with stationary center red playhead line)
 * Zone 4: Bottom Action Bar (ToolbarView with Edit, Audio, Text, Overlay, Captions, Brand & ModalBottomSheet drawers)
 */
@Composable
fun StudioConsoleScreen(
    viewModel: StudioViewModel,
    project: VideoEntity,
    capturedX: Float,
    capturedY: Float,
    directorPrompt: String,
    generatedCommand: String,
    isGeneratingCommand: Boolean,
    isExecutingEdit: Boolean,
    audioFile: File?,
    introFile: File?,
    responseLog: String?,
    overlays: List<OverlayEntity>,
    slides: List<SlideEntity>,
    timelineEvents: List<TimelineEventEntity>,
    scripts: List<ScriptEntity>,
    isTranslatingScript: Boolean,
    scriptEnglishInput: String,
    scriptGermanOutput: String,
    syncStatusState: com.example.ui.viewmodel.AudioVideoSyncState
) {
    val context = LocalContext.current
    val isAutosaving by viewModel.isAutosaving.collectAsStateWithLifecycle()
    val lastAutosavedTime by viewModel.lastAutosavedTime.collectAsStateWithLifecycle()
    val isSavingVideo by viewModel.isSavingVideo.collectAsStateWithLifecycle()
    val saveVideoProgress by viewModel.saveVideoProgress.collectAsStateWithLifecycle()
    val saveVideoStatusStep by viewModel.saveVideoStatusStep.collectAsStateWithLifecycle()

    val editorMode by viewModel.editorMode.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isProcessingPrompt by viewModel.isProcessingPrompt.collectAsStateWithLifecycle()

    var showPreviewExportModal by remember { mutableStateOf(false) }
    var selectedAspectRatio by remember { mutableStateOf("16:9") }
    var isPlaying by remember { mutableStateOf(false) }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedAssetForEvent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var eventStartTime by remember { mutableStateOf("0.0") }
    var eventEndTime by remember { mutableStateOf("5.0") }

    if (isSavingVideo) {
        SaveVideoProgressOverlay(
            progress = saveVideoProgress,
            statusStep = saveVideoStatusStep,
            filename = project.filename
        )
    }

    if (showAddEventDialog && selectedAssetForEvent != null) {
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            containerColor = CapCutSurface,
            title = {
                Text(
                    text = "ADD LAYER TO TIMELINE",
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CapCutTextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Asset: ${selectedAssetForEvent?.second}\nType: ${selectedAssetForEvent?.first?.uppercase()}",
                        color = CapCutTextSecondary,
                        fontSize = 11.sp
                    )

                    val curStart = eventStartTime.toFloatOrNull() ?: 0.0f
                    val curEnd = eventEndTime.toFloatOrNull() ?: 5.0f

                    VideoTrimRangeSlider(
                        startValue = curStart,
                        endValue = curEnd,
                        durationMax = 15.0f,
                        onRangeChange = { s, e ->
                            eventStartTime = "%.1f".format(s)
                            eventEndTime = "%.1f".format(e)
                        },
                        label = "TRIM TIMESTAMPS"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val start = eventStartTime.toFloatOrNull() ?: 0.0f
                        val end = eventEndTime.toFloatOrNull() ?: 5.0f
                        selectedAssetForEvent?.let { (type, name) ->
                            viewModel.addTimelineEvent(type, name, start, end)
                        }
                        showAddEventDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CapCutCyan, contentColor = StudioBlack),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ADD TO TIMELINE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) {
                    Text("CANCEL", color = CapCutTextMuted, fontSize = 11.sp)
                }
            },
            modifier = Modifier.border(1.dp, CapCutBorderSubtle, RoundedCornerShape(20.dp))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CapCutBackground)
    ) {
        // ZONE 1: TOP APP BAR (CapCut Header Bar + Mode Toggle)
        Box(modifier = Modifier.wrapContentHeight()) {
            Zone1TopBar(
                project = project,
                editorMode = editorMode,
                isAutosaving = isAutosaving,
                isExecutingEdit = isExecutingEdit,
                onCloseProject = { viewModel.selectVideo(null) },
                onToggleMode = { viewModel.toggleEditorMode() },
                onExportClick = { viewModel.openExportModal { viewModel.executeEdit(context) } }
            )
        }

        // ZONE 2: VIDEO PLAYER VIEWPORT ZONE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (editorMode == "prompt") 0.38f else 0.46f)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Zone2VideoViewport(
                project = project,
                aspectRatioStr = selectedAspectRatio,
                capturedX = capturedX,
                capturedY = capturedY,
                isPlaying = isPlaying,
                onPlayPauseToggle = { isPlaying = !isPlaying },
                onAspectRatioChange = { selectedAspectRatio = it },
                onTapCoordinates = { x, y -> viewModel.setCoordinates(x, y) },
                onClearCoordinates = { viewModel.clearCoordinates() },
                syncState = syncStatusState
            )
        }

        // ZONE 3: MULTI-TRACK TIMELINE ZONE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (editorMode == "prompt") 0.14f else 0.40f)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            TimelineView(
                project = project,
                timelineEvents = timelineEvents,
                overlays = overlays,
                scripts = scripts,
                audioFile = audioFile,
                viewModel = viewModel
            )
        }

        // ZONE 4: PROMPT CHAT OR MANUAL TOOLBAR ZONE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (editorMode == "prompt") 0.48f else 0.14f)
        ) {
            if (editorMode == "prompt") {
                PromptChatView(
                    viewModel = viewModel,
                    chatMessages = chatMessages,
                    isProcessingPrompt = isProcessingPrompt
                )
            } else {
                ToolbarView(
                    viewModel = viewModel,
                    project = project,
                    overlays = overlays,
                    slides = slides,
                    timelineEvents = timelineEvents,
                    scripts = scripts,
                    audioFile = audioFile,
                    directorPrompt = directorPrompt,
                    generatedCommand = generatedCommand,
                    isGeneratingCommand = isGeneratingCommand,
                    isExecutingEdit = isExecutingEdit,
                    onAddTimelineEvent = { type, name, duration ->
                        selectedAssetForEvent = Pair(type, name)
                        eventStartTime = "0.0"
                        eventEndTime = "$duration"
                        showAddEventDialog = true
                    }
                )
            }
        }
    }

    if (showPreviewExportModal) {
        PreviewExportModal(
            viewModel = viewModel,
            project = project,
            timelineEvents = timelineEvents,
            overlays = overlays,
            slides = slides,
            audioFile = audioFile,
            onDismiss = { showPreviewExportModal = false },
            onConfirmExport = {
                showPreviewExportModal = false
                viewModel.openExportModal { viewModel.executeEdit(context) }
            }
        )
    }
}

// ==========================================
// ZONE 1: TOP BAR COMPOSABLE (CapCut style)
// ==========================================
@Composable
private fun Zone1TopBar(
    project: VideoEntity,
    editorMode: String,
    isAutosaving: Boolean,
    isExecutingEdit: Boolean,
    onCloseProject: () -> Unit,
    onToggleMode: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CapCutBackground)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: X Close & Mode Toggle (🤖 AI Prompt vs ⚙️ Manual)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onCloseProject,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = CapCutTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Mode Selector Pill (🤖 Prompt Director / ⚙️ Manual Tools)
            Surface(
                onClick = onToggleMode,
                color = CapCutSurfaceElevated,
                border = BorderStroke(1.dp, if (editorMode == "prompt") CapCutCyanGlow else CapCutBorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("editor_mode_toggle_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (editorMode == "prompt") Icons.Default.AutoAwesome else Icons.Default.Tune,
                        contentDescription = "Mode Toggle",
                        tint = if (editorMode == "prompt") CapCutCyan else CapCutTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (editorMode == "prompt") "AI DIRECTOR" else "MANUAL TOOLS",
                        color = if (editorMode == "prompt") CapCutCyan else CapCutTextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Right: Resolution Dropdown Pill + CapCut Cyan EXPORT Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(CapCutSurfaceElevated, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "4K UHD",
                    color = CapCutTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Resolution options",
                    tint = CapCutTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // CapCut Cyan Export Button
            Button(
                onClick = onExportClick,
                enabled = !isExecutingEdit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CapCutCyan,
                    contentColor = StudioBlack
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("zone1_export_btn")
            ) {
                if (isExecutingEdit) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = StudioBlack,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export", tint = StudioBlack, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Export",
                            color = StudioBlack,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ZONE 2: VIDEO PLAYER VIEWPORT COMPOSABLE
// ==========================================
@Composable
private fun Zone2VideoViewport(
    project: VideoEntity,
    aspectRatioStr: String,
    capturedX: Float,
    capturedY: Float,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onTapCoordinates: (Float, Float) -> Unit,
    onClearCoordinates: () -> Unit,
    syncState: com.example.ui.viewmodel.AudioVideoSyncState?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .testTag("zone2_video_viewport_card")
    ) {
        VideoPlayerViewport(
            capturedX = capturedX,
            capturedY = capturedY,
            videoName = project.filename,
            syncState = syncState,
            onTap = onTapCoordinates,
            onClear = onClearCoordinates
        )
    }
}
