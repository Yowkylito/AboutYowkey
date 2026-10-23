package dev.yowkey.portfolio.ui.theme

/*
 * Where the theme choice lives between visits. On the web it is the "theme" key in the browser's localStorage,
 * "light" or "dark", which index.html's head script reads before the static page's first paint; on the desktop
 * it is the user's Java preferences. Nothing saved means the visitor never switched, and the page is light.
 */

/** The theme chosen on an earlier visit: true for dark, false for light, null if the visitor never switched. */
expect fun savedDarkTheme(): Boolean?

/** Keeps the choice for the next visit. */
expect fun saveDarkTheme(dark: Boolean)
