package eu.kanade.tachiyomi.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import eu.kanade.domain.ui.model.AppIcon

object AppIconManager {

    // The <activity-alias> entries live in MainActivity's package.
    private val aliasPackage = MainActivity::class.java.name.substringBeforeLast('.')

    /**
     * Enables the alias for [appIcon] and disables the others, switching the launcher icon.
     * The selected alias is enabled first so the app always keeps a launcher entry.
     */
    fun apply(context: Context, appIcon: AppIcon) {
        val packageManager = context.packageManager
        setEnabled(packageManager, context, appIcon, enabled = true)
        AppIcon.entries
            .filter { it != appIcon }
            .forEach { setEnabled(packageManager, context, it, enabled = false) }
    }

    private fun setEnabled(
        packageManager: PackageManager,
        context: Context,
        appIcon: AppIcon,
        enabled: Boolean,
    ) {
        val component = ComponentName(context.packageName, "$aliasPackage.${appIcon.aliasName}")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
