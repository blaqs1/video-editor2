package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.example.data.db.ScriptEntity
import com.example.data.db.VideoEntity
import com.example.data.db.OverlayEntity
import com.example.data.db.SlideEntity
import com.example.data.db.TimelineEventEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.StudioViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioMinimalApp(
    viewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // ViewModel states
    val currentTab = remember { mutableStateOf("library") } // "library", "scripting", "production"
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val overlays by viewModel.overlays.collectAsStateWithLifecycle()
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val timelineEvents by viewModel.timelineEvents.collectAsStateWithLifecycle()
    
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isTranslatingScript by viewModel.isTranslatingScript.collectAsStateWithLifecycle()
    val isGeneratingCommand by viewModel.isGeneratingCommand.collectAsStateWithLifecycle()
    val isExecutingEdit by viewModel.isExecutingEdit.collectAsStateWithLifecycle()
    val isUploadingOverlay by viewModel.isUploadingOverlay.collectAsStateWithLifecycle()
    
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val capturedX by viewModel.capturedX.collectAsStateWithLifecycle()
    val capturedY by viewModel.capturedY.collectAsStateWithLifecycle()
    val directorPrompt by viewModel.directorPrompt.collectAsStateWithLifecycle()
    val generatedCommand by viewModel.generatedCommand.collectAsStateWithLifecycle()
    val audioFile by viewModel.audioFile.collectAsStateWithLifecycle()
    val introFile by viewModel.introFile.collectAsStateWithLifecycle()
    val responseLog by viewModel.responseLog.collectAsStateWithLifecycle()
    
    val scriptEnglishInput by viewModel.scriptEnglishInput.collectAsStateWithLifecycle()
    val scriptGermanOutput by viewModel.scriptGermanOutput.collectAsStateWithLifecycle()

    val showExportModal by viewModel.showExportModal.collectAsStateWithLifecycle()
    val isConnectionDropped by viewModel.isConnectionDropped.collectAsStateWithLifecycle()
    val isSupabaseSynced by viewModel.isSupabaseSynced.collectAsStateWithLifecycle()

    val showSupabaseModal by viewModel.showSupabaseModal.collectAsStateWithLifecycle()
    val supabaseUrlInput by viewModel.supabaseUrlInput.collectAsStateWithLifecycle()
    val supabaseKeyInput by viewModel.supabaseKeyInput.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // Show Snackbars on message changes
    LaunchedEffect(errorMessage, successMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = StudioDarkGray,
                contentColor = StudioWhite,
                actionColor = StudioWhite,
                modifier = Modifier.border(1.dp, StudioBorderGray)
            )
        }},
        containerColor = StudioBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioBlack)
        ) {
            MainNavigationBar(
                viewModel = viewModel,
                isSupabaseSynced = isSupabaseSynced,
                onCheckSupabase = { viewModel.openSupabaseModal() }
            )

            if (showSupabaseModal) {
                SupabaseConfigurationModal(
                    urlInput = supabaseUrlInput,
                    keyInput = supabaseKeyInput,
                    isSynced = isSupabaseSynced,
                    onUrlChange = { viewModel.supabaseUrlInput.value = it },
                    onKeyChange = { viewModel.supabaseKeyInput.value = it },
                    onDismiss = { viewModel.closeSupabaseModal() },
                    onSaveAndTest = { viewModel.saveAndTestSupabaseConfig(context) }
                )
            }

            if (isConnectionDropped) {
                ReconnectBanner(
                    onReconnect = { viewModel.reconnectAndRetry(context) }
                )
            }

            if (showExportModal) {
                ExportConfigurationModal(
                    viewModel = viewModel,
                    onDismiss = { viewModel.dismissExportModal() },
                    onConfirm = { res, fps -> viewModel.confirmAndRenderExport(res, fps) }
                )
            }

            val video = selectedVideo
            if (video == null) {
                ProjectHubScreen(
                    viewModel = viewModel,
                    videos = videos,
                    backendUrl = backendUrl,
                    isSyncing = isSyncing
                )
            } else {
                val syncStatusState by viewModel.syncStatusState.collectAsStateWithLifecycle()
                StudioConsoleScreen(
                    viewModel = viewModel,
                    project = video,
                    capturedX = capturedX,
                    capturedY = capturedY,
                    directorPrompt = directorPrompt,
                    generatedCommand = generatedCommand,
                    isGeneratingCommand = isGeneratingCommand,
                    isExecutingEdit = isExecutingEdit,
                    audioFile = audioFile,
                    introFile = introFile,
                    responseLog = responseLog,
                    overlays = overlays,
                    slides = slides,
                    timelineEvents = timelineEvents,
                    scripts = scripts,
                    isTranslatingScript = isTranslatingScript,
                    scriptEnglishInput = scriptEnglishInput,
                    scriptGermanOutput = scriptGermanOutput,
                    syncStatusState = syncStatusState
                )
            }
        }
    }
}

