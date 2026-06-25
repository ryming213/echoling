package com.echoling.app.player.subtitle

import android.util.Log
import java.io.BufferedReader
import javax.inject.Inject

class LrcParser @Inject constructor() : SubtitleParser {

    companion object {
        private const val TAG = "LrcParser"
        private val TIME_TAG_REGEX = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")
        private val WORD_TIME_TAG_REGEX = Regex("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>")
    }

    override fun parse(content: String): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        val normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n")

        val lines = normalizedContent.split("\n")
        Log.d(TAG, "Total lines found: ${lines.size}")

        val lyricsWithTime = mutableListOf<LyricLine>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Extract all time tags from the line
            val timeTags = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timeTags.isEmpty()) continue

            // Get the text content (remove time tags)
            val textContent = trimmed.replace(TIME_TAG_REGEX, "").trim()
            if (textContent.isEmpty()) continue

            // Parse word-level timing if present
            val wordTimings = parseWordTimings(textContent)

            // Create a subtitle for each time tag (LRC can have multiple timestamps per line)
            for (timeTag in timeTags) {
                val (minutes, seconds, centis) = parseTimeComponents(timeTag)
                val startTimeMs = minutes.toLong() * 60000 + seconds.toLong() * 1000 + centis.toLong()

                val (contentEn, contentCn) = parseSubtitleContent(textContent)

                if (contentEn.isNotEmpty() || contentCn.isNotEmpty()) {
                    // For LRC, use the line index as subtitle index
                    val index = lyricsWithTime.size + 1
                    lyricsWithTime.add(LyricLine(index, startTimeMs, contentEn, contentCn, wordTimings))
                }
            }
        }

        // Sort by start time and assign proper indices
        val sortedLyrics = lyricsWithTime.sortedBy { it.startTimeMs }
        sortedLyrics.forEachIndexed { idx, lyric ->
            subtitles.add(Subtitle(idx + 1, lyric.startTimeMs, lyric.endTimeMs, lyric.contentEn, lyric.contentCn))
        }

        // Calculate end times based on next subtitle's start time
        for (i in 0 until subtitles.size - 1) {
            val current = subtitles[i]
            val next = subtitles[i + 1]
            val updatedSubtitle = current.copy(endTimeMs = next.startTimeMs)
            subtitles[i] = updatedSubtitle
        }

        // Set end time for the last subtitle (default 5 seconds)
        if (subtitles.isNotEmpty()) {
            val last = subtitles.last()
            subtitles[subtitles.size - 1] = last.copy(endTimeMs = last.startTimeMs + 5000)
        }

        Log.d(TAG, "Total LRC subtitles parsed: ${subtitles.size}")
        return subtitles
    }

    fun parseStream(reader: BufferedReader): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        val lyricsWithTime = mutableListOf<LyricLine>()
        var lineCount = 0

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                lineCount++
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    // Extract all time tags from the line
                    val timeTags = TIME_TAG_REGEX.findAll(trimmed).toList()
                    if (timeTags.isNotEmpty()) {
                        val textContent = trimmed.replace(TIME_TAG_REGEX, "").trim()
                        if (textContent.isNotEmpty()) {
                            val wordTimings = parseWordTimings(textContent)

                            for (timeTag in timeTags) {
                                val (minutes, seconds, centis) = parseTimeComponents(timeTag)
                                val startTimeMs = minutes.toLong() * 60000 + seconds.toLong() * 1000 + centis.toLong()

                                val (contentEn, contentCn) = parseSubtitleContent(textContent)

                                if (contentEn.isNotEmpty() || contentCn.isNotEmpty()) {
                                    val index = lyricsWithTime.size + 1
                                    lyricsWithTime.add(LyricLine(index, startTimeMs, contentEn, contentCn, wordTimings))
                                }
                            }
                        }
                    }
                }

                line = reader.readLine()

                // Safety limit
                if (lineCount > 100000) {
                    Log.w(TAG, "Reached line limit, stopping parse")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading stream: ${e.message}", e)
        }

        // Sort by start time and assign proper indices
        val sortedLyrics = lyricsWithTime.sortedBy { it.startTimeMs }
        sortedLyrics.forEachIndexed { idx, lyric ->
            subtitles.add(Subtitle(idx + 1, lyric.startTimeMs, lyric.endTimeMs, lyric.contentEn, lyric.contentCn))
        }

        // Calculate end times based on next subtitle's start time
        for (i in 0 until subtitles.size - 1) {
            val current = subtitles[i]
            val next = subtitles[i + 1]
            subtitles[i] = current.copy(endTimeMs = next.startTimeMs)
        }

        // Set end time for the last subtitle (default 5 seconds)
        if (subtitles.isNotEmpty()) {
            val last = subtitles.last()
            subtitles[subtitles.size - 1] = last.copy(endTimeMs = last.startTimeMs + 5000)
        }

        Log.d(TAG, "Stream parsed: ${subtitles.size} subtitles from $lineCount lines")
        return subtitles
    }

    private fun parseWordTimings(text: String): List<WordTiming> {
        val timings = mutableListOf<WordTiming>()
        val matches = WORD_TIME_TAG_REGEX.findAll(text)

        for (match in matches) {
            val (minutes, seconds, centis) = parseTimeComponents(match)
            val timeMs = minutes.toLong() * 60000 + seconds.toLong() * 1000 + centis.toLong()
            // Extract the word after this timing tag
            val afterTag = text.substring(match.range.last + 1)
            val wordMatch = Regex("^([^<\\[]+)").find(afterTag)
            if (wordMatch != null) {
                timings.add(WordTiming(timeMs, wordMatch.value.trim()))
            }
        }

        return timings
    }

    private fun parseTimeComponents(tag: MatchResult): Triple<Int, Int, Int> {
        val values = tag.destructured
        val parts = values.toList()
        val minutes = parts[0].toIntOrNull() ?: 0
        val seconds = parts[1].toIntOrNull() ?: 0
        val centis = if (parts[2].length == 3) {
            parts[2].toIntOrNull() ?: 0
        } else {
            (parts[2].toIntOrNull() ?: 0) * 10
        }
        return Triple(minutes, seconds, centis)
    }

    private fun parseSubtitleContent(text: String): Pair<String, String> {
        if (text.isEmpty()) return Pair("", "")

        // Remove word-level timing tags for content analysis
        val cleanText = text.replace(WORD_TIME_TAG_REGEX, "")

        val chineseRegex = Regex("[\\u4e00-\\u9fff]+")

        if (chineseRegex.containsMatchIn(cleanText)) {
            return splitBilingual(cleanText)
        }

        return Pair(cleanText.trim(), "")
    }

    private fun splitBilingual(content: String): Pair<String, String> {
        val chineseRegex = Regex("[\\u4e00-\\u9fff]+")
        val chineseMatches = chineseRegex.findAll(content).toList()

        if (chineseMatches.isEmpty()) {
            return Pair(content, "")
        }

        val firstChineseIndex = chineseMatches.first().range.first
        val englishPart = content.substring(0, firstChineseIndex).trim()
        val chinesePart = content.substring(firstChineseIndex).trim()

        return Pair(englishPart, chinesePart)
    }

    override fun findSubtitleAtTime(subtitles: List<Subtitle>, timeMs: Long): Subtitle? {
        return subtitles.find { it.startTimeMs <= timeMs && it.endTimeMs >= timeMs }
    }

    private data class LyricLine(
        val index: Int,
        val startTimeMs: Long,
        val contentEn: String,
        val contentCn: String,
        val wordTimings: List<WordTiming> = emptyList()
    ) {
        val endTimeMs: Long = 0L // Will be calculated later
    }

    private data class WordTiming(
        val timeMs: Long,
        val word: String
    )
}