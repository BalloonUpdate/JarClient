package com.github.asforest.logging

import java.text.SimpleDateFormat

class ConsoleHandler(logsys: LogSys) : AbstractHandler(logsys)
{
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS")

    override fun onMessage(message: Message)
    {
        val ts = fmt.format(System.currentTimeMillis())
        val level = message.level.name.uppercase()
        val tag = if (message.tag != "") "[${message.tag}] " else ""
        val rangedTags = message.rangedTags.joinToString("/").run { if (isNotEmpty()) "[$this] " else "" }
        val prefix = String.format("[ %s %-5s ] %s%s", ts, level, rangedTags, tag)

        var text = prefix + message.message
        if(message.newLineIndent)
            text = text.replace(Regex("\n"), "\n"+prefix)

        if(message.level.ordinal >= LogSys.LogLevel.WARN.ordinal)
            System.err.println(text)
        else
            System.out.println(text)
    }
}