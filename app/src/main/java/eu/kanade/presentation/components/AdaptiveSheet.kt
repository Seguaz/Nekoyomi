package eu.kanade.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.DisposableEffectIgnoringConfiguration
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.ScreenTransition
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetImpl

@OptIn(InternalVoyagerApi::class)
@Composable
fun NavigatorAdaptiveSheet(
    screen: Screen,
    enableSwipeDismiss: (Navigator) -> Boolean = { true },
    onDismissRequest: () -> Unit,
) {
    Navigator(
        screen = screen,
        content = { sheetNavigator ->
            AdaptiveSheet(
                enableSwipeDismiss = enableSwipeDismiss(sheetNavigator),
                onDismissRequest = onDismissRequest,
            ) {
                ScreenTransition(
                    navigator = sheetNavigator,
                    transition = {
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                    },
                )

                BackHandler(
                    enabled = sheetNavigator.size > 1,
                    onBack = sheetNavigator::pop,
                )
            }

            // Make sure screens are disposed no matter what
            if (sheetNavigator.parent?.disposeBehavior?.disposeNestedNavigators == false) {
                DisposableEffectIgnoringConfiguration {
                    onDispose {
                        sheetNavigator.items
                            .asReversed()
                            .forEach(sheetNavigator::dispose)
                    }
                }
            }
        },
    )
}

/**
 * Sheet with adaptive position aligned to bottom on small screen, otherwise aligned to center
 * and will not be able to dismissed with swipe gesture.
 *
 * Max width of the content is set to 460 dp.
 */
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeDismiss: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isTabletUi = isTabletUi()
    // Sheet content must clear the system bars. A Compose Dialog's own window doesn't report
    // system-bar insets reliably, and WindowInsets.systemBars can read as 0 when an ancestor
    // Scaffold has consumed them (the tracking sheet). The raw Android root-window insets fix that,
    // but they can come back null/empty on some ROMs (Samsung), which would leave EVERY sheet cut
    // off. So take the larger of the two per edge — correct in consumed contexts and safe if the
    // root insets are unavailable.
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val composePadding = WindowInsets.systemBars.asPaddingValues()
    val rootInsets = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars())
    val sheetPadding = with(density) {
        PaddingValues(
            start = maxOf(composePadding.calculateStartPadding(layoutDirection), (rootInsets?.left ?: 0).toDp()),
            top = maxOf(composePadding.calculateTopPadding(), (rootInsets?.top ?: 0).toDp()),
            end = maxOf(composePadding.calculateEndPadding(layoutDirection), (rootInsets?.right ?: 0).toDp()),
            bottom = maxOf(composePadding.calculateBottomPadding(), (rootInsets?.bottom ?: 0).toDp()),
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = dialogProperties,
    ) {
        AdaptiveSheetImpl(
            modifier = modifier,
            isTabletUi = isTabletUi,
            enableSwipeDismiss = enableSwipeDismiss,
            onDismissRequest = onDismissRequest,
            sheetPadding = sheetPadding,
        ) {
            content()
        }
    }
}

private val dialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    // Draw edge-to-edge so Compose dispatches window insets to the dialog. Android 15+ forces
    // edge-to-edge and ignores decorFitsSystemWindows=true, which left navigationBarsPadding()
    // reading 0 inside the sheet and buttons (e.g. "Apply") hidden behind the system bars.
    decorFitsSystemWindows = false,
)
