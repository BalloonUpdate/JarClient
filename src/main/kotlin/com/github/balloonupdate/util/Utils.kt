package com.github.balloonupdate.util

import com.github.balloonupdate.exception.SecurityReasonException
import java.security.MessageDigest

object Utils
{
    /**
     * 统计文件数量
     */
    @JvmStatic
    fun countFiles(directory: File2): Int
    {
        var count = 0
        val files: List<File2>

        try {
            files = directory.files
        } catch (e: NullPointerException) {
            throw SecurityReasonException(directory.path)
        }

        for (f in files)
            count += if(f.isFile) 1 else countFiles(f)

        return count
    }

    /**
     * 拆分较长的字符串到多行里
     */
    @JvmStatic
    fun stringBreak(str: String, lineLength: Int, newline: String="\n"): String
    {
        val lines = mutableListOf<String>()

        val lineCount = str.length / lineLength
        val remains = str.length % lineLength

        for (i in 0 until lineCount)
            lines += str.substring(lineLength * i, lineLength * (i + 1))

        if (remains > 0)
            lines += str.substring(lineLength * lineCount)

        return lines.joinToString(newline)
    }

    fun sha1(content: String): String = hash(content, "SHA1")

    fun md5(content: String): String = hash(content, "MD5")

    private fun hash(content: String, method: String): String
    {
        val md = MessageDigest.getInstance(method)
        md.update(content.encodeToByteArray())
        return bin2str(md.digest())
    }

    /**
     * 将二进制数据转换为十六进制字符串
     */
    private fun bin2str(binary: ByteArray): String
    {
        fun cvt (num: Byte): String
        {
            val hi = (num.toInt() shr 4) and 0x0F
            val lo = num.toInt() and 0x0F
            val sh = if(hi>9) (hi - 10 + 'a'.code.toByte()).toChar() else (hi + '0'.code.toByte()).toChar()
            val sl = if(lo>9) (lo - 10 + 'a'.code.toByte()).toChar() else (lo + '0'.code.toByte()).toChar()
            return sh.toString() + sl.toString()
        }
        return binary.joinToString("") { cvt(it) }
    }

    fun parseAsLong(number: Any): Long
    {
        return try {
            (number as Int).toLong()
        } catch (e: ClassCastException) {
            number as Long
        }
    }

    /**
     * 获取URL指向的文件名
     */
    fun getUrlFilename(url: String): String
    {
        if ("/" !in url)
            return ""
        return url.substring(url.lastIndexOf("/") + 1)
    }

    /**
     * 字节转换为kb, mb, gb等单位
     */
    fun convertBytes(bytes: Long, b: String = "B", kb: String = "KB", mb: String = "MB", gb: String = "GB"): String
    {
        return when {
            bytes < 1024 -> "${String.format("%.1f", bytes.toFloat())} $b"
            bytes < 1024 * 1024 -> "${String.format("%.1f", (bytes / 1024f))} $kb"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", (bytes / 1024 / 1024f))} $mb"
            else -> "${String.format("%.1f", (bytes / 1024 / 1024 / 1024f))} $gb"
        }
    }
}