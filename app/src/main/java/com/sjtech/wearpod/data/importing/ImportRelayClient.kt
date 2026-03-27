package com.sjtech.wearpod.data.importing

import com.sjtech.wearpod.data.model.PhoneImportSession
import com.sjtech.wearpod.data.model.PhoneImportSessionSnapshot
import com.sjtech.wearpod.data.model.PhoneImportSessionStatus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ImportRelayClient(
    private val baseUrl: String,
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    suspend fun createSession(): PhoneImportSession = withContext(Dispatchers.IO) {
        ensureConfigured()
        val connection = openConnection("/api/sessions", method = "POST").apply {
            doOutput = true
            setRequestProperty("Content-Length", "0")
        }
        val payload = connection.readJsonObject()
        PhoneImportSession(
            sessionId = payload.getString("sessionId"),
            shortCode = payload.getString("shortCode"),
            mobileUrl = payload.getString("mobileUrl"),
            expiresAtEpochMillis = payload.getLong("expiresAtEpochMillis"),
        )
    }

    suspend fun fetchSession(sessionId: String): PhoneImportSessionSnapshot = withContext(Dispatchers.IO) {
        ensureConfigured()
        val payload = openConnection("/api/sessions/$sessionId").readJsonObject()
        PhoneImportSessionSnapshot(
            sessionId = payload.getString("sessionId"),
            shortCode = payload.getString("shortCode"),
            mobileUrl = payload.getString("mobileUrl"),
            expiresAtEpochMillis = payload.getLong("expiresAtEpochMillis"),
            status = PhoneImportSessionStatus.valueOf(payload.getString("status")),
            feedUrls = payload.optJSONArray("feedUrls").toStringList(),
            invalidCount = payload.optInt("invalidCount", 0),
            duplicateCountWithinPayload = payload.optInt("duplicateCountWithinPayload", 0),
        )
    }

    private fun ensureConfigured() {
        check(isConfigured()) {
            "手机导入服务未配置"
        }
    }

    private fun openConnection(path: String, method: String = "GET"): HttpURLConnection {
        val separator = if (path.startsWith("/")) "" else "/"
        return (URL("${baseUrl.trimEnd('/')}$separator$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = method
            setRequestProperty("User-Agent", "WearPod/0.1 (Wear OS phone import)")
            setRequestProperty("Accept", "application/json")
        }
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
