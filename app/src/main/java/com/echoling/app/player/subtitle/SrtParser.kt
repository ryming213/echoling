package com.echoling.app.player.subtitle

import android.util.Log
import javax.inject.Inject

class SrtParser @Inject constructor() : SubtitleParser {

    companion object {
        private const val TAG = "SrtParser"
    }

    override fun parse(content: String): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()

        // Normalize line endings
        val normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n")

        // Split by empty lines to get subtitle blocks
        val blocks = normalizedContent.split(Regex("\n\n+"))

        Log.d(TAG, "Total blocks found: ${blocks.size}")

        for (block in blocks) {
            val trimmedBlock = block.trim()
            if (trimmedBlock.isEmpty()) continue

            val lines = trimmedBlock.split("\n")
            if (lines.size < 2) continue

            // First line should be the index number
            val indexLine = lines[0].trim()
            val index = indexLine.toIntOrNull()
            if (index == null) {
                Log.w(TAG, "Failed to parse index from: $indexLine")
                continue
            }

            // Second line should be the timestamp
            if (lines.size < 2) continue
            val timeLine = lines[1].trim()
            val timeParts = timeLine.split("-->")
            if (timeParts.size != 2) {
                Log.w(TAG, "Invalid timestamp line: $timeLine")
                continue
            }

            val startTime = parseTime(timeParts[0].trim())
            val endTime = parseTime(timeParts[1].trim())
            if (startTime == null || endTime == null) {
                Log.w(TAG, "Failed to parse times from: $timeLine")
                continue
            }

            // Rest is subtitle content
            val subtitleLines = lines.drop(2).map { it.trim() }.filter { it.isNotEmpty() }
            if (subtitleLines.isEmpty()) continue

            val (contentEn, contentCn) = parseSubtitleContent(subtitleLines)

            if (contentEn.isNotEmpty() || contentCn.isNotEmpty()) {
                subtitles.add(Subtitle(index, startTime, endTime, contentEn, contentCn))
                Log.d(TAG, "Parsed subtitle $index: $contentEn / $contentCn")
            }
        }

        Log.d(TAG, "Total subtitles parsed: ${subtitles.size}")
        return subtitles.sortedBy { it.startTimeMs }
    }

    private fun parseSubtitleContent(lines: List<String>): Pair<String, String> {
        if (lines.isEmpty()) return Pair("", "")

        // Join all lines - SRT subtitles can have multiple lines per entry
        val fullContent = lines.joinToString("\n").trim()

        if (fullContent.isEmpty()) return Pair("", "")

        // Chinese Unicode range
        val chineseRegex = Regex("[\\u4e00-\\u9fff]")

        if (chineseRegex.containsMatchIn(fullContent)) {
            // Has Chinese - try to split bilingual
            return splitBilingual(fullContent)
        }

        // English only
        return Pair(fullContent, "")
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

    private fun parseTime(timeStr: String): Long? {
        // Format: HH:MM:SS,mmm or HH:MM:SS.mmm
        val regex = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
        val match = regex.find(timeStr) ?: return null

        val (hours, minutes, seconds, millis) = match.destructured
        return (hours.toLongOrNull() ?: return null) * 3600000 +
                (minutes.toLongOrNull() ?: return null) * 60000 +
                (seconds.toLongOrNull() ?: return null) * 1000 +
                (millis.toLongOrNull() ?: 0).coerceIn(0, 999)
    }
}
