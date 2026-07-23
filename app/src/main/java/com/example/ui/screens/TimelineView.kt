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
    var selectedTrackIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        // Timeline Header: Timecode (00:00 / 05:24) & Ruler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "00:00 / 05:24",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("00:00", "00:01", "00:02", "00:03", "00:04").forEach { tick ->
                    Text(
                        text = tick,
                        color = Color(0xFFA0A0B0),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Multi-Track Viewport with Left Quick Tools & Stationary Center White Playhead Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF14141A))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Quick Actions Column (CapCut style: AI clipper, Cover, Captions status)
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF101014))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI Clipper button
                    Surface(
                        color = Color(0xFF1E222A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = "AI clipper", tint = Color(0xFF80F3FF), modifier = Modifier.size(16.dp))
                            Text("AI clipper", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Cover selector button
                    Surface(
                        color = Color(0xFF1E222A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Cover", tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Cover", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Floating captions status tag (h captions 98% >)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E3840), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF80F3FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF80F3FF), modifier = Modifier.size(10.dp))
                            Text("h captions 98% >", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Horizontally Scrollable Tracks Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState)
                    ) {
                        Spacer(modifier = Modifier.width(100.dp))

                        Column(
                            modifier = Modifier
                                .width(900.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Track 1: Main Video Track (with thumbnail strip & + button)
                            MainVideoTrackCapCut(
                                project = project,
                                isSelected = selectedTrackIndex == 0,
                                onClick = { selectedTrackIndex = 0 }
                            )

                            // Track 2: Primary Captions / Subtitles Track (Yellow CapCut Chips)
                            CaptionsTrackCapCut(
                                title = "willkommen",
                                subtitle = "machen wir dein...",
                                isSelected = selectedTrackIndex == 1,
                                onClick = { selectedTrackIndex = 1 }
                            )

                            // Track 3: Secondary Translation Subtitles Track (Yellow CapCut Chips)
                            CaptionsTrackCapCut(
                                title = "welcome to Office",
                                subtitle = "we make your workin...",
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

                        Spacer(modifier = Modifier.width(100.dp))
                    }

                    // STATIONARY CENTER WHITE PLAYHEAD NEEDLE LINE
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.Center)
                    ) {
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 3f
                        )
                    }

                    // Stationary White Playhead Needle Head Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(width = 10.dp, height = 10.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MainVideoTrackCapCut(
    project: VideoEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF5A1C20),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF80F3FF) else Color(0xFF381014)
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF381014)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("mm", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Plus add clip button at end of track
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add clip", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CaptionsTrackCapCut(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Yellow Subtitle Chip 1
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFB800), RoundedCornerShape(4.dp))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    Text(
                        text = title,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Yellow Subtitle Chip 2
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFB800), RoundedCornerShape(4.dp))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    Text(
                        text = subtitle,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        color = Color(0xFF141C24),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF80F3FF) else Color(0xFF223040)
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF80F3FF), modifier = Modifier.size(14.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
            ) {
                val barWidth = 3.dp.toPx()
                val gap = 2.dp.toPx()
                val count = (size.width / (barWidth + gap)).toInt()
                val waveColor = Color(0xFF80F3FF)

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
