package dev.yowkey.portfolio.admin

import dev.yowkey.portfolio.data.HttpReply
import dev.yowkey.portfolio.data.HttpTransport

/**
 * An [HttpTransport] that can also send request headers, which the owner's routes need for their bearer token.
 * The same object serves the guest view's [dev.yowkey.portfolio.data.HttpSiteApi] (through the three-argument
 * [send], which sends no extra headers) and the owner's [AdminApi].
 */
interface AdminTransport : HttpTransport {
    /**
     * Sends [body] (JSON, when not null) with [headers] added, and returns whatever came back, whatever its status.
     * @throws dev.yowkey.portfolio.data.SiteApiException when nothing came back at all
     */
    suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply

    override suspend fun send(method: String, url: String, body: String?): HttpReply = send(method, url, body, emptyMap())
}
