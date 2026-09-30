package dev.handspell.app.ui.home

import androidx.compose.ui.geometry.Offset
import dev.handspell.app.core.model.CanonicalHandshape

/** Share of each letter's time spent holding its pose before it turns into the next one. */
internal const val HOLD_FRACTION = 0.55f

/** 0 while a pose is held, then an eased 0 to 1 as it turns into the next pose. */
internal fun morphProgress(fraction: Float): Float {
    val t = ((fraction - HOLD_FRACTION) / (1f - HOLD_FRACTION)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The 21 landmarks fitted into the unit square with the hand's proportions kept, so two poses can be blended. */
internal fun fitToUnitSquare(shape: CanonicalHandshape): List<Offset> {
    val points = shape.landmarks.map { Offset(it.x, it.y) }
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val range = maxOf(maxX - minX, maxY - minY).coerceAtLeast(0.0001f)
    val padX = (1f - (maxX - minX) / range) / 2f
    val padY = (1f - (maxY - minY) / range) / 2f
    return points.map { Offset((it.x - minX) / range + padX, (it.y - minY) / range + padY) }
}

/** Point by point blend of two poses: [t] 0 is [from], 1 is [to]. */
internal fun blend(from: List<Offset>, to: List<Offset>, t: Float): List<Offset> =
    from.indices.map { i -> Offset(from[i].x + (to[i].x - from[i].x) * t, from[i].y + (to[i].y - from[i].y) * t) }
