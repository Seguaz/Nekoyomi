package eu.kanade.domain.ui.model

import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.aniyomi.AYMR

/**
 * A launcher icon the user can pick. Each entry maps to an <activity-alias> in the manifest; exactly
 * one alias is enabled at a time (see [eu.kanade.tachiyomi.ui.main.AppIconManager]).
 */
enum class AppIcon(
    val aliasName: String,
    val titleRes: StringResource,
    @DrawableRes val iconRes: Int,
) {
    Default("DefaultIcon", AYMR.strings.app_icon_default, R.mipmap.ic_launcher),
    Grayscale("GrayscaleIcon", AYMR.strings.app_icon_grayscale, R.mipmap.ic_launcher_gray),
}
