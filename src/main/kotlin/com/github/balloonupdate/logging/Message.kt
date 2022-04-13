package com.github.balloonupdate.logging

data class Message(
    val time: Long,
    val level: LogSys.LogLevel,
    val tag: String,
    val message: String,
    val newLineIndent: Boolean,
    val rangedTags: List<String>,
    val newLine: Boolean,
)