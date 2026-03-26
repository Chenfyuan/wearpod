package com.sjtech.wearpod.data.rss

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class FeedNetworkClient {
    fun openStream(url: String): InputStream {
        val connection = openConnection(url)
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            error("Request failed with HTTP $status")
        }
        return connection.inputStream
    }

    fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WearPod/0.1 (Wear OS)")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
    }
}
