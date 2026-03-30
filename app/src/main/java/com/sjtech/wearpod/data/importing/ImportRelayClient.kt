package com.sjtech.wearpod.data.importing

import com.sjtech.wearpod.data.model.PhoneImportSession
import com.sjtech.wearpod.data.model.PhoneExportSession
import com.sjtech.wearpod.data.model.PhoneImportSessionSnapshot
import com.sjtech.wearpod.data.model.PhoneImportSessionStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ImportRelayClient(
    private val baseUrl: String,
    private val fallbackBaseUrl: String = "",
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    suspend fun createSession(): PhoneImportSession = withContext(Dispatchers.IO) {
        ensureConfigured()
        requestWithFallback { requestBaseUrl ->
            val connection = openConnection(requestBaseUrl, "/api/sessions", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Length", "0")
            }
            val payload = connection.readJsonObject()
            PhoneImportSession(
                sessionId = payload.getString("sessionId"),
                shortCode = payload.getString("shortCode"),
                mobileUrl = canonicalMobileUrl(payload.getString("mobileUrl")),
                expiresAtEpochMillis = payload.getLong("expiresAtEpochMillis"),
            )
        }
    }

    suspend fun createExportSession(
        opmlContent: String,
        outlineCount: Int,
    ): PhoneExportSession = withContext(Dispatchers.IO) {
        ensureConfigured()
        requestWithFallback { requestBaseUrl ->
            val payload = JSONObject()
                .put("opmlContent", opmlContent)
                .put("outlineCount", outlineCount)
            val connection = openConnection(requestBaseUrl, "/api/export-sessions", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val response = connection.readJsonObject()
            PhoneExportSession(
                sessionId = response.getString("sessionId"),
                shortCode = response.getString("shortCode"),
                mobileUrl = canonicalMobileUrl(response.getString("mobileUrl")),
                expiresAtEpochMillis = response.getLong("expiresAtEpochMillis"),
                outlineCount = response.optInt("outlineCount", outlineCount),
            )
        }
    }

    suspend fun fetchSession(sessionId: String): PhoneImportSessionSnapshot = withContext(Dispatchers.IO) {
        ensureConfigured()
        requestWithFallback { requestBaseUrl ->
            val payload = openConnection(requestBaseUrl, "/api/sessions/$sessionId").readJsonObject()
            PhoneImportSessionSnapshot(
                sessionId = payload.getString("sessionId"),
                shortCode = payload.getString("shortCode"),
                mobileUrl = canonicalMobileUrl(payload.getString("mobileUrl")),
                expiresAtEpochMillis = payload.getLong("expiresAtEpochMillis"),
                status = PhoneImportSessionStatus.valueOf(payload.getString("status")),
                feedUrls = payload.optJSONArray("feedUrls").toStringList(),
                invalidCount = payload.optInt("invalidCount", 0),
                duplicateCountWithinPayload = payload.optInt("duplicateCountWithinPayload", 0),
            )
        }
    }

    private fun ensureConfigured() {
        check(isConfigured()) {
            "手机导入服务未配置"
        }
    }

    private fun openConnection(requestBaseUrl: String, path: String, method: String = "GET"): HttpURLConnection {
        val separator = if (path.startsWith("/")) "" else "/"
        return (URL("${requestBaseUrl.trimEnd('/')}$separator$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = method
            setRequestProperty("User-Agent", "WearPod/0.1 (Wear OS phone import)")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun canonicalMobileUrl(rawUrl: String): String {
        val preferredBaseUrl = baseUrl.trim()
        if (preferredBaseUrl.isBlank()) return rawUrl
        return runCatching {
            val rawUri = URI(rawUrl)
            val preferredUri = URI(preferredBaseUrl)
            URI(
                preferredUri.scheme,
                preferredUri.userInfo,
                preferredUri.host,
                preferredUri.port,
                rawUri.rawPath,
                rawUri.rawQuery,
                null,
            ).toString()
        }.getOrDefault(rawUrl)
    }

    private fun <T> requestWithFallback(block: (requestBaseUrl: String) -> T): T {
        val primaryBaseUrl = baseUrl.trim()
        val secondaryBaseUrl = fallbackBaseUrl.trim()
            .takeIf { it.isNotBlank() && it != primaryBaseUrl }

        var primaryError: Throwable? = null
        repeat(PRIMARY_RETRY_COUNT) { attempt ->
            try {
                return block(primaryBaseUrl)
            } catch (throwable: Throwable) {
                primaryError = throwable
                val hasNextPrimaryAttempt = attempt < PRIMARY_RETRY_COUNT - 1
                if (!throwable.isRetryableNetworkFailure() || !hasNextPrimaryAttempt) return@repeat
                Thread.sleep(PRIMARY_RETRY_DELAY_MILLIS)
            }
        }

        val terminalPrimaryError = checkNotNull(primaryError)
        if (!terminalPrimaryError.isRetryableNetworkFailure() || secondaryBaseUrl == null) {
            throw terminalPrimaryError
        }

        try {
            return block(secondaryBaseUrl)
        } catch (fallbackError: Throwable) {
            fallbackError.addSuppressed(terminalPrimaryError)
            throw fallbackError
        }
    }
}

private const val PRIMARY_RETRY_COUNT = 2
private const val PRIMARY_RETRY_DELAY_MILLIS = 400L

private fun Throwable.isRetryableNetworkFailure(): Boolean = when (this) {
    is ConnectException,
    is SocketException,
    is SocketTimeoutException,
    is UnknownHostException,
    is SSLException,
    is IOException -> true
    else -> cause?.isRetryableNetworkFailure() == true
}

private fun HttpURLConnection.readJsonObject(): JSONObject {
    val responseStream = try {
        val code = responseCode
        if (code !in 200..299) {
            val errorBody = errorStream?.bufferedReader()?.use(BufferedReader::readText)
            disconnect()
            error(errorBody?.takeIf(String::isNotBlank) ?: "请求失败，HTTP $code")
        }
        inputStream
    } catch (throwable: Throwable) {
        disconnect()
        throw throwable
    }

    return responseStream.bufferedReader().use { reader ->
        JSONObject(reader.readText())
    }.also {
        disconnect()
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            add(optString(index))
        }
    }.filter(String::isNotBlank)
}
