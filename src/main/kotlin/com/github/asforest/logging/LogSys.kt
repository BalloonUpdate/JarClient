package com.github.asforest.logging

object LogSys
{
    val handlers: MutableList<AbstractHandler> = mutableListOf()

    @JvmStatic
    fun debug(message: String) = message(LogLevel.DEBUG, message)

    fun info(message: String) = message(LogLevel.INFO, message)

    fun warn(message: String) = message(LogLevel.WARN, message)

    fun error(message: String) = message(LogLevel.ERROR, message)

    fun message(level: LogLevel, message: String)
    {
        for (h in handlers)
            if(level.ordinal >= h.filter.ordinal)
                h.onMessage(Message(level, message))
    }

    fun initialize()
    {
        addHandler(ConsoleHandler(this))
    }

    fun destory()
    {
        handlers.forEach { it.onDestroy() }
        handlers.clear()
    }

    fun addHandler(handler: AbstractHandler)
    {
        if(handler in handlers)
            return
        handlers += handler.also { it.onInit() }
    }

    inline fun <reified T> removeHandler() where T: AbstractHandler
    {
        for (h in handlers)
        {
            if(T::class == h.javaClass)
            {
                h.onDestroy()
                handlers.remove(h)
                return
            }
        }
    }

}

class Message(
    val level: LogLevel,
    val message: String,
    val newLineIndent: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

abstract class AbstractHandler(
    private val logsys: LogSys,
    val filter: LogLevel = LogLevel.ALL
) {
    open fun onInit() {}

    open fun onDestroy() {}

    abstract fun onMessage(message: Message)
}

enum class LogLevel
{
    ALL, DEBUG, INFO, WARN, ERROR, NONE
}