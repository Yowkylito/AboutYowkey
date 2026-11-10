package dev.yowkey.portfolio.ui

/**
 * Tells the page which [section] is being read (its name in lower case, as the static page's ids). In the browser
 * the page's own script counts how far readers get (index.template.html, `window.yowkeyReading`), and the app,
 * which draws on a canvas the script cannot see into, reports to it. Elsewhere nothing is counted.
 */
internal expect fun reportReading(section: String)
