package com.github.asforest.logging

import java.text.SimpleDateFormat

class ConsoleHandler(logsys: LogSys) : AbstractHandler(logsys)
{
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS")

    override fun onMessage(message: Message)
    {
        val ts = fmt.format(System.currentTimeMillis())
        val level = message.level.name.uppercase()
        val prefix = String.format("[ %s %-1s ] ", ts, level[0])

        var text = prefix + message.message
        if(message.newLineIndent)
            text = text.replace(Regex("\n"), "\n"+prefix)

        if(message.level.ordinal >= LogLevel.WARN.ordinal)
            System.err.println(text)
        else
            System.out.println(text)
    }
}