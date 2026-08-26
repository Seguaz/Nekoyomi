package eu.kanade.domain.source.anime.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class ToggleAnimeIncognito(
    private val basePreferences: BasePreferences,
    private val preferences: SourcePreferences,
) {
    fun await(extensionPackage: String?, enable: Boolean) {
        if (enable) {
            // Turn incognito on for just this extension; leave the global switch as-is.
            extensionPackage?.let { pkg ->
                preferences.incognitoAnimeExtensions().getAndSet { it.plus(pkg) }
            }
        } else {
            // Turning it off from a source/extension must actually disable incognito there: clear the
            // per-extension flag AND the global switch. Otherwise an active global incognito stays
            // detected-but-stuck, since the effective state is (global OR per-extension).
            extensionPackage?.let { pkg ->
                preferences.incognitoAnimeExtensions().getAndSet { it.minus(pkg) }
            }
            if (basePreferences.incognitoMode().get()) {
                basePreferences.incognitoMode().set(false)
            }
        }
    }
}
