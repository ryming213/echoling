package com.echoling.app.player.subtitle

data class Subtitle(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val contentEn: String,
    val contentCn: String = ""
)