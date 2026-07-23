package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.OverlayEntity
import com.example.data.db.ScriptEntity
import com.example.data.db.SlideEntity
import com.example.data.db.TimelineEventEntity
import com.example.data.db.VideoEntity
import com.example.ui.viewmodel.StudioViewModel
import java.io.File

val CapCutCharcoal = Color(0xFF0C0C10)
val CapCutDarkSlate = Color(0xFF16161E)
val CapCutBorderSlate = Color(0xFF2A2A38)
val CapCutBordeauxRed = Color(0xFF823334)
val CapCutMutedGray = Color(0xFFA0A0B0)
val CapCutTextWhite = Color(0xFFF0F0F5)

data class ActionTool(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tag: String
)

/**
 * Zone 4: Bottom Action Bar & ModalBottomSheet Tool Drawers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarView(
    viewModel: StudioViewModel,
    project: VideoEntity,
    overlays: List<OverlayEntity>,
    slides: List<SlideEntity>,
    timelineEvents: List<TimelineEventEntity>,
    scripts: List<ScriptEntity>,
    audioFile: File?,
    directorPrompt: String,
    generatedCommand: String,
    isGeneratingCommand: Boolean,
    isExecutingEdit: Boolean,
    onAddTimelineEvent: (type: String, name: String, duration: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSheetTool by remember { mutableStateOf<String?>(null) }
    
    val tools = listOf(
        ActionTool("edit", "Edit", Icons.Default.ContentCut, "tool_btn_edit"),
        ActionTool("audio", "Audio", Icons.Default.MusicNote, "tool_btn_audio"),
        ActionTool("text", "Text", Icons.Default.Title, "tool_btn_text"),
        ActionTool("overlay", "Overlay", Icons.Default.Layers, "tool_btn_overlay"),
        ActionTool("captions", "Captions", Icons.Default.ClosedCaption, "tool_btn_captions"),
        ActionTool("brand", "Brand", Icons.Default.AutoAwesome, "tool_btn_brand")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CapCutDarkSlate)
            .border(BorderStroke(1.dp, CapCutBorderSlate))
            .navigationBarsPadding()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { tool ->
                val isSelected = activeSheetTool == tool.id
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) CapCutBordeauxRed.copy(alpha = 0.25f) else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) CapCutBordeauxRed else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { activeSheetTool = tool.id }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(tool.tag)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isSelected) CapCutBordeauxRed else Color(0xFF222230),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = if (isSelected) CapCutTextWhite else CapCutMutedGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tool.title,
                        color = if (isSelected) CapCutTextWhite else CapCutMutedGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }

    activeSheetTool?.let { toolId ->
        ModalBottomSheet(
            onDismissRequest = { activeSheetTool = null },
            containerColor = CapCutDarkSlate,
            contentColor = CapCutTextWhite,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(CapCutBorderSlate, CircleShape)
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                when (toolId) {
                    "edit" -> EditToolDrawerContent(
                        viewModel = viewModel,
                        project = project,
                        onDismiss = { activeSheetTool = null }
                    )
                    "audio" -> AudioToolDrawerContent(
                        viewModel = viewModel,
                        audioFile = audioFile,
                        isExecutingEdit = isExecutingEdit,
                        onDismiss = { activeSheetTool = null }
                    )
                    "text" -> TextToolDrawerContent(
                        viewModel = viewModel,
                        scripts = scripts,
                        onDismiss = { activeSheetTool = null }
                    )
                    "overlay" -> OverlayToolDrawerContent(
                        viewModel = viewModel,
                        overlays = overlays,
                        slides = slides,
                        onAddEvent = onAddTimelineEvent,
                        onDismiss = { activeSheetTool = null }
                    )
                    "captions" -> CaptionsToolDrawerContent(
                        viewModel = viewModel,
                        scripts = scripts,
                        onDismiss = { activeSheetTool = null }
                    )
                    "brand" -> BrandToolDrawerContent(
                        viewModel = viewModel,
                        directorPrompt = directorPrompt,
                        generatedCommand = generatedCommand,
                        isGeneratingCommand = isGeneratingCommand,
                        onDismiss = { activeSheetTool = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditToolDrawerContent(
    viewModel: StudioViewModel,
    project: VideoEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var speed by remember { mutableFloatStateOf(1.0f) }
    var volume by remember { mutableFloatStateOf(100f) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EDIT & TRIM CONTROLS",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.autoTrimSilenceAndAlign(context)
                    onDismiss()
                },
                modifier = Modifier.weight(1f).testTag("drawer_edit_autotrim"),
                colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed, contentColor = CapCutTextWhite),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AUTO-TRIM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    viewModel.clearCoordinates()
                    onDismiss()
                },
                modifier = Modifier.weight(1f).testTag("drawer_edit_clear_focal"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = CapCutMutedGray),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CapCutBorderSlate)
            ) {
                Icon(Icons.Default.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CLEAR FOCAL", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            border = BorderStroke(1.dp, CapCutBorderSlate),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speed Multiplier", color = CapCutMutedGray, fontSize = 12.sp)
                    Text("${"%.1f".format(speed)}x", color = CapCutTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = CapCutBordeauxRed,
                        activeTrackColor = CapCutBordeauxRed,
                        inactiveTrackColor = CapCutBorderSlate
                    )
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            border = BorderStroke(1.dp, CapCutBorderSlate),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Master Volume", color = CapCutMutedGray, fontSize = 12.sp)
                    Text("${volume.toInt()}%", color = CapCutTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = CapCutBordeauxRed,
                        activeTrackColor = CapCutBordeauxRed,
                        inactiveTrackColor = CapCutBorderSlate
                    )
                )
            }
        }
    }
}

@Composable
private fun AudioToolDrawerContent(
    viewModel: StudioViewModel,
    audioFile: File?,
    isExecutingEdit: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = File(context.cacheDir, "temp_upload_audio.mp3")
            context.contentResolver.openInputStream(it)?.use { input ->
                java.io.FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            viewModel.setAudioFile(file)
            viewModel.successMessage.value = "Audio track updated: ${file.name}"
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO & SOUNDTRACK",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
            border = BorderStroke(1.dp, CapCutBorderSlate),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
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
                            .background(CapCutBordeauxRed.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = CapCutBordeauxRed, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = audioFile?.name ?: "No Custom Audio Track Loaded",
                            color = CapCutTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (audioFile != null) "${audioFile.length() / 1024} KB • Ready for sync" else "Tap browse to select MP3 / WAV",
                            color = CapCutMutedGray,
                            fontSize = 10.sp
                        )
                    }
                }

                Button(
                    onClick = { audioPicker.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("drawer_audio_browse")
                ) {
                    Text("Browse", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                viewModel.openExportModal { viewModel.syncAudioToVideo(context) }
                onDismiss()
            },
            enabled = !isExecutingEdit,
            colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed, contentColor = CapCutTextWhite),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_audio_sync_btn")
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SYNC AUDIO TO VIDEO TIMELINE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TextToolDrawerContent(
    viewModel: StudioViewModel,
    scripts: List<ScriptEntity>,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TEXT & TRANSLATION TOOL",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Enter subtitle text or script line...", color = CapCutMutedGray, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B1B26),
                unfocusedContainerColor = Color(0xFF1B1B26),
                focusedBorderColor = CapCutBordeauxRed,
                unfocusedBorderColor = CapCutBorderSlate,
                focusedTextColor = CapCutTextWhite,
                unfocusedTextColor = CapCutTextWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_text_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.scriptEnglishInput.value = textInput
                        viewModel.translateScript()
                        textInput = ""
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f).testTag("drawer_text_add_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed, contentColor = CapCutTextWhite),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ADD CAPTION CHIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    viewModel.translateScript()
                    onDismiss()
                },
                modifier = Modifier.weight(1f).testTag("drawer_text_translate_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = CapCutTextWhite),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CapCutBorderSlate)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI TRANSLATE", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OverlayToolDrawerContent(
    viewModel: StudioViewModel,
    overlays: List<OverlayEntity>,
    slides: List<SlideEntity>,
    onAddEvent: (type: String, name: String, duration: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val overlayPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = File(context.cacheDir, "temp_overlay.png")
            context.contentResolver.openInputStream(it)?.use { input ->
                java.io.FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            viewModel.uploadOverlay(file, file.name)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OVERLAY & GRAPHICS BIN",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        Button(
            onClick = { overlayPicker.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_overlay_upload_btn")
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("UPLOAD NEW OVERLAY / B-ROLL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (overlays.isEmpty() && slides.isEmpty()) {
            Text(
                text = "No media overlays added yet. Upload images or create graphics to place on Track 2.",
                color = CapCutMutedGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                overlays.forEach { overlay ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
                        border = BorderStroke(1.dp, CapCutBorderSlate),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(overlay.displayName, color = CapCutTextWhite, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    onAddEvent("overlay", overlay.displayName, 5.0f)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+ Track 2", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionsToolDrawerContent(
    viewModel: StudioViewModel,
    scripts: List<ScriptEntity>,
    onDismiss: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI CAPTIONS & SUBTITLES",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        Button(
            onClick = {
                viewModel.translateScript()
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_captions_auto_gen")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("GENERATE AUTO CAPTIONS FROM AUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Active Caption Chips (${scripts.size}):",
            color = CapCutMutedGray,
            fontSize = 11.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            scripts.take(3).forEach { script ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
                    border = BorderStroke(1.dp, CapCutBorderSlate),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• ${script.enText}",
                        color = CapCutTextWhite,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandToolDrawerContent(
    viewModel: StudioViewModel,
    directorPrompt: String,
    generatedCommand: String,
    isGeneratingCommand: Boolean,
    onDismiss: () -> Unit
) {
    var promptInput by remember { mutableStateOf(directorPrompt) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI DIRECTOR & BRAND ASSETS",
                color = CapCutTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = CapCutMutedGray)
            }
        }

        OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            placeholder = { Text("e.g. Cut to hero stress clip at 00:03, add watermark top right...", color = CapCutMutedGray, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B1B26),
                unfocusedContainerColor = Color(0xFF1B1B26),
                focusedBorderColor = CapCutBordeauxRed,
                unfocusedBorderColor = CapCutBorderSlate,
                focusedTextColor = CapCutTextWhite,
                unfocusedTextColor = CapCutTextWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_brand_prompt_input")
        )

        Button(
            onClick = {
                viewModel.generateCommandFromPrompt()
                onDismiss()
            },
            enabled = !isGeneratingCommand,
            colors = ButtonDefaults.buttonColors(containerColor = CapCutBordeauxRed, contentColor = CapCutTextWhite),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("drawer_brand_generate_btn")
        ) {
            if (isGeneratingCommand) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CapCutTextWhite, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXECUTE AI DIRECTOR COMMAND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
