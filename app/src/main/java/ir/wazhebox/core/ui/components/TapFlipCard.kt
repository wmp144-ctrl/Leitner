package ir.wazhebox.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card whose two faces sit back to back in 3D: tapping flips it around the Y
 * axis so the answer lives on the reverse (PRD §12.2). The rotation is animated
 * to 90° with the front visible, then the back is drawn with the same rotation
 * mirrored, which keeps its text readable instead of mirrored.
 *
 * The card is a button from an accessibility standpoint; the flip itself is the
 * press feedback, so no ripple is drawn on the rotating surface.
 */
@Composable
fun TapFlipCard(
    flipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 280.dp,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "card-flip",
    )
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .graphicsLayer {
                rotationY = rotation
                // A shallow camera keeps the perspective subtle rather than cartoonish.
                cameraDistance = 16f * density.density
            }
            .clip(MaterialTheme.shapes.large)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onFlip,
            ),
    ) {
        if (rotation <= 90f) {
            front()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f },
            ) {
                back()
            }
        }
    }
}
