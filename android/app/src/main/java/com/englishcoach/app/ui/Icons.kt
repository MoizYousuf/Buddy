package com.englishcoach.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons drawn from 24x24 SVG paths, so they look the same on every phone (no font glyphs). */
object Icons {
    const val HOME = "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"
    const val PROGRESS = "M4 20V10M10 20V4M16 20v-7M2 20h20"
    const val SETTINGS = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
    const val MIC = "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM19 11a7 7 0 0 1-14 0M12 18v3"
    const val WALLET = "M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM3 7l12-4 2 4M16 13.5h.01"
    const val SHOP = "M5 8h14l-1 12H6zM9 8V6a3 3 0 0 1 6 0v2"
    const val MORE = "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"
    const val MAC = "M3 5h18v11H3zM8 20h8M12 16v4"
    const val LOCK = "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4"
    const val CHECK = "M5 12l5 5L20 7"
    const val SCREEN = "M3 4h18v12H3zM7 20h10M9 16l-1 4M15 16l1 4"
    const val SPARK = "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6"
    const val KEYBOARD = "M2 6h20v12H2zM6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10"
    const val POINTER = "M5 3l14 7-6 2-2 6z"
    const val TRACKPAD = "M4 4h16v16H4zM4 15h16M12 15v5"
    const val EXPAND = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
    const val COLLAPSE = "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"
    const val BROOM = "M14 3l-4 9M7 12h9l2 9H5zM9 16v5M13 16v5"
    const val GRID = "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"
    const val CHEVRON_DOWN = "M6 9l6 6 6-6"
    const val ARROW = "M5 12h14M13 6l6 6-6 6"
    const val BACK = "M15 18l-6-6 6-6"
    const val PLAY = "M8 5v14l11-7z"
}

@Composable
fun LineIcon(path: String, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, strokeWidth: Float = 2f, filled: Boolean = false) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (filled) drawPath(p, color)
            else drawPath(p, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
