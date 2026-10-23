package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.admin.AdminTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * [HttpTransport] (and [AdminTransport], for the owner's bearer token) over the platform's `HttpURLConnection`, on
 * the IO pool. No cookies, no cache. Nothing it sends or receives is logged.
 *
 * @param userAgent what the worker sees; it must not look like a bot's (no URL, no "java/"), or visits do not count
 * @param rewriteUrl lets a debug build point the app at a local stand-in for the worker; release builds pass none
 */
class UrlConnectionTransport(
    private val userAgent: String = "AboutYowkey-android",
    private val rewriteUrl: (String) -> String = { it },
) : AdminTransport {

    override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(rewriteUrl(url)).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", userAgent)
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                }
                if (body != null) {
                    val bytes = body.encodeToByteArray()
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
                HttpReply(status, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The exception's own text can name the host, never the token, which only travels in a header.
                throw SiteApiException("No connection: ${e.javaClass.simpleName}", e)
            } finally {
                connection?.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
