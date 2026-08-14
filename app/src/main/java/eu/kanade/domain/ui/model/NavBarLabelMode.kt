package eu.kanade.domain.ui.model

/**
 * How the bottom navigation bar shows tab labels.
 * - [HIDDEN]: icons only.
 * - [BESIDE]: label beside the icon, only on the selected tab (the pill style).
 * - [BELOW]: label under the icon on every tab (classic Material layout).
 */
enum class NavBarLabelMode {
    HIDDEN,
    BESIDE,
    BELOW,
}
