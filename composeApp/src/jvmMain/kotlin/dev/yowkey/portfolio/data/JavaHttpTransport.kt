package dev.yowkey.portfolio.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** [HttpTransport] over `java.net.http`, for the desktop window. */
class JavaHttpTransport : HttpTransport {

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override suspend fun send(method: String, url: String, body: String?): HttpReply = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("User-Agent", "AboutYowkey-desktop")
            .apply {
                if (body == null) {
                    method(method, HttpRequest.BodyPublishers.noBody())
                } else {
                    header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body))
                }
            }
            .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            HttpReply(response.statusCode(), response.body())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SiteApiException("No connection: ${e.message}", e)
        }
    }
}
