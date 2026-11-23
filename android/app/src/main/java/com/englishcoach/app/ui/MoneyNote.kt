package com.englishcoach.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Colours of each Pakistani note (light, main, dark ink). A drawn look-alike for the app, marked نمونہ (specimen). */
private data class NoteInk(val light: Color, val main: Color, val dark: Color, val urdu: String, val words: String)

private fun ink(v: Int) = when (v) {
    5000 -> NoteInk(Color(0xFFF1DC93), Color(0xFFCFA94A), Color(0xFF6B4E14), "پانچ ہزار روپے", "FIVE THOUSAND RUPEES")
    1000 -> NoteInk(Color(0xFFC9DDF2), Color(0xFF5E86C2), Color(0xFF1D3866), "ایک ہزار روپے", "ONE THOUSAND RUPEES")
    500 -> NoteInk(Color(0xFFD3EBC8), Color(0xFF5FA35A), Color(0xFF1F4D22), "پانچ سو روپے", "FIVE HUNDRED RUPEES")
    100 -> NoteInk(Color(0xFFF5CFC6), Color(0xFFC9544A), Color(0xFF6A1A14), "ایک سو روپے", "ONE HUNDRED RUPEES")
    50 -> NoteInk(Color(0xFFE6D3EE), Color(0xFF9A6BB3), Color(0xFF462260), "پچاس روپے", "FIFTY RUPEES")
    20 -> NoteInk(Color(0xFFF6DDC3), Color(0xFFDB8A3E), Color(0xFF6E3A0E), "بیس روپے", "TWENTY RUPEES")
    else -> NoteInk(Color(0xFFDDEFCB), Color(0xFF7FB55E), Color(0xFF2E5B20), "دس روپے", "TEN RUPEES")
}

