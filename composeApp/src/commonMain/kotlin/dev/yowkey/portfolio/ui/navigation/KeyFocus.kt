package dev.yowkey.portfolio.ui.navigation

/**
 * Called once the page has taken the focus back from a text field without a click (Escape in a form). On the web
 * the field typed through a hidden text area, and when that lets go the browser's focus falls to the page's body,
 * where no key reaches the app; this hands it back to the app's canvas. Elsewhere the window keeps it: nothing to do.
 */
internal expect fun returnKeysToPage()
