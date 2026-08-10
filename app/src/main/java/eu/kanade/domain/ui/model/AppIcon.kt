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
    Invested("InvestedIcon", AYMR.strings.app_icon_invested, R.mipmap.ic_launcher_invested),
    Nyaa("NyaaIcon", AYMR.strings.app_icon_nyaa, R.mipmap.ic_launcher_nyaa),
    Radiactive("RadiactiveIcon", AYMR.strings.app_icon_radiactive, R.mipmap.ic_launcher_radiactive),
    ByeKitty("ByeKittyIcon", AYMR.strings.app_icon_bye_kitty, R.mipmap.ic_launcher_bye_kitty),
    BarryAllen("BarryAllenIcon", AYMR.strings.app_icon_barry_allen, R.mipmap.ic_launcher_barry_allen),
    StylishPaws("StylishPawsIcon", AYMR.strings.app_icon_stylish_paws, R.mipmap.ic_launcher_stylish_paws),
    Chilling("ChillingIcon", AYMR.strings.app_icon_chilling, R.mipmap.ic_launcher_chilling),
    Happy("HappyIcon", AYMR.strings.app_icon_happy, R.mipmap.ic_launcher_happy),
    Stretch("StretchIcon", AYMR.strings.app_icon_stretch, R.mipmap.ic_launcher_stretch),
}
