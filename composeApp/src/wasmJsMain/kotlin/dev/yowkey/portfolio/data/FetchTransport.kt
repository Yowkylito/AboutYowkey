@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.data

import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * [HttpTransport] over the browser's `fetch`. No cookies or credentials go along; the worker wants none. The
 * promise never rejects: a failure to connect comes back as status 0, so no JS exception has to cross into Wasm.
 */
class FetchTransport : HttpTransport {
    override suspend fun send(method: String, url: String, body: String?): HttpReply {
        val raw = fetchText(method, url, body ?: "").await<JsString>().toString()
        val status = raw.substringBefore('\n').toIntOrNull() ?: 0
        val text = raw.substringAfter('\n')
        if (status == 0) throw SiteApiException("No connection: $text")
        return HttpReply(status, text)
    }
}

/** "<status>\n<body>", or "0\n<reason>" when nothing came back: one string, so nothing needs converting. */
private fun fetchText(method: String, url: String, body: String): Promise<JsString> = js(
    """(function () {
        var init = { method: method, credentials: 'omit', cache: 'no-store' };
        if (body !== '') { init.headers = { 'Content-Type': 'application/json' }; init.body = body; }
        return fetch(url, init)
            .then(function (r) { return r.text().then(function (t) { return r.status + '\n' + t; }); })
            .catch(function (e) { return '0\n' + String(e); });
    })()""",
)