private fun DrawScope.text(t: String, x: Float, y: Float, size: Float, c: Color, bold: Boolean = true, alignRight: Boolean = false,
                           serif: Boolean = false) {
    val p = Paint().apply {
        isAntiAlias = true; color = c.toArgb(); textSize = size
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(t, x, y, p)
}

/** Spirograph rosette like the ones printed on notes. */
private fun DrawScope.rosette(c: Offset, r: Float, col: Color, petals: Int = 14) {
    val path = Path()
    val n = 360
    for (i in 0..n) {
        val t = i / n.toFloat() * 2 * PI.toFloat()
        val rr = r * (0.72f + 0.28f * cos(petals * t))
        val p = Offset(c.x + rr * cos(t), c.y + rr * sin(t))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, col, style = Stroke(1.4f))
    drawCircle(col, r * .38f, c, style = Stroke(1.2f))
    for (k in 0 until petals) rotate(k * 360f / petals, c) { drawLine(col.copy(alpha = col.alpha * .6f), Offset(c.x, c.y - r * .38f), Offset(c.x, c.y - r * .7f), 1f) }
}

/** Fine wavy lines across the note. */
private fun DrawScope.guilloche(col: Color, rows: Int, amp: Float) {
    for (k in 0 until rows) {
        val y0 = size.height * (k + .5f) / rows
        val path = Path(); var x = 0f
        path.moveTo(0f, y0)
        while (x <= size.width) { path.lineTo(x, y0 + sin(x / (size.width / 9f) * PI.toFloat() + k * .7f) * amp); x += 5f }
        drawPath(path, col, style = Stroke(.9f))
    }
}

private fun DrawScope.crescent(c: Offset, r: Float, col: Color, bg: Color) {
    drawCircle(col, r, c)
    drawCircle(bg, r * .82f, Offset(c.x + r * .35f, c.y - r * .1f))
    val star = Path()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5; val rr = if (i % 2 == 0) r * .38f else r * .16f
        val p = Offset(c.x + r * .62f + rr * cos(a), c.y - r * .25f + rr * sin(a))
        if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close(); drawPath(star, col)
}

/** Portrait panel: a gentleman in a karakul cap and sherwani, drawn as an engraving silhouette (no real face). */
private fun DrawScope.portrait(cx: Float, top: Float, h: Float, ink: Color) {
    val w = h * .78f
    val oval = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - w / 2, top, cx + w / 2, top + h)) }
    clipPath(oval) {
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .45f), Color.Transparent), Offset(cx, top + h * .4f), h * .7f))
        // engraving lines
        var y = top
        while (y < top + h) { drawLine(ink.copy(alpha = .10f), Offset(cx - w, y), Offset(cx + w, y), .8f); y += 3f }
        val head = Offset(cx, top + h * .42f)
        // shoulders / sherwani
        val body = Path().apply {
            moveTo(cx - w * .62f, top + h); cubicTo(cx - w * .55f, top + h * .7f, cx - w * .25f, top + h * .66f, cx - w * .12f, top + h * .64f)
            lineTo(cx + w * .12f, top + h * .64f); cubicTo(cx + w * .25f, top + h * .66f, cx + w * .55f, top + h * .7f, cx + w * .62f, top + h); close()
        }
        drawPath(body, Brush.verticalGradient(listOf(ink.copy(alpha = .75f), ink), top + h * .64f, top + h))
        drawLine(Color.White.copy(alpha = .25f), Offset(cx, top + h * .66f), Offset(cx, top + h), 1.5f)   // sherwani buttons line
        for (i in 0 until 4) drawCircle(Color.White.copy(alpha = .35f), 1.6f, Offset(cx + 3f, top + h * (.72f + i * .07f)))
        // neck + face
        drawRect(ink.copy(alpha = .7f), Offset(cx - w * .07f, top + h * .55f), Size(w * .14f, h * .12f))
        drawOval(Brush.radialGradient(listOf(ink.copy(alpha = .45f), ink.copy(alpha = .85f)), Offset(head.x - w * .05f, head.y - h * .03f), w * .3f),
            Offset(head.x - w * .17f, head.y - h * .15f), Size(w * .34f, h * .3f))
        // karakul cap
        val cap = Path().apply {
            moveTo(head.x - w * .2f, head.y - h * .1f); lineTo(head.x - w * .17f, head.y - h * .26f)
            quadraticTo(head.x, head.y - h * .31f, head.x + w * .17f, head.y - h * .26f); lineTo(head.x + w * .2f, head.y - h * .1f); close()
        }
        drawPath(cap, ink)
        for (i in 0 until 6) drawLine(Color.White.copy(alpha = .15f), Offset(head.x - w * .16f + i * w * .064f, head.y - h * .26f),
            Offset(head.x - w * .18f + i * w * .07f, head.y - h * .11f), 1f)
    }
    drawPath(oval, ink.copy(alpha = .6f), style = Stroke(2f))
    drawOval(ink.copy(alpha = .3f), Offset(cx - w / 2 - 5f, top - 5f), Size(w + 10f, h + 10f), style = Stroke(1f))
}

/** Faisal-Mosque-like building (tent roof + 4 minarets) for the back. */
private fun DrawScope.mosque(cx: Float, base: Float, h: Float, ink: Color) {
    val w = h * 2.2f
    val roof = Path().apply { moveTo(cx - w * .28f, base); lineTo(cx, base - h * .62f); lineTo(cx + w * .28f, base); close() }
    drawPath(roof, Brush.verticalGradient(listOf(ink.copy(alpha = .55f), ink), base - h * .62f, base))
    drawLine(Color.White.copy(alpha = .3f), Offset(cx, base - h * .6f), Offset(cx, base), 1.2f)
    listOf(-.45f, -.38f, .38f, .45f).forEach { f ->
        val x = cx + w * f
        drawRect(ink, Offset(x - 2.5f, base - h), Size(5f, h))
        val tip = Path().apply { moveTo(x - 3f, base - h); lineTo(x, base - h * 1.12f); lineTo(x + 3f, base - h); close() }
        drawPath(tip, ink)
    }
    drawRect(ink.copy(alpha = .8f), Offset(cx - w * .5f, base), Size(w, 3f))
}

/**
 * A Pakistani rupee note drawn on screen (front: portrait right, value, Urdu words, security thread;
 * back: the mosque). `shine` 0..1 moves a light reflection across it (follows the 3D turn).
 */
