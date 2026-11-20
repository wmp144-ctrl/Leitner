package ir.wazhebox.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.wazhebox.R
import ir.wazhebox.core.ui.theme.Spacing

/** One row of the slide-out menu. */
data class DrawerItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The app menu that replaced the bottom bar: it slides in from the right (the
 * start edge in RTL) so the thumb reaches the same corner it always used, but
 * every screen keeps its full height. Primary destinations sit above a
 * divider, one-off actions below it.
 */
@Composable
fun WazheBoxDrawer(
    items: List<DrawerItem>,
    actions: List<DrawerItem>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ModalDrawerSheet(
        modifier = modifier.width(304.dp),
        drawerContainerColor = colors.surface,
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.drawer_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        items.forEach { item ->
            DrawerRow(item)
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            color = colors.outlineVariant,
        )

        actions.forEach { item ->
            DrawerRow(item)
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.drawer_offline),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun DrawerRow(item: DrawerItem) {
    NavigationDrawerItem(
        label = { Text(text = item.label, style = MaterialTheme.typography.titleMedium) },
        icon = {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        },
        selected = item.selected,
        onClick = item.onClick,
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}
