package tachiyomi.presentation.core.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val sheetAnimationSpec = tween<Float>(durationMillis = 350)

/**
 * Sheet with adaptive position: a Material 3 [ModalBottomSheet] anchored to the bottom on phones,
 * or a centered dialog on tablets (where it can't be dismissed with a swipe gesture).
 *
 * On phones the bottom sheet handles window insets natively, so its content always clears the
 * system bars without any manual padding. Tablets still render inside a Compose [Dialog], whose
 * window doesn't report insets reliably (Google issue 246909281), so the host insets are captured
 * by the caller and passed in as [tabletSheetPadding].
 *
 * Max width of the content is set to 460 dp (600 dp in landscape).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // Only used by the tablet (centered dialog) variant; the phone bottom sheet handles its own
    // insets, so this is ignored there.
    tabletSheetPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    val maxWidth = if (LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE) {
        600.dp
    } else {
        460.dp
    }

    if (isTabletUi) {
        val scope = rememberCoroutineScope()
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = TabletDialogProperties,
        ) {
            var targetAlpha by remember { mutableFloatStateOf(0f) }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = sheetAnimationSpec,
                label = "alpha",
            )
            val internalOnDismissRequest: () -> Unit = {
                scope.launch {
                    targetAlpha = 0f
                    onDismissRequest()
                }
            }
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = internalOnDismissRequest,
                    )
                    .fillMaxSize()
                    .alpha(alpha),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .requiredWidthIn(max = maxWidth)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = {},
                        )
                        .padding(tabletSheetPadding)
                        .padding(vertical = 16.dp)
                        .then(modifier),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = {
                        BackHandler(enabled = alpha > 0f, onBack = internalOnDismissRequest)
                        content()
                    },
                )

                LaunchedEffect(Unit) {
                    targetAlpha = 1f
                }
            }
        }
    } else {
        // enableSwipeDismiss can change while the sheet is open (e.g. the tracking sheet disables it
        // when you drill into a sub-screen). rememberModalBottomSheetState only captures the
        // confirmValueChange lambda once, so read the latest value through a stable state holder.
        val swipeDismissEnabled = rememberUpdatedState(enableSwipeDismiss)
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            // Block the swipe/tap-outside dismissal when swipe-dismiss is off; programmatic hide
            // (our own onDismissRequest) still goes through, so the sheet can always be closed.
            confirmValueChange = { newValue ->
                swipeDismissEnabled.value || newValue != SheetValue.Hidden
            },
        )
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = maxWidth,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            content()
        }
    }
}

private val TabletDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    // Draw edge-to-edge so Compose dispatches window insets to the dialog. Android 15+ forces
    // edge-to-edge and ignores decorFitsSystemWindows=true.
    decorFitsSystemWindows = false,
)