@Composable
fun PkrNote(value: Int, modifier: Modifier, back: Boolean = false, shine: Float = -1f) {
    val k = ink(value)
    Canvas(modifier) {
        val W = size.width; val H = size.height
        val r = CornerRadius(H * .05f)
        // paper
        drawRoundRect(Brush.linearGradient(listOf(k.light, k.main, k.light, k.main), Offset.Zero, Offset(W, H)), cornerRadius = r)
        drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent), Offset(W * (if (back) .7f else .3f), H * .5f), H), cornerRadius = r)
        guilloche(k.dark.copy(alpha = .13f), 16, H * .025f)
        val m = H * .055f
        // border frame with pattern
        drawRoundRect(k.dark.copy(alpha = .55f), Offset(m, m), Size(W - 2 * m, H - 2 * m), CornerRadius(H * .03f), style = Stroke(H * .012f))
        drawRoundRect(k.dark.copy(alpha = .25f), Offset(m * 1.6f, m * 1.6f), Size(W - 3.2f * m, H - 3.2f * m), CornerRadius(H * .02f), style = Stroke(1f))
        var bx = m * 2
        while (bx < W - m * 2) { drawCircle(k.dark.copy(alpha = .18f), H * .008f, Offset(bx, m * 1.3f)); drawCircle(k.dark.copy(alpha = .18f), H * .008f, Offset(bx, H - m * 1.3f)); bx += H * .035f }

        if (!back) {
            rosette(Offset(W * .2f, H * .48f), H * .3f, k.dark.copy(alpha = .35f))
            // watermark window (light area, left)
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent), Offset(W * .2f, H * .48f), H * .28f),
                Offset(W * .2f - H * .24f, H * .48f - H * .26f), Size(H * .48f, H * .52f))
            crescent(Offset(W * .2f, H * .46f), H * .1f, k.dark.copy(alpha = .55f), Color(0xFFF8F0D8))
            // security thread
            var ty = m * 1.6f
            while (ty < H - m * 1.6f) { drawRect(Color(0xFFDADFE6), Offset(W * .41f, ty), Size(H * .022f, H * .05f)); ty += H * .085f }
            drawLine(Color(0x55FFFFFF), Offset(W * .41f + H * .011f, m * 1.6f), Offset(W * .41f + H * .011f, H - m * 1.6f), .8f)
            // texts
            text("$value", W * .46f, H * .42f, H * .26f, k.dark, serif = true)
            text(k.urdu, W * .47f, H * .6f, H * .1f, k.dark)
            text(k.words, W * .47f, H * .72f, H * .055f, k.dark.copy(alpha = .8f))
            text("پاکستان", W * .47f, H * .18f, H * .075f, k.dark.copy(alpha = .85f))
            text("$value", W - m * 2.2f, m * 2 + H * .08f, H * .09f, k.dark, alignRight = true)
            text("$value", m * 2.2f, H - m * 2.2f, H * .09f, k.dark)
            portrait(W * .82f, H * .17f, H * .62f, k.dark)
            text("نمونہ", W * .82f, H - m * 2.1f, H * .055f, k.dark.copy(alpha = .55f))
        } else {
            rosette(Offset(W * .8f, H * .45f), H * .27f, k.dark.copy(alpha = .3f))
            mosque(W * .36f, H * .74f, H * .4f, k.dark)
            text("$value", W * .66f, H * .38f, H * .19f, k.dark, serif = true)
            text("RUPEES", W * .67f, H * .5f, H * .065f, k.dark.copy(alpha = .8f))
            text(k.urdu, W * .66f, H * .66f, H * .08f, k.dark)
            text("$value", m * 2.2f, m * 2 + H * .08f, H * .09f, k.dark)
            text("$value", W - m * 2.2f, H - m * 2.2f, H * .09f, k.dark, alignRight = true)
        }
        // light reflection band
        if (shine in 0f..1f) {
            val x = -W * .4f + shine * W * 1.8f
            drawRoundRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent),
                Offset(x - W * .15f, 0f), Offset(x + W * .15f, H)), cornerRadius = r)
        }
        drawRoundRect(Color.Black.copy(alpha = .15f), cornerRadius = r, style = Stroke(1.2f))
    }
}
