package com.github.asforest.logging

import com.github.asforest.file.FileObj
import com.github.asforest.util.EnvUtil
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat

class FileHandler(logsys: LogSys) : AbstractHandler(logsys)
{
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
    lateinit var logFile: FileObj
    var fileWriter: PrintWriter? = null

    override fun onInit()
    {
        val workdir = System.getProperty("user.dir").run { FileObj(if(EnvUtil.isPackaged) this else "$this${File.separator}workdir") }

        logFile = workdir + "updater.log"

        if (logFile.exists)
        {
            logFile.clear()
            fileWriter =  PrintWriter(logFile._file)
        }
    }

    override fun onDestroy() {
        fileWriter?.close()
    }

    override fun onMessage(message: Message)
    {
        if (fileWriter == null)
            return

        val ts = fmt.format(System.currentTimeMillis())
        val level = message.level.name.uppercase()
        val tag = if (message.tag != "") "[${message.tag}] " else ""
        val rangedTags = message.rangedTags.joinToString("/").run { if (isNotEmpty()) "[$this] " else "" }
        val prefix = String.format("[ %s %-5s ] %s%s", ts, level, rangedTags, tag)

        var text = prefix + message.message
        if(message.newLineIndent)
            text = text.replace(Regex("\n"), "\n"+prefix)

        fileWriter!!.println(text)
        fileWriter?.flush()
    }
}