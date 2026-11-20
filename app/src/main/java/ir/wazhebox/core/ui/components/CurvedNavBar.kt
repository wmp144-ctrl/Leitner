package ir.wazhebox.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One destination of [CurvedNavBar]. */
data class NavBarTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * A floating, slightly translucent navigation bar whose top edge dips into a
 * smooth notch under the selected tab. The notch slides between tabs with a
 * 320ms ease, and the selected tab gets a filled chip inside the dip.
 *
 * The surface is drawn with a `Path` instead of a Material `NavigationBar` so
 * the curve belongs to the bar itself rather than to a decoration on top of it;
 * content scrolling underneath stays faintly visible through the 92% fill.
 */
@Composable
fun CurvedNavBar(
    tabs: List<NavBarTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val notchDepth = 18.dp
    val barHeight = 68.dp
    val totalHeight = barHeight + notchDepth
    val corner = 26.dp
    val notchHalfWidth = 40.dp

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "nav-notch",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        val surfaceColor = colors.surface.copy(alpha = 0.92f)
        val borderColor = colors.outline.copy(alpha = 0.14f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val depth = with(density) { notchDepth.toPx() }
            val radius = with(density) { corner.toPx() }
            val notchHalf = with(density) { notchHalfWidth.toPx() }
            val width = size.width
            val height = size.height
            val cell = width / tabs.size
            val cellCenter = (animatedIndex + 0.5f) * cell
            val center = if (layoutDirection == LayoutDirection.Rtl) width - cellCenter else cellCenter

            val left = (center - notchHalf).coerceIn(radius, width - radius)
            val right = (center + notchHalf).coerceIn(radius, width - radius)
            val mid = (left + right) / 2f

            val path = Path().apply {
                moveTo(radius, depth)
                lineTo(left, depth)
                // Dip down to the notch bottom and back up, all tangents
                // horizontal so the curve joins the straight edge smoothly.
                cubicTo(left + (mid - left) * 0.5f, depth, left + (mid - left) * 0.5f, depth * 2f, mid, depth * 2f)
                cubicTo(right - (right - mid) * 0.5f, depth * 2f, right - (right - mid) * 0.5f, depth, right, depth)
                lineTo(width - radius, depth)
                arcTo(
                    rect = Rect(width - radius * 2f, depth, width, depth + radius * 2f),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(width, height - radius)
                arcTo(
                    rect = Rect(width - radius * 2f, height - radius * 2f, width, height),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(radius, height)
                arcTo(
                    rect = Rect(0f, height - radius * 2f, radius * 2f, height),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(0f, depth + radius)
                arcTo(
                    rect = Rect(0f, depth, radius * 2f, depth + radius * 2f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                close()
            }
            drawPath(path, surfaceColor)
            drawPath(path, borderColor, style = Stroke(width = with(density) { 1.dp.toPx() }))
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = notchDepth),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primaryContainer else colors.surface.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
