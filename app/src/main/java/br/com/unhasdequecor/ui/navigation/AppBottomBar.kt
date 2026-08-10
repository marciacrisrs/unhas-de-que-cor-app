package br.com.unhasdequecor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Box {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            tonalElevation = 0.dp,
        ) {
            mainDestinations.forEachIndexed { index, destination ->
                if (index == 2) {
                    // Placeholder visual sob o FAB — oculto do TalkBack para evitar duplicata.
                    NavigationBarItem(
                        selected = currentRoute == Routes.CONTEXT,
                        onClick = { onNavigate(Routes.CONTEXT) },
                        icon = { Box(modifier = Modifier.size(24.dp)) },
                        label = {
                            Text(
                                text = "Escolher",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.clearAndSetSemantics { },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ),
                    )
                } else {
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onNavigate(destination.route) },
                        icon = {
                            Icon(
                                imageVector = when (destination.route) {
                                    Routes.HOME -> Icons.Outlined.Home
                                    Routes.HISTORY -> Icons.Outlined.History
                                    Routes.FAVORITES -> Icons.Outlined.FavoriteBorder
                                    else -> Icons.Outlined.Person
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription = destination.contentDescription
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onNavigate(Routes.CONTEXT) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .semantics { contentDescription = "Escolher minha cor" },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
        }
    }
}
