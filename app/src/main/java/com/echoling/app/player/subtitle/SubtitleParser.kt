package com.echoling.app.player.subtitle

interface SubtitleParser {
    fun parse(content: String): List<Subtitle>
    fun findSubtitleAtTime(subtitles: List<Subtitle>, timeMs: Long): Subtitle?
}
