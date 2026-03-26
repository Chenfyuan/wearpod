package com.sjtech.wearpod.data.rss

import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
)

class PodcastFeedParser {
    fun parse(feedUrl: String, inputStream: InputStream): ParsedFeed {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(inputStream)

        val channel = document.getElementsByTagName("channel").item(0) as? Element
            ?: error("Unsupported feed at $feedUrl")

        val artworkUrl = channel.imageHref()
            ?: channel.nestedText("image", "url")
            ?: channel.firstDescendantAttr("media:thumbnail", "url")

        val episodes = channel.descendants("item").mapNotNull { item ->
            val audioUrl = item.firstDescendantAttr("enclosure", "url")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            ParsedEpisode(
                guid = item.firstText("guid") ?: audioUrl,
                title = item.firstText("title") ?: "Untitled Episode",
                description = cleanDescription(
                    item.firstText("content:encoded")
                        ?: item.firstText("description")
                        ?: "",
                ),
                audioUrl = audioUrl,
                artworkUrl = item.imageHref()
                    ?: item.firstDescendantAttr("media:thumbnail", "url")
                    ?: artworkUrl,
                publishedAtEpochMillis = parseDate(
                    item.firstText("pubDate") ?: item.firstText("published"),
                ),
                durationSeconds = parseDurationToSeconds(item.firstText("itunes:duration")),
                sizeBytes = item.firstDescendantAttr("enclosure", "length")?.toLongOrNull(),
            )
        }

        return ParsedFeed(
            title = channel.firstText("title") ?: feedUrl,
            author = channel.firstText("itunes:author")
                ?: channel.firstText("author")
                ?: channel.firstText("managingEditor")
                ?: "Unknown",
            description = cleanDescription(
                channel.firstText("description")
                    ?: channel.firstText("itunes:summary")
                    ?: "",
            ),
            artworkUrl = artworkUrl,
            episodes = episodes.sortedByDescending { it.publishedAtEpochMillis ?: 0L },
        )
    }

    internal fun parseDurationToSeconds(value: String?): Int? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            normalized.all(Char::isDigit) -> normalized.toIntOrNull()
            ':' in normalized -> {
                val parts = normalized.split(':').mapNotNull { it.toIntOrNull() }
                when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> null
                }
            }

            else -> null
        }
    }

    private fun cleanDescription(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseDate(value: String?): Long? {
        val input = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val formatters = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.US),
        )
        for (formatter in formatters) {
            try {
                return ZonedDateTime.parse(input, formatter).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    return OffsetDateTime.parse(input, formatter).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    // Try the next pattern.
                }
            }
        }
        return null
    }
}

private fun Element.firstText(tagName: String): String? =
    descendants(tagName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun Element.nestedText(parentTag: String, childTag: String): String? =
    descendants(parentTag)
        .firstOrNull()
        ?.let { parent ->
            (0 until parent.childNodes.length)
                .mapNotNull { index -> parent.childNodes.item(index) as? Element }
                .firstOrNull { it.tagName == childTag }
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

private fun Element.firstDescendantAttr(tagName: String, attrName: String): String? =
    descendants(tagName).firstOrNull()?.getAttribute(attrName)?.takeIf { it.isNotBlank() }

private fun Element.imageHref(): String? =
    firstDescendantAttr("itunes:image", "href")
        ?: firstDescendantAttr("media:content", "url")

private fun Element.descendants(tagName: String): List<Element> =
    buildList {
        val nodeList = getElementsByTagName(tagName)
        repeat(nodeList.length) { index ->
            val node = nodeList.item(index)
            if (node is Element) add(node)
        }
    }
