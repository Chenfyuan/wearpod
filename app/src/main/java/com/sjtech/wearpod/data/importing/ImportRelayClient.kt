package com.sjtech.wearpod.data.importing

import com.sjtech.wearpod.data.model.PhoneImportSession
import com.sjtech.wearpod.data.model.PhoneImportSessionSnapshot
import com.sjtech.wearpod.data.model.PhoneImportSessionStatus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
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
        val candidates = buildList {
            add(baseUrl.trim())
            fallbackBaseUrl.trim()
                .takeIf { it.isNotBlank() && it != baseUrl.trim() }
                ?.let(::add)
        }
        var lastError: Throwable? = null
        candidates.forEachIndexed { index, requestBaseUrl ->
            try {
                return block(requestBaseUrl)
            } catch (throwable: Throwable) {
                lastError = throwable
                if (index == candidates.lastIndex) throw throwable
            }
        }
        throw checkNotNull(lastError)
    }
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