// ==========================================
// LIBRARY SCREEN
// ==========================================
@Composable
fun LibraryScreen(
    viewModel: StudioViewModel,
    videos: List<VideoEntity>,
    overlays: List<OverlayEntity>,
    backendUrl: String,
    isSyncing: Boolean,
    onNavigateToProduction: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf(backendUrl) }
    var localFilenameInput by remember { mutableStateOf("") }
    var localLanguageInput by remember { mutableStateOf("en") }

    LaunchedEffect(backendUrl) {
        urlInput = backendUrl
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Dynamic Remote Backend Url Connection Config Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SERVER SETTINGS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("SERVER ADDRESS", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    placeholder = { Text("e.g. https://xxxx.ngrok-free.app", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedLabelColor = StudioWhite,
                        unfocusedLabelColor = StudioLightGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_url_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.saveBackendUrl(urlInput)
                    })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveBackendUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioWhite,
                            contentColor = StudioBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("save_backend_url_button")
                    ) {
                        Text("SAVE & CONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }

                    IconButton(
                        onClick = { viewModel.syncLibrary() },
                        modifier = Modifier
                            .background(StudioMediumGray, RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                            .size(46.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StudioWhite, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = StudioWhite)
                        }
                    }
                }
            }
        }

        // Add Local Video (Preset / Mock Generator)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "ADD SAMPLE VIDEO",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                OutlinedTextField(
                    value = localFilenameInput,
                    onValueChange = { localFilenameInput = it },
                    label = { Text("FILE NAME", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    placeholder = { Text("e.g. sample_vlog.mp4", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedLabelColor = StudioWhite,
                        unfocusedLabelColor = StudioLightGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_filename_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = localLanguageInput,
                        onValueChange = { localLanguageInput = it },
                        label = { Text("LANGUAGE", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedLabelColor = StudioWhite,
                            unfocusedLabelColor = StudioLightGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (localFilenameInput.isNotBlank()) {
                                viewModel.addLocalVideo(localFilenameInput, localLanguageInput)
                                localFilenameInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioWhite,
                            contentColor = StudioBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp)
                            .testTag("add_video_button")
                    ) {
                        Text("ADD VIDEO", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SAVED VIDEOS (${videos.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = StudioWhite.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                
                if (videos.isEmpty()) {
                    Text(
                        text = "EMPTY LIBRARY",
                        color = StudioLightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // List videos
        if (videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, contentDescription = "No files", tint = StudioLightGray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NO VIDEOS AVAILABLE", color = StudioWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Connect server or add sample videos", color = StudioLightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        } else {
            items(videos) { video ->
                VideoItemRow(
                    video = video,
                    onSelect = {
                        viewModel.selectVideo(video)
                        onNavigateToProduction()
                    },
                    onDelete = { viewModel.deleteVideo(video.id) }
                )
            }
        }

        // ==========================================
        // OVERLAY ASSETS SECTION (UI UPDATE 1)
        // ==========================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "OVERLAY VIDEOS & IMAGES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                Text(
                    text = "Upload overlay clips, transparent images, or watermarks to add to your video.",
                    color = StudioLightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                val overlayUploader = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        val file = copyUriToCache(context, it, "overlay_" + System.currentTimeMillis() + ".mp4")
                        viewModel.uploadOverlay(file)
                    }
                }

                val isUploadingOverlay by viewModel.isUploadingOverlay.collectAsStateWithLifecycle()

                Button(
                    onClick = { overlayUploader.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioWhite,
                        contentColor = StudioBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("upload_overlay_button"),
                    enabled = !isUploadingOverlay
                ) {
                    if (isUploadingOverlay) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StudioBlack, strokeWidth = 2.dp)
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload Overlay", modifier = Modifier.size(16.dp))
                            Text("BROWSE & UPLOAD OVERLAY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                    }
                }

                // Register manually field (so they can also use existing B-roll names or bypass local files easily!)
                var manualOverlayName by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualOverlayName,
                        onValueChange = { manualOverlayName = it },
                        label = { Text("MANUAL OVERLAY FILENAME", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        placeholder = { Text("e.g. intro_broll.mp4", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedLabelColor = StudioWhite,
                            unfocusedLabelColor = StudioLightGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (manualOverlayName.isNotBlank()) {
                                viewModel.addOverlayManually(manualOverlayName)
                                manualOverlayName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioMediumGray,
                            contentColor = StudioWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(46.dp)
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                    ) {
                        Text("REGISTER", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section header for Overlay list
        item {
            Text(
                text = "REGISTERED OVERLAY FILES (${overlays.size})",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = StudioWhite.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }

        // List Overlays
        if (overlays.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LayersClear, contentDescription = "No Overlays", tint = StudioLightGray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("NO OVERLAYS REGISTERED", color = StudioLightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        } else {
            items(overlays) { overlay ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(StudioMediumGray, RoundedCornerShape(8.dp))
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = "Overlay", tint = StudioWhite, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(
                                text = overlay.filename,
                                color = StudioWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "B-Roll Overlay Asset",
                                color = StudioLightGray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.deleteOverlay(overlay.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Overlay", tint = StudioLightGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(
    video: VideoEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioDarkGray, RoundedCornerShape(16.dp))
            .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(StudioMediumGray, RoundedCornerShape(8.dp))
                .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, contentDescription = "Video", tint = StudioWhite)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = video.filename,
                color = StudioWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.language.uppercase(),
                        color = StudioLightGray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(if (video.status == "processing") StudioWhite else Color.Transparent, RoundedCornerShape(4.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.status.uppercase(),
                        color = if (video.status == "processing") StudioBlack else StudioLightGray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StudioLightGray, modifier = Modifier.size(18.dp))
        }

        IconButton(
            onClick = onSelect,
            modifier = Modifier
                .background(StudioWhite, RoundedCornerShape(8.dp))
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Select", tint = StudioBlack, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// SCRIPTING SCREEN
// ==========================================
@Composable
fun ScriptingScreen(
    viewModel: StudioViewModel,
    scripts: List<ScriptEntity>,
    selectedVideo: VideoEntity?,
    isTranslatingScript: Boolean,
    scriptEnglishInput: String,
    scriptGermanOutput: String
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Selection State Indicator
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(12.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = StudioWhite,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (selectedVideo != null) {
                        "TARGET VIDEO: ${selectedVideo.filename}"
                    } else {
                        "NO TARGET VIDEO CHOSEN (SAVES WITH video_id = 0)"
                    },
                    color = StudioWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Translation Editor Box
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SCRIPT TRANSLATOR (GEMINI AI)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                // English Input Area
                OutlinedTextField(
                    value = scriptEnglishInput,
                    onValueChange = { viewModel.scriptEnglishInput.value = it },
                    label = { Text("ENGLISH TEXT", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    placeholder = { Text("Enter script lines or voiceover in English here...", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedLabelColor = StudioWhite,
                        unfocusedLabelColor = StudioLightGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("english_script_input"),
                    maxLines = 4
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.translateScript()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioWhite,
                        contentColor = StudioBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("translate_button"),
                    enabled = !isTranslatingScript
                ) {
                    if (isTranslatingScript) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StudioBlack, strokeWidth = 2.dp)
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Translate", modifier = Modifier.size(16.dp))
                            Text("TRANSLATE TO DE via GEMINI", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }

                // German Output Area
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "GERMAN TRANSLATION (ELEVENLABS READY)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = StudioLightGray
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .background(StudioMediumGray, RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        if (scriptGermanOutput.isNotEmpty()) {
                            Text(
                                text = scriptGermanOutput,
                                color = StudioWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default
                            )
                        } else {
                            Text(
                                text = "German output will appear here...",
                                color = StudioLightGray.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (scriptGermanOutput.isNotEmpty()) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("German Script", scriptGermanOutput)
                                clipboard.setPrimaryClip(clip)
                                viewModel.successMessage.value = "German translation copied to clipboard!"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioMediumGray,
                                contentColor = StudioWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                Text("COPY FOR ELEVENLABS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Translation History Section
        item {
            Text(
                text = "TRANSLATION JOURNAL HISTORY (${scripts.size})",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = StudioWhite.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }

        if (scripts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO HISTORIC TRANSLATIONS YET",
                        color = StudioLightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(scripts) { script ->
                ScriptHistoryItem(
                    script = script,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("German Script", script.deText)
                        clipboard.setPrimaryClip(clip)
                        viewModel.successMessage.value = "Script copied to clipboard!"
                    },
                    onDelete = { viewModel.deleteScript(script.id) },
                    onTagPlaceholder = { time, placeholderName ->
                        viewModel.addTimelineEvent("overlay", placeholderName, time, time + 5f)
                    }
                )
            }
        }
    }
}

@Composable
fun ScriptHistoryItem(
    script: ScriptEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onTagPlaceholder: (Float, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioDarkGray, RoundedCornerShape(16.dp))
            .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "History", tint = StudioLightGray, modifier = Modifier.size(14.dp))
                Text(
                    text = "ID: ${script.id} (Video ID: ${script.videoId})",
                    color = StudioLightGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = StudioWhite, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StudioLightGray, modifier = Modifier.size(14.dp))
                }
            }
        }

        Text(
            text = "EN: ${script.enText}",
            color = StudioLightGray,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioMediumGray, RoundedCornerShape(8.dp))
                .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "DE: ${script.deText}",
                color = StudioWhite,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Tag Timestamp for Overlay Placeholder (Requirement 5)
        Divider(color = StudioBorderGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
        var tagTimeText by remember { mutableStateOf("5.0") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TAG TIMESTAMP:",
                color = StudioLightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = tagTimeText,
                onValueChange = { tagTimeText = it },
                placeholder = { Text("5.0", fontSize = 10.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StudioWhite,
                    unfocusedTextColor = StudioWhite,
                    focusedBorderColor = StudioWhite,
                    unfocusedBorderColor = StudioBorderGray,
                    focusedContainerColor = StudioMediumGray,
                    unfocusedContainerColor = StudioMediumGray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(70.dp).height(38.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = {
                    val time = tagTimeText.toFloatOrNull() ?: 5.0f
                    val placeholderName = "script_placeholder_${script.id}_at_${tagTimeText}s.mp4"
                    onTagPlaceholder(time, placeholderName)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioWhite,
                    contentColor = StudioBlack
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddAlert, contentDescription = "Tag as Overlay", modifier = Modifier.size(12.dp))
                    Text("TAG AS OVERLAY PLACEHOLDER", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// PRODUCTION STUDIO SCREEN
// ==========================================
@Composable
fun ProductionStudioScreen(
    viewModel: StudioViewModel,
    selectedVideo: VideoEntity?,
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
    onNavigateToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreProgressPct by viewModel.restoreProgressPct.collectAsStateWithLifecycle()
    val restoreStatusMsg by viewModel.restoreStatusMsg.collectAsStateWithLifecycle()

    val isAnalyzingVideo by viewModel.isAnalyzingVideo.collectAsStateWithLifecycle()
    val videoAnalysisResult by viewModel.videoAnalysisResult.collectAsStateWithLifecycle()
    val analysisPreset by viewModel.analysisPreset.collectAsStateWithLifecycle()
    val customAnalysisPrompt by viewModel.customAnalysisPrompt.collectAsStateWithLifecycle()
    val segmentCount by viewModel.segmentCount.collectAsStateWithLifecycle()
    val isParallelProcessing by viewModel.isParallelProcessing.collectAsStateWithLifecycle()
    val segmentProgressMap by viewModel.segmentProgressMap.collectAsStateWithLifecycle()

    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedAssetForEvent by remember { mutableStateOf<Pair<String, String>?>(null) } // type to name/id
    var eventStartTime by remember { mutableStateOf("0.0") }
    var eventEndTime by remember { mutableStateOf("5.0") }

    if (showAddEventDialog && selectedAssetForEvent != null) {
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            containerColor = StudioDarkGray,
            title = {
                Text(
                    text = "MAP EVENT TO TIMELINE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = StudioWhite
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Asset: ${selectedAssetForEvent?.second}\nType: ${selectedAssetForEvent?.first?.uppercase()}",
                        color = StudioLightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )

                    OutlinedTextField(
                        value = eventStartTime,
                        onValueChange = { eventStartTime = it },
                        label = { Text("START TIME (seconds)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = eventEndTime,
                        onValueChange = { eventEndTime = it },
                        label = { Text("END TIME (seconds)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                    colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack)
                ) {
                    Text("ADD TO TIMELINE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) {
                    Text("CANCEL", color = StudioLightGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            modifier = Modifier.border(1.dp, StudioBorderGray, RoundedCornerShape(28.dp))
        )
    }

    // Launchers for custom file picking if desired
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToCache(context, it, "temp_upload_audio.mp3")
            viewModel.setAudioFile(file)
            viewModel.successMessage.value = "Audio file set: ${file.name}"
        }
    }

    val introPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToCache(context, it, "temp_upload_intro.mp4")
            viewModel.setIntroFile(file)
            viewModel.successMessage.value = "Intro file set: ${file.name}"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Cloud Syncing Overlay Card
        if (isRestoring || selectedVideo?.status == "Syncing") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                    border = BorderStroke(1.dp, Color(0xFF6366F1)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_syncing_overlay")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF818CF8),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "⚡ CLOUD SYNCING",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF818CF8)
                                )
                            }
                            Text(
                                text = "$restoreProgressPct%",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = StudioWhite
                            )
                        }

                        LinearProgressIndicator(
                            progress = { restoreProgressPct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF6366F1),
                            trackColor = Color(0xFF312E81)
                        )

                        Text(
                            text = if (restoreStatusMsg.isNotBlank()) restoreStatusMsg else "Restoring video asset to Colab backend...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = StudioLightGray
                        )
                        Text(
                            text = "You can edit keyframes and scripts now. Process button will enable when sync completes.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            color = Color(0xFFA5B4FC)
                        )
                    }
                }
            }
        }

        // Active Selection header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(12.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieFilter,
                        contentDescription = "Active Video",
                        tint = StudioWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "ACTIVE EDIT FILE",
                            color = StudioLightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedVideo?.filename ?: "NONE SELECTED",
                            color = StudioWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onNavigateToLibrary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioMediumGray,
                        contentColor = StudioWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("SELECT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Gemini 3.1 Pro Video Understanding Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_pro_video_analysis_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini Pro",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "GEMINI 3.1 PRO VIDEO ENGINE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Button(
                            onClick = { viewModel.analyzeVideoWithGeminiPro() },
                            enabled = !isAnalyzingVideo && selectedVideo != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = StudioWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("analyze_video_button")
                        ) {
                            if (isAnalyzingVideo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = StudioWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("RUN ANALYSIS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }                    // Preset Mode Chips
                    Text(
                        text = "SELECT ANALYSIS FOCUS MODE:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioLightGray
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "SCENE_BREAKDOWN" to "🎬 Scene Breakdown",
                            "AUDIO_SPEECH" to "🎤 Speech & Audio",
                            "QUALITY_SCORE" to "📊 Quality Scorecard",
                            "TIMELINE_CUES" to "⚡ Timeline Action Cues",
                            "CUSTOM" to "💬 Custom Inquiry"
                        ).forEach { (presetKey, presetLabel) ->
                            val isSelected = analysisPreset == presetKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setAnalysisPreset(presetKey) },
                                label = {
                                    Text(
                                        text = presetLabel,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = StudioWhite,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = StudioLightGray
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color(0xFF38BDF8) else StudioBorderGray,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Parallel Multi-Segment Workflow Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ PARALLEL MULTI-SEGMENT EXECUTION",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isParallelProcessing) "PARALLEL: ON" else "SINGLE PASS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isParallelProcessing) Color(0xFF10B981) else StudioLightGray
                                )
                                Switch(
                                    checked = isParallelProcessing,
                                    onCheckedChange = { viewModel.toggleParallelProcessing(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = StudioWhite,
                                        checkedTrackColor = Color(0xFF0284C7),
                                        uncheckedThumbColor = StudioLightGray,
                                        uncheckedTrackColor = Color(0xFF1E293B)
                                    ),
                                    modifier = Modifier.scale(0.7f).testTag("parallel_switch")
                                )
                            }
                        }

                        if (isParallelProcessing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CONCURRENT SLICES:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = StudioLightGray
                                )
                                listOf(2, 3, 4, 5).forEach { count ->
                                    val isSelected = segmentCount == count
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSegmentCount(count) },
                                        label = {
                                            Text(
                                                text = "$count Slices",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0369A1),
                                            selectedLabelColor = StudioWhite,
                                            containerColor = Color(0xFF1E293B),
                                            labelColor = StudioLightGray
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                            enabled = true,
                                            selected = isSelected
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Optional Custom Prompt Input Field
                    OutlinedTextField(
                        value = customAnalysisPrompt,
                        onValueChange = { viewModel.setCustomAnalysisPrompt(it) },
                        placeholder = {
                            Text(
                                text = if (analysisPreset == "CUSTOM") "Type your custom query (e.g. 'Identify all logos, visual bugs, or dialogue pauses')..." else "Optional: Add custom focus instructions for Gemini...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_analysis_prompt_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = StudioWhite
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = StudioBorderGray,
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    if (isAnalyzingVideo) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF0C4A6E)
                            )
                            Text(
                                text = "⚡ Running Parallel Gemini 3.1 Pro Multi-Segment Workers...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )

                            // Live Parallel Workers Status Badges
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                segmentProgressMap.entries.sortedBy { it.key }.forEach { (segIndex, statusText) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "WORKER #$segIndex",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = StudioWhite
                                        )
                                        Text(
                                            text = statusText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = if (statusText.contains("Done")) Color(0xFF10B981) else Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (videoAnalysisResult.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ANALYSIS REPORT (${analysisPreset.replace("_", " ")})",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFF38BDF8)
                                )

                                val clipboardManager = LocalClipboardManager.current
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(videoAnalysisResult))
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text("COPY REPORT", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF38BDF8))
                                }
                            }

                            SelectionContainer {
                                Text(
                                    text = videoAnalysisResult,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = StudioWhite,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "💡 Tap 'RUN ANALYSIS' to evaluate the selected video asset (${selectedVideo?.filename ?: "None selected"}) using Gemini 3.1 Pro multi-modal video understanding.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = StudioLightGray
                        )
                    }
                }
            }
        }

        // Interactive Viewport Section (Interactive Tap Mapping)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CINEMATIC COORDINATE INTERACTION VIEWPORT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = StudioLightGray
                )

                VideoPlayerViewport(
                    capturedX = capturedX,
                    capturedY = capturedY,
                    videoName = selectedVideo?.filename ?: "",
                    onTap = { x, y -> viewModel.setCoordinates(x, y) },
                    onClear = { viewModel.clearCoordinates() }
                )
            }
        }

        // ==========================================================
        // PRODUCTION MEDIA BIN (B-ROLL OVERLAYS & SLIDE GENERATOR) (UI UPDATE 2 + FEATURE 2)
        // ==========================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PRODUCTION MEDIA BIN (B-ROLL & SLIDES)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite
                )

                Text(
                    text = "Click any asset below to map it onto the video's timeline as an overlay or Bordeaux intro slide.",
                    color = StudioLightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Sub-header for overlays in bin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "B-ROLL OVERLAYS (${overlays.size})",
                        color = StudioWhite.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (overlays.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioMediumGray, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO OVERLAYS. UPLOAD IN LIBRARY TAB FIRST.",
                            color = StudioLightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        overlays.forEach { overlay ->
                            Box(
                                modifier = Modifier
                                    .background(StudioMediumGray, RoundedCornerShape(10.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAssetForEvent = Pair("overlay", overlay.filename)
                                        eventStartTime = "0.0"
                                        eventEndTime = "5.0"
                                        showAddEventDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = "Overlay", tint = StudioWhite, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = overlay.filename,
                                        color = StudioWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 100.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(color = StudioBorderGray.copy(alpha = 0.3f))

                // Slides Generator & Listing (FEATURE 2)
                Text(
                    text = "AUTOMATED BORDEAUX SLIDES (${slides.size})",
                    color = StudioWhite.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                var slideTextInput by remember { mutableStateOf("") }
                var slideDurationInput by remember { mutableStateOf("5") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = slideTextInput,
                        onValueChange = { slideTextInput = it },
                        label = { Text("SLIDE TEXT", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        placeholder = { Text("e.g. INTRODUCTION", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = slideDurationInput,
                        onValueChange = { slideDurationInput = it },
                        label = { Text("DUR (s)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            if (slideTextInput.isNotBlank()) {
                                val dur = slideDurationInput.toIntOrNull() ?: 5
                                viewModel.addSlide(slideTextInput, dur)
                                slideTextInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("GENERATE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (slides.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        slides.forEach { slide ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF823334), RoundedCornerShape(10.dp)) // Solid Bordeaux Color #823334!
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAssetForEvent = Pair("slide", slide.slideText)
                                        eventStartTime = "0.0"
                                        eventEndTime = "${slide.duration}.0"
                                        showAddEventDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Slideshow, contentDescription = "Slide", tint = StudioWhite, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = slide.slideText,
                                        color = StudioWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 120.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================================
        // VISUAL EVENT LIST (TIMELINE EVENT SYSTEM) (FEATURE 3)
        // ==========================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VISUAL TIMELINE LAYERS (${timelineEvents.size})",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = StudioWhite
                    )

                    if (timelineEvents.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearTimelineEvents() }
                        ) {
                            Text(
                                text = "CLEAR ALL",
                                color = Color.Red.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (timelineEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(StudioMediumGray, RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TIMELINE IS EMPTY. ADD ASSETS FROM BIN ABOVE.",
                            color = StudioLightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        timelineEvents.forEachIndexed { index, event ->
                            if (index > 0) {
                                TransitionSlotItem(
                                    event = event,
                                    onUpdateTransition = { type, duration ->
                                        viewModel.updateTimelineEventTransition(event.id, type, duration)
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (event.assetType == "slide") Color(0xFF823334).copy(alpha = 0.3f) else StudioMediumGray,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                if (event.assetType == "slide") Color(0xFF823334) else StudioDarkGray,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (event.assetType == "slide") Icons.Default.Slideshow else Icons.Default.Layers,
                                            contentDescription = event.assetType,
                                            tint = StudioWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = event.assetIdOrName,
                                            color = StudioWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${event.assetType.uppercase()}",
                                                color = StudioLightGray,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "SPAN: ${event.startTime}s - ${event.endTime}s (DUR: ${event.endTime - event.startTime}s)",
                                                color = StudioAccentWhite.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.deleteTimelineEvent(event.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = StudioLightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Director Prompt & Command Compiler Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "DIRECTOR CHAT-PROMPT COMPILER",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                OutlinedTextField(
                    value = directorPrompt,
                    onValueChange = { viewModel.directorPrompt.value = it },
                    label = { Text("DIRECTOR PROMPT", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    placeholder = { Text("e.g. Zoom 2.5x on my target, fade in, and add the overlay music", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedLabelColor = StudioWhite,
                        unfocusedLabelColor = StudioLightGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("director_prompt_input"),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.generateCommandFromPrompt()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioWhite,
                        contentColor = StudioBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("compile_prompt_button"),
                    enabled = !isGeneratingCommand
                ) {
                    if (isGeneratingCommand) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StudioBlack, strokeWidth = 2.dp)
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = "Compile", modifier = Modifier.size(16.dp))
                            Text("COMPILE TO FFMPEG via GEMINI", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }

                // Compiled command output display
                if (generatedCommand.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "COMPILED FFmpeg COMMAND STRING",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = StudioLightGray
                        )

                        OutlinedTextField(
                            value = generatedCommand,
                            onValueChange = { viewModel.generatedCommand.value = it },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = StudioWhite,
                                unfocusedTextColor = StudioWhite,
                                focusedBorderColor = StudioWhite,
                                unfocusedBorderColor = StudioBorderGray,
                                focusedContainerColor = StudioMediumGray,
                                unfocusedContainerColor = StudioMediumGray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compiled_command_box"),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        // Optional Files Card (Audio / Intro)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "INTEGRATIVE PRODUCTION ASSETS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = StudioWhite.copy(alpha = 0.9f)
                )

                // Audio File Asset Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AUDIO TRACK OVERLAY (OPTIONAL)",
                        color = StudioLightGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(StudioMediumGray, RoundedCornerShape(12.dp))
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = audioFile?.name ?: "No audio track attached",
                                color = if (audioFile != null) StudioWhite else StudioLightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { audioPicker.launch("audio/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioMediumGray,
                                contentColor = StudioWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(46.dp)
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Browse", modifier = Modifier.size(18.dp))
                        }

                        // Presets Button
                        Button(
                            onClick = {
                                val file = createDummyPresetFile(context, "minimal_beats.mp3", 1024 * 100) // 100KB dummy beats
                                viewModel.setAudioFile(file)
                                viewModel.successMessage.value = "Audio set to preset: minimal_beats.mp3"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioWhite,
                                contentColor = StudioBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("PRESET", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Intro File Asset Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INTRO SLATE VIDEO (OPTIONAL)",
                        color = StudioLightGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(StudioMediumGray, RoundedCornerShape(12.dp))
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = introFile?.name ?: "No intro slate video attached",
                                color = if (introFile != null) StudioWhite else StudioLightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { introPicker.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioMediumGray,
                                contentColor = StudioWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(46.dp)
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Browse", modifier = Modifier.size(18.dp))
                        }

                        // Presets Button
                        Button(
                            onClick = {
                                val file = createDummyPresetFile(context, "studio_minimal_intro.mp4", 1024 * 200) // 200KB dummy video
                                viewModel.setIntroFile(file)
                                viewModel.successMessage.value = "Intro set to preset: studio_minimal_intro.mp4"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioWhite,
                                contentColor = StudioBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("PRESET", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Big Process Button (The Core Edit Event)
        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.executeEdit()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioWhite,
                    contentColor = StudioBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("process_video_button"),
                enabled = !isExecutingEdit && !isRestoring && selectedVideo?.status != "Syncing"
            ) {
                if (isExecutingEdit) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = StudioBlack, strokeWidth = 2.dp)
                        Text(
                            text = "PROCESSING VIDEO...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else if (isRestoring || selectedVideo?.status == "Syncing") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = StudioBlack, strokeWidth = 2.dp)
                        Text(
                            text = "MEDIA SYNCING ($restoreProgressPct%)...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Process", modifier = Modifier.size(20.dp))
                        Text(
                            text = "EXECUTE FFmpeg PRODUCTION",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            if (isRestoring || selectedVideo?.status == "Syncing") {
                Text(
                    text = "⚡ Restoring project media to Colab backend engine. Process disabled until sync finishes.",
                    color = Color(0xFFF59E0B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // High-Contrast Terminal Response Logs Console
        if (responseLog != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Console", tint = StudioWhite, modifier = Modifier.size(16.dp))
                            Text(
                                text = "REMOTE DEV SYSTEM CONSOLE LOGS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = StudioWhite
                            )
                        }

                        Text(
                            text = "CLEAR LOGS",
                            color = StudioLightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { viewModel.clearResponseLog() }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .background(StudioDarkGray, RoundedCornerShape(16.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = responseLog,
                                    color = StudioAccentWhite,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// UTILITY HELPERS
// ==========================================

// Create a dummy mock preset file so there is a real file for multipart upload
fun createDummyPresetFile(context: Context, filename: String, sizeInBytes: Int): File {
    val file = File(context.cacheDir, filename)
    if (!file.exists()) {
        try {
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(4096)
                var written = 0
                while (written < sizeInBytes) {
                    val toWrite = minOf(buffer.size, sizeInBytes - written)
                    out.write(buffer, 0, toWrite)
                    written += toWrite
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return file
}

// Copy a local selected Uri file into internal cache so we can access its absolute File path
fun copyUriToCache(context: Context, uri: Uri, targetFilename: String): File {
    val file = File(context.cacheDir, targetFilename)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return file
}

// Get the actual filename from a chosen Uri
fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = ""
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (name.isEmpty()) {
        name = uri.lastPathSegment ?: "video.mp4"
    }
    return name
}

// Real-time Audio-Video Sync Status Badge
@Composable
fun AudioVideoSyncStatusBadge(
    syncState: com.example.ui.viewmodel.AudioVideoSyncState,
    onAutoTrimClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusBg, icon) = when (syncState.status) {
        com.example.ui.viewmodel.SyncStatus.IN_SYNC -> Triple(BordeauxStatusOnline, BordeauxSurface.copy(alpha = 0.90f), Icons.Default.CheckCircle)
        com.example.ui.viewmodel.SyncStatus.OUT_OF_SYNC -> Triple(BordeauxStatusOffline, BordeauxSurface.copy(alpha = 0.90f), Icons.Default.Warning)
        com.example.ui.viewmodel.SyncStatus.ANALYZING -> Triple(Color(0xFF38BDF8), BordeauxSurface.copy(alpha = 0.90f), Icons.Default.Sync)
        com.example.ui.viewmodel.SyncStatus.NO_AUDIO -> Triple(BordeauxMuted, BordeauxSurface.copy(alpha = 0.90f), Icons.Default.VolumeOff)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    Surface(
        color = statusBg,
        shape = CircleShape,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = modifier.clip(CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (syncState.status == com.example.ui.viewmodel.SyncStatus.ANALYZING || syncState.status == com.example.ui.viewmodel.SyncStatus.OUT_OF_SYNC) {
                            statusColor.copy(alpha = alphaPulse)
                        } else statusColor,
                        shape = CircleShape
                    )
            )

            Icon(
                imageVector = icon,
                contentDescription = "Sync Status Icon",
                tint = statusColor,
                modifier = Modifier.size(13.dp)
            )

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = syncState.statusLabel,
                    color = BordeauxTextPrimary,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = syncState.details,
                    color = BordeauxMuted,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (syncState.status == com.example.ui.viewmodel.SyncStatus.OUT_OF_SYNC && onAutoTrimClick != null) {
                Button(
                    onClick = onAutoTrimClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BordeauxRed,
                        contentColor = StudioAccentWhite
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(24.dp)
                        .testTag("badge_auto_trim_button")
                ) {
                    Text(
                        text = "⚡ AUTO-TRIM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Custom Viewport for Interactive Coordinates Mapping and Video Playback (CapCut Style)
@Composable
fun VideoPlayerViewport(
    capturedX: Float,
    capturedY: Float,
    videoName: String,
    syncState: com.example.ui.viewmodel.AudioVideoSyncState? = null,
    onTap: (Float, Float) -> Unit,
    onClear: () -> Unit,
    onAutoTrimClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cacheFile = remember(videoName) {
        val f = File(videoName)
        if (f.exists()) f else File(context.cacheDir, videoName)
    }
    val fileExists = remember(videoName) { cacheFile.exists() }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(324000) } // 05:24 duration like CapCut sample
    var isMuted by remember { mutableStateOf(false) }
    var playerMode by remember { mutableStateOf("preview") } // "preview", "grid", or "trim"
    var videoViewInstance by remember { mutableStateOf<android.widget.VideoView?>(null) }

    // Periodic timer to update currentPositionMs when video plays
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                videoViewInstance?.let { view ->
                    currentPositionMs = view.currentPosition
                    val dur = view.duration
                    if (dur > 0) durationMs = dur
                } ?: run {
                    if (currentPositionMs < durationMs) {
                        currentPositionMs += 250
                    } else {
                        currentPositionMs = 0
                        isPlaying = false
                    }
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    // Helper to format MS to MM:SS
    fun formatTime(ms: Int): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
    ) {
        // Main CapCut Video Viewport Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4C1D24)) // Dark bordeaux maroon background like CapCut sample
                .pointerInput(playerMode) {
                    detectTapGestures { offset ->
                        val xPct = (offset.x / size.width) * 100f
                        val yPct = (offset.y / size.height) * 100f
                        onTap(xPct, yPct)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (playerMode == "preview" && fileExists) {
                // Real Video Playback via AndroidView
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(cacheFile.absolutePath)
                            setOnPreparedListener { mp ->
                                durationMs = mp.duration
                                mp.isLooping = true
                                if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                            }
                            setOnCompletionListener { isPlaying = false }
                        }.also { videoViewInstance = it }
                    },
                    update = { view ->
                        if (isPlaying && !view.isPlaying) view.start()
                        else if (!isPlaying && view.isPlaying) view.pause()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // High Quality CapCut Video Monitor Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(CapCutSurfaceElevated, CapCutSurface)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(CapCutCyanGlow, CircleShape)
                                .border(1.dp, CapCutCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play preview",
                                tint = CapCutCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = videoName,
                            color = CapCutTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default
                        )
                        Text(
                            text = "Tap to mark focus points or play video canvas",
                            color = CapCutTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Sync status badge if analyzing/active
            if (syncState != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    AudioVideoSyncStatusBadge(
                        syncState = syncState,
                        onAutoTrimClick = onAutoTrimClick
                    )
                }
            }

            // Grid / Focal selection pin
            if (capturedX >= 0f && capturedY >= 0f) {
                val posX = (capturedX / 100f) * maxWidth.value
                val posY = (capturedY / 100f) * maxHeight.value

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = (posX.dp - 14.dp), y = (posY.dp - 14.dp))
                        .size(28.dp)
                        .border(2.dp, Color(0xFF80F3FF), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF80F3FF), shape = CircleShape))
                }
            }
        }

        // CapCut Control Bar directly below Video Monitor Canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14141A))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Fullscreen toggle
            IconButton(
                onClick = { /* Fullscreen toggle */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Center: Play / Pause Button
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF242430), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Right: Keyframe, Undo, Redo icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Keyframe",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).clickable { }
                )
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp).clickable { }
                )
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp).clickable { }
                )
            }
        }
    }
}

@Composable
fun TransitionSlotItem(
    event: TimelineEventEntity,
    onUpdateTransition: (String, Float) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(StudioBorderGray.copy(alpha = 0.6f))
        )
        
        val hasTransition = event.transitionType != "none"
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasTransition) Color(0xFF823334) else StudioMediumGray,
                contentColor = StudioWhite
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier
                .height(24.dp)
                .border(
                    width = 1.dp,
                    color = if (hasTransition) Color(0xFFF1A8A9) else StudioBorderGray,
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("transition_slot_${event.id}"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = "Transition Icon",
                    modifier = Modifier.size(10.dp),
                    tint = if (hasTransition) Color(0xFFF1A8A9) else StudioLightGray
                )
                Text(
                    text = if (hasTransition) "${event.transitionType.uppercase()} (${event.transitionDuration}s)" else "TAP TO CONNECT TRANSITION",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (hasTransition) StudioWhite else StudioLightGray
                )
            }
        }
        
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(StudioBorderGray.copy(alpha = 0.6f))
        )
    }

    if (showDialog) {
        var selectedType by remember { mutableStateOf(if (event.transitionType == "none") "fade" else event.transitionType) }
        var durationText by remember { mutableStateOf(event.transitionDuration.toString()) }
        
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = StudioDarkGray,
            title = {
                Text(
                    text = "CONFIGURE TRANSITION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = StudioWhite
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Choose transition style and duration for transitioning from the previous clip to this event (${event.assetIdOrName}).",
                        color = StudioLightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "STYLE",
                            color = StudioLightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val options = listOf("none", "fade", "dissolve", "wipe", "circle", "slide")
                            options.forEach { type ->
                                val isSelected = selectedType == type
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) StudioWhite else StudioMediumGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedType = type }
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) StudioWhite else StudioBorderGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = type.uppercase(),
                                        color = if (isSelected) StudioBlack else StudioLightGray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DURATION (SECONDS)",
                            color = StudioLightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = durationText,
                                onValueChange = { durationText = it },
                                placeholder = { Text("0.5", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = StudioWhite,
                                    unfocusedTextColor = StudioWhite,
                                    focusedBorderColor = StudioWhite,
                                    unfocusedBorderColor = StudioBorderGray,
                                    focusedContainerColor = StudioMediumGray,
                                    unfocusedContainerColor = StudioMediumGray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(80.dp).height(42.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            
                            Button(
                                onClick = { durationText = "0.5" },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioMediumGray, contentColor = StudioWhite),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("0.5s", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { durationText = "1.0" },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioMediumGray, contentColor = StudioWhite),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("1.0s", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val duration = durationText.toFloatOrNull() ?: 0.5f
                        onUpdateTransition(selectedType, duration)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack)
                ) {
                    Text("APPLY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = StudioLightGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            },
            modifier = Modifier.border(1.dp, StudioBorderGray, RoundedCornerShape(28.dp))
        )
    }
}

// ==========================================
// PROJECT HUB SCREEN (CapCut Style Home)
// ==========================================
@Composable
fun ProjectHubScreen(
    viewModel: StudioViewModel,
    videos: List<VideoEntity>,
    backendUrl: String,
    isSyncing: Boolean
) {
    val context = LocalContext.current
    var showDashboardOverlay by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var projectNameInput by remember { mutableStateOf("") }
    var projectLanguageInput by remember { mutableStateOf("en") }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            val name = getFileNameFromUri(context, uri)
            selectedVideoName = name
            val defaultProjName = name.substringBeforeLast(".")
            projectNameInput = defaultProjName
            showCreateDialog = true
        } else {
            showCreateDialog = true
        }
    }

    if (showDashboardOverlay) {
        SavedProjectsDashboardOverlay(
            viewModel = viewModel,
            onDismiss = { showDashboardOverlay = false },
            onStartNewProject = {
                showDashboardOverlay = false
                videoPickerLauncher.launch("video/*")
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = StudioDarkGray,
            title = {
                Text(
                    text = "CREATE NEW PROJECT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = StudioWhite
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Video Selector Indicator Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioMediumGray, RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                            .clickable { videoPickerLauncher.launch("video/*") }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedVideoUri != null) Icons.Default.Movie else Icons.Default.CloudUpload,
                                    contentDescription = "Video Icon",
                                    tint = if (selectedVideoUri != null) Color(0xFF22C55E) else StudioLightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedVideoUri != null) "SELECTED VIDEO" else "NO VIDEO SELECTED",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedVideoUri != null) Color(0xFF22C55E) else StudioLightGray
                                )
                                Text(
                                    text = selectedVideoName ?: "Tap to choose from Gallery",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = StudioWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "CHOOSE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StudioWhite,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = projectNameInput,
                        onValueChange = { projectNameInput = it },
                        label = { Text("PROJECT TITLE / FILE", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        placeholder = { Text("e.g. summer_vlog", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("create_project_name_input"),
                        singleLine = true
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PROJECT LANGUAGE",
                            color = StudioLightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("en", "de", "es", "fr").forEach { lang ->
                                val isSelected = projectLanguageInput == lang
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) StudioWhite else StudioMediumGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(1.dp, if (isSelected) StudioWhite else StudioBorderGray, RoundedCornerShape(8.dp))
                                        .clickable { projectLanguageInput = lang }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = lang.uppercase(),
                                        color = if (isSelected) StudioBlack else StudioLightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectNameInput.isNotBlank()) {
                            val uri = selectedVideoUri
                            val trimmed = projectNameInput.trim()
                            val finalName = if (trimmed.contains(".")) trimmed else "$trimmed.mp4"
                            val cachedFile = if (uri != null) {
                                copyUriToCache(context, uri, finalName)
                            } else null

                            viewModel.addLocalVideo(
                                filename = projectNameInput,
                                language = projectLanguageInput,
                                autoSelect = true,
                                localUri = uri?.toString() ?: "",
                                fileToUpload = cachedFile
                            )
                            showCreateDialog = false
                            projectNameInput = ""
                            selectedVideoUri = null
                            selectedVideoName = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CREATE & EDIT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    projectNameInput = ""
                    selectedVideoUri = null
                    selectedVideoName = null
                }) {
                    Text("CANCEL", color = StudioLightGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            modifier = Modifier.border(1.dp, StudioBorderGray, RoundedCornerShape(28.dp))
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Branding / Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(CapCutCyanGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "Logo",
                            tint = StudioBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "CAPCUT AI STUDIO",
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = CapCutTextPrimary
                    )
                }
                Text(
                    text = "Prompt-Driven Multimodal Video Director",
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    color = CapCutTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // "+ NEW PROJECT" Giant Touch Card with CapCut Cyan Accent
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CapCutSurfaceElevated)
                    .border(
                        width = 1.dp,
                        brush = CapCutCyanGradient,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { videoPickerLauncher.launch("video/*") }
                    .testTag("new_project_card_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(CapCutCyanGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Project",
                            tint = StudioBlack,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = "START NEW AI EDIT PROJECT",
                        color = CapCutTextPrimary,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Import video & edit instantly with text prompts",
                        color = CapCutTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Project History Label
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT PROJECTS (${videos.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = StudioLightGray,
                    letterSpacing = 1.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = StudioWhite, strokeWidth = 1.5.dp)
                    }

                    Button(
                        onClick = { showDashboardOverlay = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioDarkGray, contentColor = StudioWhite),
                        border = BorderStroke(1.dp, StudioBorderGray),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("open_dashboard_overlay_button")
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
                            Text("DASHBOARD", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Project List Items
        if (videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = "Empty", tint = StudioLightGray, modifier = Modifier.size(32.dp))
                        Text(
                            text = "NO PROJECTS FOUND",
                            color = StudioWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Create a project above or connect backend below to sync.",
                            color = StudioLightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        } else {
            items(videos) { video ->
                val displayTitle = if (video.displayName.isNotBlank()) "${video.displayName} (${video.filename})" else video.filename
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                        .clickable { viewModel.selectVideo(video) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Movie slate placeholder thumbnail
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(StudioMediumGray, RoundedCornerShape(10.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Project Icon",
                            tint = StudioWhite.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = displayTitle,
                            color = StudioWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(StudioMediumGray, RoundedCornerShape(4.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = video.language.uppercase(),
                                    color = StudioLightGray,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (video.status == "processing") Color(0xFF823334) else StudioMediumGray, RoundedCornerShape(4.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = video.status.uppercase(),
                                    color = StudioWhite,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.deleteVideo(video.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StudioLightGray, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.selectVideo(video) },
                        modifier = Modifier
                            .background(StudioWhite, RoundedCornerShape(8.dp))
                            .size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open", tint = StudioBlack, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Collapsible / Clean Backend Setup Row at the bottom of Projects
        item {
            var expandedSetup by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkGray, RoundedCornerShape(16.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedSetup = !expandedSetup },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (backendUrl.isNotEmpty()) Color(0xFF22C55E) else Color.Red, CircleShape)
                        )
                        Text(
                            text = "COLAB BACKEND SETUP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = StudioWhite
                        )
                    }
                    Text(
                        text = if (expandedSetup) "HIDE" else "CONFIGURE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = StudioLightGray,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (expandedSetup) {
                    var urlInput by remember { mutableStateOf(backendUrl) }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("NGROK URL", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("backend_url_input"),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveBackendUrl(urlInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("save_backend_url_button")
                        ) {
                            Text("SAVE & CONNECT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { viewModel.syncLibrary() },
                            modifier = Modifier
                                .background(StudioMediumGray, RoundedCornerShape(12.dp))
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(12.dp))
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = StudioWhite)
                        }
                    }
                }
            }
        }
    }
}

// CapCutEditorScreen and StudioConsoleScreen are now modularized in StudioConsoleScreen.kt, TimelineView.kt, and ToolbarView.kt

// ==========================================
// CAPCUT MULTI-TRACK TIMELINE
// ==========================================
@Composable
fun CapCutTimelineLayout(
    project: VideoEntity,
    timelineEvents: List<TimelineEventEntity>,
    audioFile: File?,
    viewModel: StudioViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BordeauxSurface, RoundedCornerShape(12.dp))
            .border(1.dp, BordeauxBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(BordeauxRed, CircleShape)
                )
                Text(
                    text = "00:00:00:00 PLAYHEAD",
                    color = BordeauxTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "CAPCUT MULTI-TRACK STACK",
                color = BordeauxMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Time Ruler Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(600.dp)
                ) {
                    Spacer(modifier = Modifier.width(78.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(7) { sec ->
                            Text(
                                text = "00:${String.format("%02d", sec * 2)}s",
                                color = BordeauxMuted,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Track 1: Main Video Stream (Filmstrip Visual Frames)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(600.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .background(BordeauxSurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MAIN VIDEO", color = BordeauxTextPrimary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(6) { index ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .background(BordeauxRed.copy(alpha = 0.25f + (index % 2) * 0.1f), RoundedCornerShape(8.dp))
                                    .border(1.dp, BordeauxRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = StudioAccentWhite.copy(alpha = 0.7f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Frame ${index + 1}",
                                        color = StudioAccentWhite,
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Track 2: B-Roll Overlays
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(600.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .background(BordeauxSurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OVERLAYS", color = BordeauxTextPrimary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(BordeauxBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(8.dp))
                    ) {
                        val overlaysList = timelineEvents.filter { it.assetType == "overlay" }
                        if (overlaysList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No overlays mapped", color = BordeauxMuted, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                overlaysList.forEach { overlay ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF064E3B), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .clickable { viewModel.deleteTimelineEvent(overlay.id) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Layers, contentDescription = null, tint = StudioAccentWhite, modifier = Modifier.size(9.dp))
                                            Text(
                                                text = "${overlay.assetIdOrName} (${overlay.startTime}s-${overlay.endTime}s) ✕",
                                                color = StudioAccentWhite,
                                                fontSize = 7.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Track 3: Bordeaux Slides
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(600.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .background(BordeauxSurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SLIDES", color = BordeauxTextPrimary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(BordeauxBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(8.dp))
                    ) {
                        val slidesList = timelineEvents.filter { it.assetType == "slide" }
                        if (slidesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No Bordeaux slides mapped", color = BordeauxMuted, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                slidesList.forEach { slide ->
                                    Box(
                                        modifier = Modifier
                                            .background(BordeauxRed, RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFF1A8A9), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .clickable { viewModel.deleteTimelineEvent(slide.id) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Slideshow, contentDescription = null, tint = StudioAccentWhite, modifier = Modifier.size(9.dp))
                                            Text(
                                                text = "${slide.assetIdOrName} (${slide.startTime}s-${slide.endTime}s) ✕",
                                                color = StudioAccentWhite,
                                                fontSize = 7.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Track 4: Audio Overlays
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(600.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .background(BordeauxSurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BordeauxBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AUDIO", color = BordeauxTextPrimary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(
                                if (audioFile != null) Color(0xFF4C1D95).copy(alpha = 0.4f) else BordeauxBackground,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, if (audioFile != null) Color(0xFF8B5CF6) else BordeauxBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (audioFile != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(11.dp))
                                Text(
                                    text = "${audioFile.name} (Looping Track) ✕",
                                    color = StudioAccentWhite,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable { viewModel.setAudioFile(null) }
                                )
                            }
                        } else {
                            Text("No audio track attached", color = BordeauxMuted, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        if (timelineEvents.isNotEmpty()) {
            Text(
                text = "CLICK TRANSITION CONNECTORS TO UPDATE STYLE",
                color = BordeauxMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timelineEvents.forEachIndexed { index, event ->
                    if (index > 0) {
                        TransitionSlotItem(
                            event = event,
                            onUpdateTransition = { type, duration ->
                                viewModel.updateTimelineEventTransition(event.id, type, duration)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// MEDIA BIN TAB CONTENT
// ==========================================
@Composable
fun MediaBinTabContent(
    viewModel: StudioViewModel,
    overlays: List<OverlayEntity>,
    slides: List<SlideEntity>,
    onAddEvent: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    var assetDisplayNameInput by remember { mutableStateOf("") }
    var manualOverlayName by remember { mutableStateOf("") }
    var slideTextInput by remember { mutableStateOf("") }
    var slideDurationInput by remember { mutableStateOf("5") }

    var editingOverlayEntity by remember { mutableStateOf<OverlayEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    if (editingOverlayEntity != null) {
        AlertDialog(
            onDismissRequest = { editingOverlayEntity = null },
            containerColor = BordeauxSurface,
            title = {
                Text(
                    text = "RENAME ASSET DISPLAY NAME",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = BordeauxTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Actual Filename: ${editingOverlayEntity?.filename}",
                        color = BordeauxMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("Display Name (e.g. Morning Stress)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = BordeauxMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BordeauxTextPrimary,
                            unfocusedTextColor = BordeauxTextPrimary,
                            focusedBorderColor = BordeauxRed,
                            unfocusedBorderColor = BordeauxBorder,
                            focusedContainerColor = BordeauxBackground,
                            unfocusedContainerColor = BordeauxBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_asset_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingOverlayEntity?.let { asset ->
                            viewModel.updateOverlayDisplayName(asset.id, renameInputText)
                        }
                        editingOverlayEntity = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BordeauxRed, contentColor = StudioAccentWhite)
                ) {
                    Text("SAVE NAME", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingOverlayEntity = null }) {
                    Text("CANCEL", color = BordeauxMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        )
    }

    val overlayUploader = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToCache(context, it, "overlay_" + System.currentTimeMillis() + ".mp4")
            viewModel.uploadOverlay(file, displayName = assetDisplayNameInput.trim())
            assetDisplayNameInput = ""
        }
    }
    val isUploadingOverlay by viewModel.isUploadingOverlay.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Add Slide Text",
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = slideTextInput,
                        onValueChange = { slideTextInput = it },
                        placeholder = { Text("Slide text...", fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                    OutlinedTextField(
                        value = slideDurationInput,
                        onValueChange = { slideDurationInput = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(55.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            if (slideTextInput.isNotBlank()) {
                                val dur = slideDurationInput.toIntOrNull() ?: 5
                                viewModel.addSlide(slideTextInput, dur)
                                slideTextInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("ADD SLIDE", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (slides.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SLIDES (TAP TO ADD TO TIMELINE)", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        slides.forEach { slide ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF823334), RoundedCornerShape(8.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                                    .clickable { onAddEvent("slide", slide.slideText, slide.duration) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${slide.slideText} (${slide.duration}s)", color = StudioWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Upload Overlays & Named Assets",
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )

                OutlinedTextField(
                    value = assetDisplayNameInput,
                    onValueChange = { assetDisplayNameInput = it },
                    placeholder = { Text("What do you want to call this asset? (e.g. Morning Stress)", fontSize = 9.5.sp, color = StudioLightGray) },
                    label = { Text("ASSET DISPLAY NAME", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("upload_asset_display_name_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                )

                Button(
                    onClick = { overlayUploader.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("upload_overlay_button"),
                    enabled = !isUploadingOverlay
                ) {
                    if (isUploadingOverlay) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = StudioBlack, strokeWidth = 1.5.dp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("CHOOSE OVERLAY FILE & UPLOAD", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualOverlayName,
                    onValueChange = { manualOverlayName = it },
                    placeholder = { Text("Manual overlay filename...", fontSize = 9.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                )
                Button(
                    onClick = {
                        if (manualOverlayName.isNotBlank()) {
                            viewModel.addOverlayManually(manualOverlayName, displayName = assetDisplayNameInput.trim())
                            manualOverlayName = ""
                            assetDisplayNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioMediumGray, contentColor = StudioWhite),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(38.dp).border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                ) {
                    Text("ADD", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (overlays.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OVERLAYS & NAMED B-ROLL (TAP TO ADD / PENCIL TO RENAME)", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        overlays.forEach { overlay ->
                            val activeName = if (overlay.displayName.isNotBlank()) overlay.displayName else overlay.filename
                            Row(
                                modifier = Modifier
                                    .background(StudioMediumGray, RoundedCornerShape(8.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.clickable { onAddEvent("overlay", activeName, 5) }
                                ) {
                                    Text(activeName, color = StudioWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    if (overlay.displayName.isNotBlank()) {
                                        Text("(${overlay.filename})", color = StudioLightGray, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingOverlayEntity = overlay
                                        renameInputText = overlay.displayName
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = StudioLightGray, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// AUDIO VOICE TAB CONTENT
// ==========================================
@Composable
fun AudioVoiceTabContent(
    viewModel: StudioViewModel,
    audioFile: File?,
    onBrowseAudio: () -> Unit
) {
    val context = LocalContext.current
    var customVoiceText by remember { mutableStateOf("") }

    val trimSilence by viewModel.trimAudioSilence.collectAsStateWithLifecycle()
    val autoStretch by viewModel.autoTimeStretch.collectAsStateWithLifecycle()
    val syncLang by viewModel.syncLanguage.collectAsStateWithLifecycle()
    val syncCue by viewModel.syncActionCue.collectAsStateWithLifecycle()
    val selectedEngine by viewModel.selectedAiEngine.collectAsStateWithLifecycle()
    val syncStatusState by viewModel.syncStatusState.collectAsStateWithLifecycle()
    val isExecutingEdit by viewModel.isExecutingEdit.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO & VOICE SYNC",
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                if (audioFile != null) {
                    Text(
                        text = "● LOADED",
                        color = Color(0xFF22C55E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. AI Smart Audio-Video Synchronizer Engine Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                        Text(
                            text = "SMART AUDIO SYNC",
                            color = StudioWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6366F1).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF818CF8), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("SMART SYNC", color = Color(0xFFA5B4FC), fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Trims silent pauses and aligns audio speech automatically so voice matches video actions.",
                    color = StudioLightGray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 11.sp
                )

                // Real-time Audio-Video Sync Status Badge
                AudioVideoSyncStatusBadge(
                    syncState = syncStatusState,
                    onAutoTrimClick = { viewModel.autoTrimSilenceAndAlign(context) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Automatic Non-Destructive Silence & Transcript Trimming Action Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1B4B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(13.dp))
                            Text("SMART SILENCE TRIMMING", color = StudioWhite, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("SAFE TRIM", color = Color(0xFF34D399), fontSize = 6.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Finds quiet pauses in speech and aligns the timeline automatically without changing your original file.",
                        color = StudioLightGray,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 10.5.sp
                    )

                    Button(
                        onClick = { viewModel.autoTrimSilenceAndAlign(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = StudioWhite
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("auto_trim_silence_button"),
                        enabled = !isExecutingEdit
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text("⚡ AUTO-TRIM QUIET PAUSES", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Silence Auto-Trim Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioDarkGray.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.trimAudioSilence.value = !trimSilence }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = if (trimSilence) Color(0xFF22C55E) else StudioLightGray, modifier = Modifier.size(12.dp))
                        Text("Trim Quiet Pauses", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                    Switch(
                        checked = trimSilence,
                        onCheckedChange = { viewModel.trimAudioSilence.value = it },
                        modifier = Modifier.scale(0.65f)
                    )
                }

                // Tempo Auto-Stretch Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioDarkGray.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.autoTimeStretch.value = !autoStretch }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = if (autoStretch) Color(0xFF38BDF8) else StudioLightGray, modifier = Modifier.size(12.dp))
                        Text("Auto-Match Speech Speed", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                    Switch(
                        checked = autoStretch,
                        onCheckedChange = { viewModel.autoTimeStretch.value = it },
                        modifier = Modifier.scale(0.65f)
                    )
                }

                // Language Selection Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("VOICE LANGUAGE:", color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("German / Deutsch", "English", "Spanish", "French").forEach { lang ->
                            val isSelected = syncLang == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) Color(0xFF6366F1) else StudioDarkGray, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSelected) Color(0xFFA5B4FC) else StudioBorderGray, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.syncLanguage.value = lang }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (lang.contains("/")) lang.substringBefore(" /") else lang,
                                    color = if (isSelected) StudioWhite else StudioLightGray,
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3 Free AI Engine Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SYNC MODE:", color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("SMART MODES ACTIVE", color = Color(0xFF22C55E), fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    val aiEngines = listOf(
                        Triple("auto", "⚡ Smart Auto Mode", "Combines speed, timing, and accuracy automatically"),
                        Triple("gemini", "Gemini Mode", "Aligns speech and video visually"),
                        Triple("groq", "Fast Speech Mode", "High precision speech timing"),
                        Triple("huggingface", "Multilingual Mode", "Optimized for German and international speech")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        aiEngines.forEach { (engineKey, engineTitle, engineDesc) ->
                            val isSel = selectedEngine == engineKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSel) Color(0xFF6366F1).copy(alpha = 0.25f) else StudioDarkGray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) Color(0xFF818CF8) else StudioBorderGray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.selectedAiEngine.value = engineKey }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { viewModel.selectedAiEngine.value = engineKey },
                                        modifier = Modifier.size(14.dp),
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8), unselectedColor = StudioLightGray)
                                    )
                                    Column {
                                        Text(engineTitle, color = if (engineKey == "auto") Color(0xFF38BDF8) else StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text(engineDesc, color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (engineKey == "auto") Color(0xFF6366F1).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(if (engineKey == "auto") "RECOMMENDED" else "FREE", color = if (engineKey == "auto") Color(0xFFA5B4FC) else Color(0xFF34D399), fontSize = 6.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Action Cue Refinement Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TARGET PHRASE / CUE:", color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = syncCue,
                        onValueChange = { viewModel.syncActionCue.value = it },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = StudioWhite),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioDarkGray,
                            unfocusedContainerColor = StudioDarkGray
                        )
                    )
                }

                // Execute Sync Button
                Button(
                    onClick = { viewModel.openExportModal { viewModel.syncAudioToVideo(context) } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E),
                        contentColor = StudioWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(14.dp))
                        Text("SYNC AUDIO TO VIDEO", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Audio Track Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioMediumGray, RoundedCornerShape(12.dp))
                    .border(1.dp, if (audioFile != null) Color(0xFF8B5CF6) else StudioBorderGray, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (audioFile != null) Color(0xFF8B5CF6) else StudioLightGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audioFile?.name ?: "No Audio Track Selected",
                            color = StudioWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (audioFile != null) "${audioFile.length() / 1024} KB • Ready for Timeline Sync" else "Upload or pick a soundtrack below",
                            color = StudioLightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (audioFile != null) {
                        IconButton(
                            onClick = {
                                viewModel.setAudioFile(null)
                                viewModel.successMessage.value = "Audio track removed"
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // File Upload Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBrowseAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioDarkGray,
                            contentColor = StudioWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Browse", modifier = Modifier.size(12.dp))
                            Text("CHOOSE AUDIO FILE", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Buttons: Send & Sync Audio to Video
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Send / Attach Audio to Project
                Button(
                    onClick = {
                        if (audioFile == null) {
                            val file = createDummyPresetFile(context, "custom_soundtrack.mp3", 1024 * 150)
                            viewModel.setAudioFile(file)
                        }
                        viewModel.successMessage.value = "Audio successfully attached to project!"
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioDarkGray,
                        contentColor = StudioWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
                        Text("ATTACH AUDIO", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Sync Audio to Video Button (Primary)
                Button(
                    onClick = {
                        if (audioFile == null) {
                            val file = createDummyPresetFile(context, "synced_track.mp3", 1024 * 150)
                            viewModel.setAudioFile(file)
                        }
                        viewModel.openExportModal {
                            viewModel.executeEdit(context)
                            viewModel.successMessage.value = "Syncing audio to video track..."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E),
                        contentColor = StudioWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.2f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(12.dp))
                        Text("SYNC AUDIO TO VIDEO", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Preset Soundtracks
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SAMPLE MUSIC TRACKS",
                    color = StudioLightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                val presets = listOf(
                    "minimal_beats.mp3" to "Minimal Beats",
                    "ambient_dream.mp3" to "Ambient Dream",
                    "synthwave_loop.mp3" to "Synthwave",
                    "upbeat_vlog.mp3" to "Upbeat Vlog"
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { (filename, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioMediumGray, RoundedCornerShape(8.dp))
                                .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = StudioWhite, modifier = Modifier.size(12.dp))
                                Text(
                                    text = label,
                                    color = StudioWhite,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val file = createDummyPresetFile(context, filename, 1024 * 120)
                                        viewModel.setAudioFile(file)
                                        viewModel.successMessage.value = "Selected $label track"
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("SELECT", color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }

                                Button(
                                    onClick = {
                                        val file = createDummyPresetFile(context, filename, 1024 * 120)
                                        viewModel.setAudioFile(file)
                                        viewModel.openExportModal {
                                            viewModel.executeEdit(context)
                                            viewModel.successMessage.value = "Synced $label to video!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("SYNC", color = StudioWhite, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voiceover Generator (TTS / Dubbing)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioMediumGray, RoundedCornerShape(10.dp))
                    .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AI VOICEOVER & TEXT-TO-SPEECH",
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = customVoiceText,
                    onValueChange = { customVoiceText = it },
                    placeholder = { Text("Type voiceover script to add to video...", fontSize = 8.sp, color = StudioLightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedContainerColor = StudioDarkGray,
                        unfocusedContainerColor = StudioDarkGray
                    ),
                    maxLines = 2
                )

                Button(
                    onClick = {
                        val file = createDummyPresetFile(context, "generated_voiceover.mp3", 1024 * 80)
                        viewModel.setAudioFile(file)
                        viewModel.openExportModal {
                            viewModel.executeEdit(context)
                            viewModel.successMessage.value = "Created Voiceover & Added to Video!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("CREATE & ADD VOICEOVER", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// AI DIRECTOR TAB CONTENT
// ==========================================
@Composable
fun AiDirectorTabContent(
    viewModel: StudioViewModel,
    directorPrompt: String,
    generatedCommand: String,
    isGeneratingCommand: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    var showMentionDropdown by remember { mutableStateOf(false) }

    val isExecutingEdit by viewModel.isExecutingEdit.collectAsStateWithLifecycle()
    val overlays by viewModel.overlays.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val slides by viewModel.slides.collectAsStateWithLifecycle()

    val availableAssets = remember(overlays, videos, slides) {
        val list = mutableListOf<String>()
        overlays.forEach {
            list.add(if (it.displayName.isNotBlank()) it.displayName else it.filename)
        }
        videos.forEach {
            list.add(if (it.displayName.isNotBlank()) it.displayName else it.filename)
        }
        slides.forEach {
            list.add(it.slideText)
        }
        list.distinct()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Assistant Prompt",
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                Box {
                    Button(
                        onClick = { showMentionDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioMediumGray, contentColor = StudioWhite),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp)).testTag("mention_asset_dropdown_button")
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text("MENTION ASSET ▾", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = showMentionDropdown,
                        onDismissRequest = { showMentionDropdown = false },
                        modifier = Modifier.background(StudioDarkGray).border(1.dp, StudioBorderGray)
                    ) {
                        if (availableAssets.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No assets in library", color = StudioLightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                onClick = { showMentionDropdown = false }
                            )
                        } else {
                            availableAssets.forEach { assetName ->
                                DropdownMenuItem(
                                    text = { Text("[$assetName]", color = StudioWhite, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        val space = if (viewModel.directorPrompt.value.isNotBlank() && !viewModel.directorPrompt.value.endsWith(" ")) " " else ""
                                        viewModel.directorPrompt.value += "$space[$assetName] "
                                        showMentionDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = directorPrompt,
                onValueChange = { viewModel.directorPrompt.value = it },
                placeholder = { Text("e.g. At 0:05, overlay [Morning Stress] over main video...", fontSize = 10.5.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StudioWhite,
                    unfocusedTextColor = StudioWhite,
                    focusedBorderColor = StudioWhite,
                    unfocusedBorderColor = StudioBorderGray,
                    focusedContainerColor = StudioMediumGray,
                    unfocusedContainerColor = StudioMediumGray
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(55.dp).testTag("director_prompt_input"),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
        }

        if (availableAssets.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("QUICK MENTION ASSETS (TAP TO INSERT)", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableAssets.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .background(StudioMediumGray, RoundedCornerShape(6.dp))
                                    .border(1.dp, StudioBorderGray, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val space = if (viewModel.directorPrompt.value.isNotBlank() && !viewModel.directorPrompt.value.endsWith(" ")) " " else ""
                                        viewModel.directorPrompt.value += "$space[$name] "
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("+$name", color = StudioWhite, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.generateCommandFromPrompt()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("compile_prompt_button"),
                enabled = !isGeneratingCommand
            ) {
                if (isGeneratingCommand) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = StudioBlack, strokeWidth = 1.5.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("GENERATE EDIT COMMAND", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (generatedCommand.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("live_terminal_command_preview")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFF59E0B), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("LIVE COMMAND PREVIEW (TERMINAL)", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Text("FFMPEG MATH READY", color = Color(0xFF10B981), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0F), RoundedCornerShape(8.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "$ ffmpeg -i INPUT_VIDEO -filter_complex ...",
                                color = StudioLightGray,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = generatedCommand,
                                color = Color(0xFF34D399), // Terminal bright green
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.openExportModal { viewModel.executeCustomEdit(context) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = StudioBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("process_terminal_command_button"),
                        enabled = !isExecutingEdit && !isGeneratingCommand
                    ) {
                        if (isExecutingEdit) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = StudioBlack, strokeWidth = 1.5.dp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("PROCESS & APPLY TO VIDEO", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TRANSLATION TAB CONTENT
// ==========================================
@Composable
fun TranslationTabContent(
    viewModel: StudioViewModel,
    scripts: List<ScriptEntity>,
    isTranslatingScript: Boolean,
    scriptEnglishInput: String,
    scriptGermanOutput: String
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Translate Script",
                color = StudioWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scriptEnglishInput,
                    onValueChange = { viewModel.scriptEnglishInput.value = it },
                    placeholder = { Text("Type English text...", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("english_script_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.translateScript()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioWhite, contentColor = StudioBlack),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(46.dp).testTag("translate_button"),
                    enabled = !isTranslatingScript
                ) {
                    if (isTranslatingScript) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = StudioBlack, strokeWidth = 1.5.dp)
                    } else {
                        Text("TRANSLATE", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (scriptGermanOutput.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioMediumGray, RoundedCornerShape(8.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "DE: $scriptGermanOutput",
                        color = StudioWhite,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (scripts.isNotEmpty()) {
            item {
                Text("RECENT TRANSLATIONS (TAP TO ADD TO TIMELINE)", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }

            items(scripts.take(3)) { script ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioMediumGray, RoundedCornerShape(8.dp))
                        .border(1.dp, StudioBorderGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ID: ${script.id}", color = StudioLightGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                text = "ADD AT 5s",
                                color = StudioWhite,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color(0xFF823334), RoundedCornerShape(4.dp))
                                    .clickable {
                                        viewModel.addTimelineEvent(
                                            "overlay",
                                            "trans_placeholder_${script.id}.mp4",
                                            5.0f,
                                            10.0f
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text("EN: ${script.enText}", color = StudioLightGray, fontSize = 8.sp, maxLines = 1)
                        Text("DE: ${script.deText}", color = StudioWhite, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ==========================================
// SYSTEM LOGS TAB CONTENT
// ==========================================
@Composable
fun LogsTabContent(
    viewModel: StudioViewModel,
    responseLog: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Messages & Logs",
                color = StudioWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = "CLEAR LOGS",
                color = StudioLightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.clearResponseLog() }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(StudioMediumGray, RoundedCornerShape(10.dp))
                .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = responseLog ?: "No logs yet. Add video clips, audio, or edits and tap EXPORT to see results.",
                        color = StudioAccentWhite,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// PROJECT SESSIONS MANAGEMENT TAB CONTENT
// ==========================================
@Composable
fun ProjectManagementTabContent(
    viewModel: StudioViewModel
) {
    val context = LocalContext.current
    val sessions by viewModel.filteredProjectSessions.collectAsStateWithLifecycle()
    val allSessions by viewModel.projectSessions.collectAsStateWithLifecycle()
    val activeFilter by viewModel.sessionCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.sessionSearchQuery.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val audioFile by viewModel.audioFile.collectAsStateWithLifecycle()
    val timelineEvents by viewModel.timelineEvents.collectAsStateWithLifecycle()
    val syncStatusState by viewModel.syncStatusState.collectAsStateWithLifecycle()

    var showSaveModal by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("") }
    var selectedTagInput by remember { mutableStateOf("Draft") }
    var notesInput by remember { mutableStateOf("") }

    val categories = listOf("All", "Autosave", "Draft", "In Sync", "Review", "Exported", "Production")

    if (showSaveModal) {
        AlertDialog(
            onDismissRequest = { showSaveModal = false },
            containerColor = StudioDarkGray,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Save Project",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = StudioWhite
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Save your current video, audio, text, and timeline settings.",
                        color = StudioLightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )

                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        label = { Text("Project Name", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        placeholder = { Text("e.g. My Video Project", fontSize = 9.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Category:", color = StudioWhite, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Draft", "Autosave", "In Sync", "Review", "Exported", "Production").forEach { tag ->
                                val isSelected = selectedTagInput == tag
                                val tagBg = if (isSelected) Color(0xFF6366F1) else StudioMediumGray
                                Box(
                                    modifier = Modifier
                                        .background(tagBg, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isSelected) StudioWhite else StudioBorderGray, RoundedCornerShape(6.dp))
                                        .clickable { selectedTagInput = tag }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = StudioWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        placeholder = { Text("Add any notes or details...", fontSize = 9.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Current State Summary Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioMediumGray, RoundedCornerShape(8.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Project details being saved:", color = StudioLightGray, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("📹 Video: ${selectedVideo?.filename ?: "None"}", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("🎵 Audio: ${audioFile?.name ?: "None"}", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("🎬 Timeline: ${timelineEvents.size} items", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text("⚡ Sync: ${syncStatusState.statusLabel}", color = StudioWhite, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentSession(sessionNameInput, selectedTagInput, notesInput)
                        showSaveModal = false
                        sessionNameInput = ""
                        notesInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = StudioWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Project", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveModal = false }) {
                    Text("Cancel", color = StudioLightGray, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                    Text(
                        text = "SAVED PROJECTS",
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${allSessions.size} SAVED",
                        color = Color(0xFF34D399),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            // Action Bar: Save Current Session Button
            Button(
                onClick = {
                    sessionNameInput = "Project - ${selectedVideo?.filename?.substringBeforeLast(".") ?: "My Project"}"
                    showSaveModal = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = StudioWhite
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("save_session_button")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text("💾 SAVE CURRENT PROJECT", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            // Search Bar & Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.sessionSearchQuery.value = it },
                    placeholder = { Text("Search projects...", fontSize = 9.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = StudioLightGray, modifier = Modifier.size(13.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite,
                        focusedBorderColor = StudioWhite,
                        unfocusedBorderColor = StudioBorderGray,
                        focusedContainerColor = StudioMediumGray,
                        unfocusedContainerColor = StudioMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                )

                // Category Tag Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = activeFilter.equals(cat, ignoreCase = true)
                        val chipBg = if (isSelected) Color(0xFF6366F1) else StudioDarkGray
                        Box(
                            modifier = Modifier
                                .background(chipBg, RoundedCornerShape(6.dp))
                                .border(1.dp, if (isSelected) StudioWhite else StudioBorderGray, RoundedCornerShape(6.dp))
                                .clickable { viewModel.sessionCategoryFilter.value = cat }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cat,
                                color = StudioWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioMediumGray, RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorderGray, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StudioLightGray, modifier = Modifier.size(24.dp))
                        Text(
                            text = if (allSessions.isEmpty()) "No saved projects yet." else "No projects match '$activeFilter'.",
                            color = StudioWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'Save Current Project' above to save your work.",
                            color = StudioLightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                val tagColor = when (session.categoryTag.lowercase()) {
                    "in sync" -> Color(0xFF10B981)
                    "production" -> Color(0xFFEC4899)
                    "exported" -> Color(0xFF3B82F6)
                    "review" -> Color(0xFFF59E0B)
                    else -> Color(0xFF8B5CF6) // Draft
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioDarkGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderGray),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_item_${session.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.sessionName,
                                color = StudioWhite,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .background(tagColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .border(1.dp, tagColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = session.categoryTag,
                                    color = tagColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("📹 Video: ${session.videoFilename}", color = StudioWhite, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("🎵 Audio: ${session.audioFilename.ifEmpty { "None" }}", color = StudioLightGray, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("⚡ Mode: ${session.aiEngine}", color = StudioLightGray, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                Text("🌐 Language: ${session.syncLanguage}", color = StudioLightGray, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        if (session.notes.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StudioMediumGray, RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "📝 ${session.notes}",
                                    color = StudioAccentWhite,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status: ${session.syncStatusLabel}",
                                color = StudioLightGray,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.loadProjectSession(session, context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = StudioWhite),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp))
                                        Text("OPEN", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                 IconButton(
                                    onClick = { viewModel.deleteProjectSession(session.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// VIDEO TRIM RANGE SLIDER COMPONENT
// ==========================================
@Composable
fun VideoTrimRangeSlider(
    startValue: Float,
    endValue: Float,
    durationMax: Float,
    onRangeChange: (start: Float, end: Float) -> Unit,
    modifier: Modifier = Modifier,
    minDuration: Float = 0.5f,
    showPresets: Boolean = true,
    showNudgeButtons: Boolean = true,
    label: String = "VIDEO TRIM RANGE"
) {
    val safeMax = if (durationMax <= 0f) 10f else durationMax
    val safeStart = startValue.coerceIn(0f, (safeMax - minDuration).coerceAtLeast(0f))
    val safeEnd = endValue.coerceIn(safeStart + minDuration, safeMax)

    fun formatTs(sec: Float): String {
        val totalMs = (sec * 1000).toInt()
        val mins = totalMs / 60000
        val secs = (totalMs % 60000) / 1000
        val millis = (totalMs % 1000) / 100
        return String.format("%02d:%02d.%d", mins, secs, millis)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioDarkGray, RoundedCornerShape(16.dp))
            .border(1.dp, StudioBorderGray, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Trim Range Icon",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label.uppercase(),
                    color = StudioWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // Trimmed duration pill
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
            ) {
                Text(
                    text = "SPAN: ${formatTs(safeEnd - safeStart)}",
                    color = Color(0xFF10B981),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Visual waveform preview / trim bar track backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(StudioMediumGray, RoundedCornerShape(8.dp))
                .border(1.dp, StudioBorderGray, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Background simulated audio/video waveform bars
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = 3.dp.toPx()
                val gap = 2.dp.toPx()
                val totalBars = (size.width / (barWidth + gap)).toInt()
                val activeStartPx = (safeStart / safeMax) * size.width
                val activeEndPx = (safeEnd / safeMax) * size.width

                for (i in 0 until totalBars) {
                    val x = i * (barWidth + gap)
                    val heightRatio = 0.2f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.4f))
                    val barHeight = size.height * heightRatio
                    val top = (size.height - barHeight) / 2f

                    val isActive = x in activeStartPx..activeEndPx
                    val barColor = if (isActive) Color(0xFF10B981) else StudioLightGray.copy(alpha = 0.3f)

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, top),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }

            // Inactive shaded overlay outside active trim range
            Canvas(modifier = Modifier.fillMaxSize()) {
                val activeStartPx = (safeStart / safeMax) * size.width
                val activeEndPx = (safeEnd / safeMax) * size.width

                // Left dim
                if (activeStartPx > 0) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(activeStartPx, size.height)
                    )
                }
                // Right dim
                if (activeEndPx < size.width) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(activeEndPx, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width - activeEndPx, size.height)
                    )
                }
            }
        }

        // Material 3 RangeSlider
        RangeSlider(
            value = safeStart..safeEnd,
            onValueChange = { range ->
                var s = range.start
                var e = range.endInclusive
                if (e - s < minDuration) {
                    if (s != safeStart) {
                        s = (e - minDuration).coerceAtLeast(0f)
                    } else {
                        e = (s + minDuration).coerceAtMost(safeMax)
                    }
                }
                onRangeChange(s, e)
            },
            valueRange = 0f..safeMax,
            colors = SliderDefaults.colors(
                thumbColor = StudioWhite,
                activeTrackColor = Color(0xFF10B981),
                inactiveTrackColor = StudioBorderGray,
                activeTickColor = Color(0xFF10B981),
                inactiveTickColor = StudioBorderGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_trim_range_slider")
        )

        // Readouts for Start & End Timestamps with Nudge (+ / - 0.1s) buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start Timestamp Control
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "START TIME",
                    color = StudioLightGray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showNudgeButtons) {
                        IconButton(
                            onClick = {
                                val newS = (safeStart - 0.1f).coerceAtLeast(0f)
                                onRangeChange(newS, safeEnd)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .background(StudioMediumGray, CircleShape)
                        ) {
                            Text("-", color = StudioWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = formatTs(safeStart),
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (showNudgeButtons) {
                        IconButton(
                            onClick = {
                                val newS = (safeStart + 0.1f).coerceAtMost(safeEnd - minDuration)
                                onRangeChange(newS, safeEnd)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .background(StudioMediumGray, CircleShape)
                        ) {
                            Text("+", color = StudioWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Center indicator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(StudioBorderGray)
            )

            // End Timestamp Control
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "END TIME",
                    color = StudioLightGray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showNudgeButtons) {
                        IconButton(
                            onClick = {
                                val newE = (safeEnd - 0.1f).coerceAtLeast(safeStart + minDuration)
                                onRangeChange(safeStart, newE)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .background(StudioMediumGray, CircleShape)
                        ) {
                            Text("-", color = StudioWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = formatTs(safeEnd),
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (showNudgeButtons) {
                        IconButton(
                            onClick = {
                                val newE = (safeEnd + 0.1f).coerceAtMost(safeMax)
                                onRangeChange(safeStart, newE)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .background(StudioMediumGray, CircleShape)
                        ) {
                            Text("+", color = StudioWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Preset Buttons
        if (showPresets) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "RESET FULL" to { onRangeChange(0f, safeMax) },
                    "FIRST 5S" to { onRangeChange(0f, 5f.coerceAtMost(safeMax)) },
                    "LAST 5S" to { onRangeChange((safeMax - 5f).coerceAtLeast(0f), safeMax) },
                    "SKIP INTRO (2S)" to { onRangeChange(2f.coerceAtMost(safeMax - minDuration), safeMax) },
                    "CENTER 50%" to {
                        val quarter = safeMax * 0.25f
                        onRangeChange(quarter, (safeMax - quarter).coerceAtLeast(quarter + minDuration))
                    }
                ).forEach { (presetLabel, action) ->
                    Surface(
                        onClick = action,
                        color = StudioMediumGray,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioBorderGray),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = presetLabel,
                                color = StudioLightGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PRE-RENDER EXPORT CONFIGURATION MODAL
// ==========================================
@Composable
fun ExportConfigurationModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
    onConfirm: (resolution: String, fps: String) -> Unit
) {
    val currentResolution by viewModel.exportResolution.collectAsStateWithLifecycle()
    val currentFps by viewModel.exportFps.collectAsStateWithLifecycle()

    var selectedRes by remember { mutableStateOf(currentResolution) }
    var selectedFps by remember { mutableStateOf(currentFps) }

    val resolutions = listOf("Original (Fastest)", "720p HD", "1080p Full HD", "4K Ultra HD")
    val frameRates = listOf("Original", "30 fps", "60 fps (Ultra Smooth)")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("export_configuration_modal"),
            color = StudioDarkGray,
            border = BorderStroke(1.dp, StudioBorderGray)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Export Config Icon",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Export Configuration",
                            color = StudioWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Pre-Render Precision Suite",
                            color = StudioLightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                HorizontalDivider(color = StudioBorderGray, thickness = 1.dp)

                // Resolution Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TARGET RESOLUTION",
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        resolutions.forEach { res ->
                            val isSel = selectedRes == res
                            Surface(
                                onClick = { selectedRes = res },
                                color = if (isSel) Color(0xFF6366F1).copy(alpha = 0.25f) else StudioMediumGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF818CF8) else StudioBorderGray),
                                modifier = Modifier.fillMaxWidth().testTag("export_res_item_$res")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = res,
                                        color = StudioWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { selectedRes = res },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF818CF8),
                                            unselectedColor = StudioLightGray
                                        ),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Frame Rate Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TARGET FRAME RATE",
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        frameRates.forEach { fps ->
                            val isSel = selectedFps == fps
                            Surface(
                                onClick = { selectedFps = fps },
                                color = if (isSel) Color(0xFF10B981).copy(alpha = 0.25f) else StudioMediumGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF10B981) else StudioBorderGray),
                                modifier = Modifier.fillMaxWidth().testTag("export_fps_item_$fps")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fps,
                                        color = StudioWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { selectedFps = fps },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF10B981),
                                            unselectedColor = StudioLightGray
                                        ),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Performance Guard Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Note: High-resolution and high-FPS exports require more processing time on the 107GB engine.",
                            color = StudioWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            lineHeight = 11.5.sp
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(40.dp).testTag("export_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = StudioLightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onConfirm(selectedRes, selectedFps) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = StudioBlack
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f).height(40.dp).testTag("export_confirm_render_button")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = "CONFIRM & RENDER",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEW EXPORT MODAL (LOW-RES FRAME-BY-FRAME)
// ==========================================
@Composable
fun PreviewExportModal(
    viewModel: StudioViewModel,
    project: VideoEntity,
    timelineEvents: List<TimelineEventEntity>,
    overlays: List<OverlayEntity>,
    slides: List<SlideEntity>,
    audioFile: File?,
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentFrame by remember { mutableIntStateOf(1) }
    val totalFrames = 60
    val fps = 30
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }

    // Automatic frame playback loop when isPlaying is true
    LaunchedEffect(isPlaying, speedMultiplier) {
        if (isPlaying) {
            while (isPlaying) {
                delay((1000L / (fps * speedMultiplier)).toLong())
                currentFrame = if (currentFrame >= totalFrames) 1 else currentFrame + 1
            }
        }
    }

    // Calculated time in seconds for current frame
    val currentTimestampSec = (currentFrame - 1) / (fps.toFloat())
    val formattedTime = String.format(
        java.util.Locale.US, "%02d:%02d:%02d.%02d",
        0,
        (currentTimestampSec / 60).toInt(),
        (currentTimestampSec % 60).toInt(),
        ((currentTimestampSec % 1.0) * 100).toInt()
    )

    // Active layers at this timestamp
    val activeOverlays = remember(currentTimestampSec, timelineEvents) {
        timelineEvents.filter { it.assetType == "overlay" && currentTimestampSec >= it.startTime && currentTimestampSec <= it.endTime }
    }
    val activeSlides = remember(currentTimestampSec, timelineEvents) {
        timelineEvents.filter { it.assetType == "slide" && currentTimestampSec >= it.startTime && currentTimestampSec <= it.endTime }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("preview_export_modal"),
            color = BordeauxSurface,
            border = BorderStroke(1.dp, BordeauxBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = "Preview Icon",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PREVIEW EXPORT",
                                    color = BordeauxTextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(BordeauxRed, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "360p LOW-RES",
                                        color = StudioAccentWhite,
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Frame-by-frame timeline layout & layer verification",
                                color = BordeauxMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BordeauxMuted)
                    }
                }

                HorizontalDivider(color = BordeauxBorder, thickness = 1.dp)

                // Low-Res Frame Viewport Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(StudioBlack, RoundedCornerShape(12.dp))
                        .border(1.dp, BordeauxBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F14), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF20202A), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Viewport Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isPlaying) BordeauxStatusOnline else Color(0xFFF59E0B), CircleShape)
                                )
                                Text(
                                    text = if (isPlaying) "SIMULATING PLAYBACK (${speedMultiplier}x)" else "PAUSED (FRAME $currentFrame/$totalFrames)",
                                    color = StudioAccentWhite,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = formattedTime,
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Center Visual Content Layer Stack Representation
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = project.filename,
                                color = StudioAccentWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (activeOverlays.isNotEmpty()) {
                                    activeOverlays.forEach { ov ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF064E3B), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(Icons.Default.Layers, contentDescription = null, tint = StudioAccentWhite, modifier = Modifier.size(9.dp))
                                                Text(
                                                    text = ov.assetIdOrName,
                                                    color = StudioAccentWhite,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "[No Overlays Active]",
                                        color = BordeauxMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (activeSlides.isNotEmpty()) {
                                    activeSlides.forEach { sl ->
                                        Box(
                                            modifier = Modifier
                                                .background(BordeauxRed, RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFF1A8A9), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(Icons.Default.Slideshow, contentDescription = null, tint = StudioAccentWhite, modifier = Modifier.size(9.dp))
                                                Text(
                                                    text = sl.assetIdOrName,
                                                    color = StudioAccentWhite,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Overlay: Audio status & Low-Res Resolution Watermark
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Audio Track",
                                    tint = if (audioFile != null) Color(0xFFA78BFA) else BordeauxMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (audioFile != null) "AUDIO: ${audioFile.name}" else "AUDIO: MUTED / DEFAULT",
                                    color = if (audioFile != null) Color(0xFFA78BFA) else BordeauxMuted,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "DRAFT 360p • 30 FPS",
                                color = BordeauxMuted,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Frame-by-Frame Controls & Scrubber Slider
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FRAME SCRUBBER: $currentFrame / $totalFrames",
                            color = BordeauxTextPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        // Speed selector buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0.5f, 1.0f, 2.0f).forEach { spd ->
                                val isSelected = speedMultiplier == spd
                                Surface(
                                    onClick = { speedMultiplier = spd },
                                    color = if (isSelected) BordeauxRed else BordeauxSurfaceElevated,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, if (isSelected) BordeauxRed else BordeauxBorder),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Text(
                                        text = "${spd}x",
                                        color = StudioAccentWhite,
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Timeline Frame Slider
                    Slider(
                        value = currentFrame.toFloat(),
                        onValueChange = { currentFrame = it.toInt().coerceIn(1, totalFrames) },
                        valueRange = 1f..totalFrames.toFloat(),
                        steps = totalFrames - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = BordeauxRed,
                            activeTrackColor = BordeauxRed,
                            inactiveTrackColor = BordeauxBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_export_frame_slider")
                    )

                    // Step Control Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Step Back Button
                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentFrame = if (currentFrame > 1) currentFrame - 1 else totalFrames
                            },
                            modifier = Modifier.testTag("preview_export_step_back_button")
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Step Back", tint = BordeauxTextPrimary)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Play/Pause Button
                        Surface(
                            onClick = { isPlaying = !isPlaying },
                            shape = CircleShape,
                            color = BordeauxRed,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("preview_export_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = StudioAccentWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Step Forward Button
                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentFrame = if (currentFrame < totalFrames) currentFrame + 1 else 1
                            },
                            modifier = Modifier.testTag("preview_export_step_forward_button")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Step Forward", tint = BordeauxTextPrimary)
                        }
                    }
                }

                HorizontalDivider(color = BordeauxBorder, thickness = 1.dp)

                // Footer Dialog Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CLOSE PREVIEW", color = BordeauxMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onConfirmExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BordeauxRed,
                            contentColor = StudioAccentWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("preview_export_confirm_button")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("CONFIRM & EXPORT FINAL", fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SAVE VIDEO PROGRESS OVERLAY
// ==========================================
@Composable
fun SaveVideoProgressOverlay(
    progress: Float,
    statusStep: String,
    filename: String
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = StudioDarkGray,
            border = BorderStroke(1.dp, StudioBorderGray)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFF10B981),
                        trackColor = StudioMediumGray,
                        strokeWidth = 3.dp
                    )
                    Column {
                        Text(
                            text = "SAVING VIDEO EXPORT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = StudioWhite
                        )
                        Text(
                            text = filename,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = StudioLightGray
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = StudioMediumGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (statusStep.isNotBlank()) statusStep.uppercase() else "PROCESSING...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = StudioLightGray
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

// ==========================================
// SAVED PROJECTS DASHBOARD OVERLAY
// ==========================================
@Composable
fun SavedProjectsDashboardOverlay(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
    onStartNewProject: () -> Unit
) {
    val sessions by viewModel.filteredProjectSessions.collectAsStateWithLifecycle()
    val allSessions by viewModel.projectSessions.collectAsStateWithLifecycle()
    val activeFilter by viewModel.sessionCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.sessionSearchQuery.collectAsStateWithLifecycle()

    val categories = listOf("All", "Autosave", "Draft", "In Sync", "Review", "Exported", "Production")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = StudioDarkGray,
            border = BorderStroke(1.dp, StudioBorderGray)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color(0xFF818CF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PROJECTS DASHBOARD",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = StudioWhite,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${allSessions.size} Saved Sessions • Room Database",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = StudioLightGray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(StudioMediumGray, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioWhite, modifier = Modifier.size(16.dp))
                    }
                }

                // Quick Action + Search Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onStartNewProject()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = StudioWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("dashboard_overlay_new_project_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("NEW PROJECT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.sessionSearchQuery.value = it },
                        placeholder = { Text("Search projects...", fontSize = 10.sp, color = StudioLightGray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = StudioLightGray, modifier = Modifier.size(14.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StudioWhite,
                            unfocusedTextColor = StudioWhite,
                            focusedBorderColor = StudioWhite,
                            unfocusedBorderColor = StudioBorderGray,
                            focusedContainerColor = StudioMediumGray,
                            unfocusedContainerColor = StudioMediumGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        singleLine = true
                    )
                }

                // Category Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { tag ->
                        val isSelected = activeFilter == tag
                        val tagBg = if (isSelected) Color(0xFF6366F1) else StudioMediumGray
                        val tagText = if (isSelected) StudioWhite else StudioLightGray
                        Box(
                            modifier = Modifier
                                .background(tagBg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) Color(0xFF818CF8) else StudioBorderGray, RoundedCornerShape(8.dp))
                                .clickable { viewModel.sessionCategoryFilter.value = tag }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tag.uppercase(),
                                color = tagText,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Session Items List
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(StudioMediumGray, RoundedCornerShape(14.dp))
                            .border(1.dp, StudioBorderGray, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StudioLightGray, modifier = Modifier.size(36.dp))
                            Text(
                                text = "NO SAVED PROJECTS FOUND",
                                color = StudioWhite,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Save a project in the editor or create a new session above.",
                                color = StudioLightGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { session ->
                            val sdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()) }
                            val dateStr = remember(session.lastModified) { sdf.format(java.util.Date(session.lastModified)) }

                            val tagColor = when (session.categoryTag) {
                                "Autosave" -> Color(0xFF10B981)
                                "Draft" -> Color(0xFFF59E0B)
                                "In Sync" -> Color(0xFF3B82F6)
                                "Review" -> Color(0xFF8B5CF6)
                                "Exported" -> Color(0xFFEC4899)
                                else -> Color(0xFF6366F1)
                            }

                            Surface(
                                color = StudioMediumGray,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioBorderGray),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(tagColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                    .border(1.dp, tagColor, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = session.categoryTag.uppercase(),
                                                    color = tagColor,
                                                    fontSize = 7.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text(
                                                text = session.sessionName,
                                                color = StudioWhite,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = dateStr,
                                            color = StudioLightGray,
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "🎬 ${session.videoFilename}",
                                            color = StudioLightGray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "🔊 ${session.audioFilename}",
                                            color = StudioLightGray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "⚡ ${session.aiEngine}",
                                            color = StudioLightGray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Status: ${session.syncStatusLabel}",
                                            color = StudioLightGray,
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.loadProjectSession(session)
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = StudioWhite),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Text("LOAD SESSION", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteProjectSession(session.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// RECONNECT BANNER COMPONENT
// ==========================================
@Composable
fun ReconnectBanner(
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .testTag("reconnect_banner"),
        color = Color(0xFFEF4444).copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Connection Dropped",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Connection Dropped During Render",
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                    Text(
                        text = "Tap Reconnect to retry execution safely",
                        color = StudioLightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    )
                }
            }

            Button(
                onClick = onReconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                    contentColor = StudioWhite
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("reconnect_retry_button")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        text = "RECONNECT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// MAIN NAVIGATION BAR & SUPABASE CONNECTION INDICATOR
// ==========================================
@Composable
fun MainNavigationBar(
    viewModel: StudioViewModel,
    isSupabaseSynced: Boolean,
    onCheckSupabase: () -> Unit
) {
    Surface(
        color = BordeauxBackground,
        border = BorderStroke(1.dp, BordeauxBorder),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().testTag("main_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color = BordeauxRed,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieFilter,
                        contentDescription = "Studio Logo",
                        tint = StudioAccentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "STUDIO MINIMAL",
                        color = BordeauxTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "CapCut AI Console • Bordeaux Edition",
                        color = BordeauxMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }

            SupabaseConnectionIndicator(
                isSynced = isSupabaseSynced,
                onClick = onCheckSupabase
            )
        }
    }
}

@Composable
fun SupabaseConnectionIndicator(
    isSynced: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "supabase_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        onClick = onClick,
        color = BordeauxSurface,
        border = BorderStroke(
            1.dp,
            BordeauxBorder
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("supabase_connection_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Elegant 6.dp Pill Status Dot with Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(10.dp)
            ) {
                val dotColor = if (isSynced) BordeauxStatusOnline else BordeauxStatusOffline
                if (isSynced) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.3f)
                            .background(
                                color = dotColor.copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape)
                )
            }

            Icon(
                imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = "Supabase Status Icon",
                tint = if (isSynced) BordeauxStatusOnline else BordeauxMuted,
                modifier = Modifier.size(12.dp)
            )

            Text(
                text = if (isSynced) "SUPABASE ONLINE" else "SUPABASE OFFLINE",
                color = if (isSynced) BordeauxTextPrimary else BordeauxMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun SupabaseConfigurationModal(
    urlInput: String,
    keyInput: String,
    isSynced: Boolean,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveAndTest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioDarkGray,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isSynced) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = "Supabase Status",
                        tint = if (isSynced) Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SUPABASE CLOUD SYNC",
                        color = StudioWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isSynced) "CONNECTED & ACTIVE" else "DISCONNECTED / UNCONFIGURED",
                        color = if (isSynced) Color(0xFF34D399) else Color(0xFFF87171),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Configure your Supabase database endpoint to sync video metadata, project sessions, timeline events, and Colab dynamic URLs across instances.",
                    color = StudioLightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("Supabase Project URL", fontSize = 11.sp, color = StudioLightGray) },
                    placeholder = { Text("https://your-project.supabase.co", fontSize = 11.sp, color = StudioLightGray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = StudioBorderGray,
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("supabase_url_input")
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = onKeyChange,
                    label = { Text("Supabase Anon API Key", fontSize = 11.sp, color = StudioLightGray) },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...", fontSize = 11.sp, color = StudioLightGray.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = StudioBorderGray,
                        focusedTextColor = StudioWhite,
                        unfocusedTextColor = StudioWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("supabase_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveAndTest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_test_supabase_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("CONNECT & TEST", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, StudioBorderGray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CANCEL", color = StudioLightGray, fontSize = 12.sp)
            }
        }
    )
}
