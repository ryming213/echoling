package com.echoling.app.player.subtitle

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleParserFactory @Inject constructor(
    private val srtParser: SrtParser,
    private val assParser: AssParser
) {
    fun getParser(fileName: String): SubtitleParser {
        return when {
            fileName.endsWith(".srt", ignoreCase = true) -> srtParser
            fileName.endsWith(".ass", ignoreCase = true) -> assParser
            fileName.endsWith(".ssa", ignoreCase = true) -> assParser
            else -> srtParser
        }
    }

    fun parseSubtitles(content: String, fileName: String): List<Subtitle> {
        val parser = getParser(fileName)
        return parser.parse(content)
    }
}
