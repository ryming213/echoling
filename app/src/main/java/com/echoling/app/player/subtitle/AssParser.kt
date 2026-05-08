package com.echoling.app.player.subtitle

import javax.inject.Inject

class AssParser @Inject constructor() : SubtitleParser {

    override fun parse(content: String): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        var inEventsSection = false
        var formatLine: String? = null

        val lines = content.split("\\r?\\n".toRegex())

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.equals("[Events]", ignoreCase = true) -> {
                    inEventsSection = true
                }
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    inEventsSection = false
                }
                inEventsSection && trimmed.startsWith("Format:", ignoreCase = true) -> {
                    formatLine = trimmed.substringAfter("Format:").trim()
                }
                inEventsSection && trimmed.startsWith("Dialogue:", ignoreCase = true) -> {
                    parseDialogueLine(trimmed, formatLine)?.let { subtitles.add(it) }
                }
            }
        }

        return subtitles.sortedBy { it.startTimeMs }
    }

    override fun findSubtitleAtTime(subtitles: List<Subtitle>, timeMs: Long): Subtitle? {
        return subtitles.find { it.startTimeMs <= timeMs && it.endTimeMs >= timeMs }
    }

    private fun parseDialogueLine(line: String, formatLine: String?): Subtitle? {
        val dialogueContent = line.substringAfter("Dialogue:").trim() ?: return null

        if (formatLine == null) return null

        val formatFields = formatLine.split(",").map { it.trim() }
        val fieldMap = mutableMapOf<String, String>()

        var currentField = 0
        var inTag = false
        var fieldBuilder = StringBuilder()

        for (char in dialogueContent) {
            when {
                char == '=' && fieldBuilder.isEmpty() && !inTag -> {
                    val fieldName = fieldBuilder.toString().trim()
                    fieldBuilder.clear()
                    inTag = true
                    currentField++
                }
                char == ',' && !inTag -> {
                    if (currentField < formatFields.size) {
                        fieldMap[formatFields[currentField]] = fieldBuilder.toString().trim()
                    }
                    fieldBuilder.clear()
                    currentField++
                }
                else -> {
                    fieldBuilder.append(char)
                    if (char == '}' && inTag) {
                        inTag = false
                    }
                }
            }
        }

        if (fieldBuilder.isNotEmpty() && currentField < formatFields.size) {
            fieldMap[formatFields[currentField]] = fieldBuilder.toString().trim()
        }

        val startTime = parseAssTime(fieldMap["Start"] ?: return null) ?: return null
        val endTime = parseAssTime(fieldMap["End"] ?: return null) ?: return null
        val text = fieldMap["Text"]?.let { stripAssTags(it) } ?: return null

        if (text.isEmpty()) return null

        return Subtitle(
            index = 0,
            startTimeMs = startTime,
            endTimeMs = endTime,
            contentEn = text
        )
    }

    private fun parseAssTime(timeStr: String): Long? {
        // Format: H:MM:SS.cc (centiseconds)
        val regex = Regex("(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})")
        val match = regex.find(timeStr.trim()) ?: return null

        val (hours, minutes, seconds, centis) = match.destructured
        return (hours.toLongOrNull() ?: return null) * 3600000 +
                (minutes.toLongOrNull() ?: return null) * 60000 +
                (seconds.toLongOrNull() ?: return null) * 1000 +
                (centis.toLongOrNull() ?: 0) * 10
    }

    private fun stripAssTags(text: String): String {
        // Remove ASS style tags like {\an8}, {\pos(x,y)}, {\b1}, etc.
        return text
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\\\".toRegex(), "\\")
            .trim()
    }
}
