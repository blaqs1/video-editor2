package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// ===================================================
// PREMIUM CAPCUT-INSPIRED COLOR SYSTEM
// ===================================================

// Core Surfaces
val CapCutBackground = Color(0xFF09090D)
val CapCutSurface = Color(0xFF13131D)
val CapCutSurfaceElevated = Color(0xFF1B1B2B)
val CapCutSurfaceGlass = Color(0xCC13131D)
val CapCutInputBg = Color(0xFF101018)

// Vibrant Accent Colors
val CapCutCyan = Color(0xFF00D4FF)          // Signature CapCut Cyan / Primary Action
val CapCutCyanGlow = Color(0x3300D4FF)      // Subtle Cyan Glow
val CapCutPurple = Color(0xFF8B5CF6)        // AI Multimodal Engine Accent
val CapCutPurpleGlow = Color(0x338B5CF6)    // AI Glow
val CapCutPink = Color(0xFFEC4899)          // Highlight / Special Tool Accent
val CapCutAmber = Color(0xFFF59E0B)         // Captions / Warnings
val CapCutGreen = Color(0xFF10B981)         // Success / Online
val CapCutRed = Color(0xFFEF4444)           // Error / Offline / Bordeaux

// Text Hierarchy
val CapCutTextPrimary = Color(0xFFF9FAFB)
val CapCutTextSecondary = Color(0xFF9CA3AF)
val CapCutTextMuted = Color(0xFF6B7280)

// Borders & Dividers
val CapCutBorderSubtle = Color(0xFF222234)
val CapCutBorderFocused = Color(0xFF383854)
val CapCutBorderGlow = Color(0x8000D4FF)

// Gradients
val CapCutCyanGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00D4FF), Color(0xFF0099FF))
)
val CapCutAiGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
)
val CapCutDarkGlassGradient = Brush.verticalGradient(
    colors = listOf(Color(0xEE181826), Color(0xEE10101A))
)

// Legacy Aliases for Compatibility
val StudioBlack = CapCutBackground
val StudioWhite = CapCutTextPrimary
val StudioDarkGray = CapCutSurface
val StudioMediumGray = CapCutSurfaceElevated
val StudioLightGray = CapCutTextSecondary
val StudioBorderGray = CapCutBorderSubtle
val StudioAccentWhite = Color(0xFFFFFFFF)

val BordeauxBackground = CapCutBackground
val BordeauxSurface = CapCutSurface
val BordeauxSurfaceElevated = CapCutSurfaceElevated
val BordeauxBorder = CapCutBorderSubtle
val BordeauxRed = Color(0xFF823334)
val BordeauxRedLight = Color(0xFFA64445)
val BordeauxMuted = CapCutTextMuted
val BordeauxTextPrimary = CapCutTextPrimary
val BordeauxStatusOnline = CapCutGreen
val BordeauxStatusOffline = CapCutRed

