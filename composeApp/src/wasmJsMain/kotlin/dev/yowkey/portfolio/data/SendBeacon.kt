@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.data

/**
 * [Beacon] over `navigator.sendBeacon`, for the clicks on LinkedIn and GitHub: the browser delivers it even if the
 * tab goes away the moment after, and a string body goes as `text/plain`, a simple request with no CORS preflight to
 * wait for. A browser without it, or one that refuses the beacon, answers false, and the click goes by `fetch`.
 */
class SendBeacon : Beacon {
    override fun send(url: String, body: String): Boolean = sendBeacon(url, body)
}

private fun sendBeacon(url: String, body: String): Boolean = js(
    """(function () {
        try {
            return typeof navigator.sendBeacon === 'function' && navigator.sendBeacon(url, body) === true;
        } catch (e) {
            return false;
        }
    })()""",
)
