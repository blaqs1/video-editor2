package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ChatMessageEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun PromptChatView(
    viewModel: StudioViewModel,
    chatMessages: List<ChatMessageEntity>,
    isProcessingPrompt: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var inputPromptText by remember { mutableStateOf("") }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, isProcessingPrompt) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickActionChips = remember {
        listOf(
            "📝 Karaoke Captions" to "Add karaoke captions with yellow active word highlight",
            "✂️ Trim Silence" to "Auto-trim dead silent pauses from video",
            "⚡ Speed Up 1.5x" to "Speed up the video 1.5x without changing audio pitch",
            "🎨 Black & White" to "Apply cinematic black and white monochrome filter",
            "🎬 B-Roll Overlay" to "Overlay B-Roll asset at 00:02s for 4 seconds",
            "🎵 Add Audio" to "Attach background audio track with automatic ducking"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CapCutSurfaceGlass, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.dp, CapCutBorderSubtle, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Chat Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CapCutAiGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant",
                        tint = StudioAccentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "CAPCUT AI DIRECTOR",
                        color = CapCutTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isProcessingPrompt) "Generating edit graph..." else "Prompt-driven video execution engine",
                        color = if (isProcessingPrompt) CapCutCyan else CapCutTextMuted,
                        fontSize = 9.sp
                    )
                }
            }

            if (chatMessages.isNotEmpty()) {
                TextButton(
                    onClick = {
                        viewModel.selectedVideo.value?.let { video ->
                            viewModel.clearMessagesForVideo(video.id)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear Chat", color = CapCutTextMuted, fontSize = 10.sp)
                }
            }
        }

        Divider(color = CapCutBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // Message List Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty()) {
                // Empty state greeting
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(CapCutPurpleGlow, CircleShape)
                            .border(1.dp, CapCutPurple.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = null,
                            tint = CapCutCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "How would you like to edit this video?",
                        color = CapCutTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Describe your edits in plain English or choose a quick chip below. No manual timeline tweaking required!",
                        color = CapCutTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        ChatMessageItem(
                            message = msg,
                            onRender = { viewModel.renderFromMessage(msg.id, context) }
                        )
                    }

                    if (isProcessingPrompt) {
                        item {
                            AiTypingIndicatorCard()
                        }
                    }
                }
            }
        }

    var selectedEffectCategory by remember { mutableStateOf("TRANSITIONS") }

    val transitionsList = remember {
        listOf(
            "🔀 Slide In" to "Apply smooth slide in transition",
            "✨ Wipe Left" to "Apply wipe left transition",
            "🌌 Dissolve" to "Apply smooth dissolve transition at 5s",
            "⚡ Sci-Fi Glitch" to "Apply futuristic cyber glitch RGB transition",
            "🌀 Radial Spin" to "Apply radial spin transition",
            "⬛ Fade to Black" to "Apply fade to black transition"
        )
    }

    val filtersList = remember {
        listOf(
            "🌆 Cyberpunk" to "Apply Cyberpunk neon color grade filter",
            "📼 Vintage 35mm" to "Apply vintage 35mm film grain filter",
            "🎬 Monochrome" to "Apply high contrast black and white monochrome filter",
            "🌅 Warm Sunset" to "Apply warm golden sunset color grade",
            "💥 High Contrast" to "Boost contrast and saturation 1.4x"
        )
    }

    val motionAudioList = remember {
        listOf(
            "📝 Karaoke Captions" to "Add karaoke captions with yellow word highlighting",
            "✂️ Trim Silence" to "Auto-trim dead silent pauses from video",
            "⚡ Speed 1.5x" to "Speed up video 1.5x with pitch-safe voice",
            "📱 TikTok 9:16" to "Re-frame video to TikTok 9:16 vertical ratio with blurred background",
            "🖼️ Picture-in-Picture" to "Add picture-in-picture floating overlay in top right"
        )
    }

    val activeEffectsList = when (selectedEffectCategory) {
        "FILTERS" -> filtersList
        "MOTION" -> motionAudioList
        else -> transitionsList
    }

    // Effects & Transitions Palette Drawer
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EFFECTS & TRANSITIONS PALETTE",
                color = CapCutTextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Category Tab Chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("TRANSITIONS", "FILTERS", "MOTION").forEach { cat ->
                    val isSelected = selectedEffectCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CapCutCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) CapCutCyan else CapCutBorderSubtle,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedEffectCategory = cat }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) CapCutCyan else CapCutTextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Horizontally Scrolling CapCut Video Preview Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeEffectsList.forEach { (label, promptText) ->
                CapCutEffectVideoCard(
                    label = label,
                    promptText = promptText,
                    onSelect = { viewModel.submitEditPrompt(promptText, context) },
                    onAddToPrompt = {
                        inputPromptText = if (inputPromptText.isBlank()) promptText else "$inputPromptText and $promptText"
                    }
                )
            }
        }
    }

        Spacer(modifier = Modifier.height(8.dp))

        // Floating Prompt Input Bar
        Surface(
            color = CapCutInputBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (inputPromptText.isNotBlank()) CapCutCyanGlow else CapCutBorderSubtle
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { /* File attachment callback */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach media asset",
                        tint = CapCutTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedTextField(
                    value = inputPromptText,
                    onValueChange = { inputPromptText = it },
                    placeholder = {
                        Text(
                            text = "Describe your video edit prompt...",
                            color = CapCutTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CapCutTextPrimary,
                        unfocusedTextColor = CapCutTextPrimary,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field"),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp)
                )

                IconButton(
                    onClick = {
                        if (inputPromptText.isNotBlank()) {
                            focusManager.clearFocus()
                            val promptToSubmit = inputPromptText
                            inputPromptText = ""
                            viewModel.submitEditPrompt(promptToSubmit, context)
                        }
                    },
                    enabled = inputPromptText.isNotBlank() && !isProcessingPrompt,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputPromptText.isNotBlank()) CapCutCyanGradient
                            else Brush.linearGradient(listOf(CapCutSurfaceElevated, CapCutSurfaceElevated))
                        )
                        .testTag("send_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Submit prompt",
                        tint = if (inputPromptText.isNotBlank()) StudioBlack else CapCutTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    onRender: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User Chat Bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(CapCutCyanGradient, RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = StudioBlack,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                )
            }
        } else {
            // AI Response Card
            Surface(
                color = CapCutSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (message.status) {
                        "rendered" -> CapCutGreen.copy(alpha = 0.6f)
                        "error" -> CapCutRed.copy(alpha = 0.6f)
                        "ready" -> CapCutCyan.copy(alpha = 0.6f)
                        else -> CapCutPurple.copy(alpha = 0.4f)
                    }
                ),
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CapCutCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when (message.status) {
                                    "rendered" -> "Render Complete"
                                    "ready" -> "Edit Plan Generated"
                                    "rendering" -> "Rendering Video..."
                                    "error" -> "Processing Error"
                                    else -> "AI Director Response"
                                },
                                color = CapCutTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status pill indicator
                        val (statusText, statusBg, statusFg) = when (message.status) {
                            "rendered" -> Triple("DONE", CapCutGreen.copy(alpha = 0.2f), CapCutGreen)
                            "ready" -> Triple("READY", CapCutCyan.copy(alpha = 0.2f), CapCutCyan)
                            "rendering" -> Triple("BUSY", CapCutAmber.copy(alpha = 0.2f), CapCutAmber)
                            "error" -> Triple("ERROR", CapCutRed.copy(alpha = 0.2f), CapCutRed)
                            else -> Triple("AI", CapCutPurple.copy(alpha = 0.2f), CapCutPurple)
                        }

                        Box(
                            modifier = Modifier
                                .background(statusBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusFg,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = message.content,
                        color = CapCutTextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    if (message.renderResultMsg.isNotBlank()) {
                        if (message.status == "error") {
                            Text(
                                text = message.renderResultMsg,
                                color = CapCutRed,
                                fontSize = 10.5.sp
                            )
                        } else if (message.status == "rendered") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CapCutInputBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, CapCutCyanGlow, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = message.renderResultMsg,
                                        color = CapCutTextPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CapCutCyanGradient)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Render",
                                    tint = StudioBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "RENDER EDIT ON GPU",
                                    color = StudioBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiTypingIndicatorCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "d2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "d3"
    )

    Surface(
        color = CapCutSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CapCutPurple.copy(alpha = 0.3f)),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = CapCutPurple,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "AI is thinking",
                color = CapCutTextSecondary,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.size(4.dp).background(CapCutCyan.copy(alpha = alpha1), CircleShape))
                Box(modifier = Modifier.size(4.dp).background(CapCutCyan.copy(alpha = alpha2), CircleShape))
                Box(modifier = Modifier.size(4.dp).background(CapCutCyan.copy(alpha = alpha3), CircleShape))
            }
        }
    }
}

@Composable
fun CapCutEffectVideoCard(
    label: String,
    promptText: String,
    onSelect: () -> Unit,
    onAddToPrompt: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "motion_pulse")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(
        color = CapCutSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CapCutCyanGlow.copy(alpha = pulsingAlpha * 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini Animated Subject Video Preview Monitor Box
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(CapCutPurple, CapCutCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Preview Motion",
                    tint = StudioAccentWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = label,
                    color = CapCutTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // + PROMPT Button: Appends effect to prompt box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CapCutInputBg)
                        .border(1.dp, CapCutCyan, RoundedCornerShape(6.dp))
                        .clickable { onAddToPrompt() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to Prompt",
                            tint = CapCutCyan,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "+ PROMPT",
                            color = CapCutCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
