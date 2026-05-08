package com.echoling.app.player.subtitle

data class Subtitle(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val contentEn: String,
    val contentCn: String = ""
) {
    fun getContent(mode: SubtitleMode): String {
        return when (mode) {
            SubtitleMode.BILINGUAL -> if (contentCn.isNotEmpty()) {
                "$contentEn\n$contentCn"
            } else contentEn
            SubtitleMode.ENGLISH -> contentEn
            SubtitleMode.CHINESE -> contentCn.ifEmpty { contentEn }
        }
    }
}
