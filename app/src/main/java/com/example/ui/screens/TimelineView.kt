package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.OverlayEntity
import com.example.data.db.ScriptEntity
import com.example.data.db.TimelineEventEntity
import com.example.data.db.VideoEntity
import com.example.ui.viewmodel.StudioViewModel
import java.io.File

/**
 * Zone 3: Multi-Track Timeline with stationary red vertical playhead line in center.
 * Tracks:
 * 1. Main Video (Thumbnail cards with 8.dp rounded corners)
 * 2. Overlays ([Hero_Stress], B-roll clips)
 * 3. Captions / Subtitles (Text chips)
 * 4. Audio Waveform
 */
@Composable
fun TimelineView(
    project: VideoEntity,
    timelineEvents: List<TimelineEventEntity>,
    overlays: List<OverlayEntity>,
    scripts: List<ScriptEntity>,
    audioFile: File?,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedTrackIndex by remember { mutableIntStateOf(0) } // 0: Main, 1: Overlays, 2: Captions, 3: Audio

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CapCutDarkSlate, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, CapCutBorderSlate), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        // Timeline Header: Timecode + Track Selector Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                        .background(CapCutBordeauxRed, CircleShape)
                )
                Text(
                    text = "00:00 / 00:30",
                    color = CapCutTextWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "4-TRACK TIMELINE",
                color = CapCutMutedGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Timeline Viewport Box with Stationary Red Playhead
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CapCutCharcoal)
                .border(1.dp, CapCutBorderSlate, RoundedCornerShape(8.dp))
        ) {
            // Horizontally Scrollable Tracks Container
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Left Spacer to align 00:00 at center playhead line
                Spacer(modifier = Modifier.width(160.dp))

                Column(
                    modifier = Modifier
                        .width(800.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Time Ruler Ticks
                    TimelineRuler(totalSeconds = 30)

                    // Track 1: Main Video Track
                    MainVideoTrack(
                        project = project,
                        isSelected = selectedTrackIndex == 0,
                        onClick = { selectedTrackIndex = 0 }
                    )

                    // Track 2: Overlays Track
                    OverlaysTrack(
                        events = timelineEvents,
                        overlays = overlays,
                        isSelected = selectedTrackIndex == 1,
                        onClick = { selectedTrackIndex = 1 }
                    )

                    // Track 3: Captions / Subtitles Track
                    CaptionsTrack(
                        scripts = scripts,
                        isSelected = selectedTrackIndex == 2,
                        onClick = { selectedTrackIndex = 2 }
                    )

                    // Track 4: Audio Waveform Track
                    AudioWaveformTrack(
                        audioFile = audioFile,
                        isSelected = selectedTrackIndex == 3,
                        onClick = { selectedTrackIndex = 3 }
                    )
                }

                // Right Spacer for full scrolling past playhead
                Spacer(modifier = Modifier.width(160.dp))
            }

            // STATIONARY CENTER RED PLAYHEAD LINE
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.Center)
            ) {
                drawLine(
                    color = CapCutBordeauxRed,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3f
                )
            }

            // Stationary Red Playhead Marker Handle at top center
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-2).dp)
                    .size(width = 12.dp, height = 12.dp)
                    .background(CapCutBordeauxRed, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun TimelineRuler(totalSeconds: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(Color(0xFF121218)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val step = 5
        for (sec in 0..totalSeconds step step) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(8.dp)
                        .background(CapCutMutedGray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("00:%02d", sec),
                    color = CapCutMutedGray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MainVideoTrack(
    project: VideoEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF1E1E2A),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CapCutBordeauxRed else CapCutBorderSlate
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(vertical = 4.dp)
            .testTag("track_main_video")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CapCutBordeauxRed.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = CapCutBordeauxRed, modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.filename,
                    color = CapCutTextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Track 1 • Main Video Clip • 1080P",
                    color = CapCutMutedGray,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .background(CapCutBordeauxRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MAIN",
                    color = CapCutTextWhite,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OverlaysTrack(
    events: List<TimelineEventEntity>,
    overlays: List<OverlayEntity>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF1A1A24),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CapCutBordeauxRed else CapCutBorderSlate
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(vertical = 4.dp)
            .testTag("track_overlays")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            Text(
                text = "Track 2 Overlays:",
                color = CapCutMutedGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayChips = if (events.isNotEmpty()) {
                    events.map { it.assetIdOrName }
                } else if (overlays.isNotEmpty()) {
                    overlays.map { it.displayName }
                } else {
                    listOf("[Hero_Stress]", "B-roll_01.png")
                }

                displayChips.take(3).forEach { name ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF223040), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = name,
                            color = Color(0xFF7DD3FC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionsTrack(
    scripts: List<ScriptEntity>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF1A1A24),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CapCutBordeauxRed else CapCutBorderSlate
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(vertical = 4.dp)
            .testTag("track_captions")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
            Text(
                text = "Track 3 Captions:",
                color = CapCutMutedGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val captionTexts = if (scripts.isNotEmpty()) {
                    scripts.map { it.enText }
                } else {
                    listOf("Welcome to video editor", "AI Powered Subtitles")
                }

                captionTexts.take(2).forEach { txt ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF322818), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = txt,
                            color = Color(0xFFFDE68A),
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveformTrack(
    audioFile: File?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF181D26),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CapCutBordeauxRed else CapCutBorderSlate
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(vertical = 4.dp)
            .testTag("track_audio_waveform")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))

            Text(
                text = audioFile?.name ?: "Track 4 Audio Waveform",
                color = Color(0xFF6EE7B7),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
            ) {
                val barWidth = 3.dp.toPx()
                val gap = 2.dp.toPx()
                val count = (size.width / (barWidth + gap)).toInt()
                val waveColor = Color(0xFF10B981)

                for (i in 0 until count) {
                    val heightRatio = ((i * 37 + 13) % 100) / 100f
                    val barHeight = size.height * (0.2f + heightRatio * 0.8f)
                    val x = i * (barWidth + gap)
                    val y = (size.height - barHeight) / 2f

                    drawRoundRect(
                        color = waveColor,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}
