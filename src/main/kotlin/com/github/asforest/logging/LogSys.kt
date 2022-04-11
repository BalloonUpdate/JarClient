package com.github.asforest.logging

import java.util.*

object LogSys
{
    val handlers: MutableList<AbstractHandler> = mutableListOf()

    val rangedTags = LinkedList<String>()

    fun debug(message: String) = message(LogLevel.DEBUG, "", message)

    fun info(message: String) = message(LogLevel.INFO, "", message)

    fun warn(message: String) = message(LogLevel.WARN, "", message)

    fun error(message: String) = message(LogLevel.ERROR, "", message)

    fun debug(tag: String, message: String) = message(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = message(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = message(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = message(LogLevel.ERROR, tag, message)

    fun message(level: LogLevel, tag: String, message: String)
    {
        for (h in handlers)
            if(level.ordinal >= h.filter.ordinal)
                h.onMessage(Message(
                    time = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    newLineIndent = false,
                    rangedTags
                ))
    }

    fun openRangedTag(tag: String)
    {
        if (rangedTags.lastOrNull().run { this == null || this != tag })
            rangedTags.addLast(tag)
    }

    fun closeRangedTag()
    {
        if (rangedTags.isNotEmpty())
            rangedTags.removeLast()
    }

    fun withRangedTag(tag: String, scope: () -> Unit)
    {
        val split = tag.split("/")

        for (s in split)
            openRangedTag(s)

        scope()

        for (s in split)
            closeRangedTag()
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

    enum class LogLevel
    {
        ALL, DEBUG, INFO, WARN, ERROR, NONE
    }
}

