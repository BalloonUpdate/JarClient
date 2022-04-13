package com.github.balloonupdate.logging

abstract class AbstractHandler(
    private val logsys: LogSys,
    val filter: LogSys.LogLevel = LogSys.LogLevel.ALL
) {
    open fun onInit() {}

    open fun onDestroy() {}

    abstract fun onMessage(message: Message)
}