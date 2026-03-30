package com.sjtech.wearpod.data.opml

import com.sjtech.wearpod.data.model.Subscription
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class OpmlCodec {
    fun buildSubscriptionsExport(subscriptions: List<Subscription>): String {
        val exportedAt = OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

        val outlines = subscriptions
            .sortedBy { it.title.lowercase() }
            .joinToString(separator = "\n") { subscription ->
                val title = subscription.title.ifBlank { subscription.feedUrl }
                val authorSuffix = subscription.author
                    .takeIf { it.isNotBlank() }
                    ?.let { " - $it" }
                    .orEmpty()
                """    <outline text="${escapeXml(title)}" title="${escapeXml(title)}" type="rss" xmlUrl="${escapeXml(subscription.feedUrl)}" description="${escapeXml("$title$authorSuffix")}" />"""
            }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head>
                <title>WearPod subscriptions</title>
                <dateCreated>${escapeXml(exportedAt)}</dateCreated>
              </head>
              <body>
            $outlines
              </body>
            </opml>
        """.trimIndent()
    }

    private fun escapeXml(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
