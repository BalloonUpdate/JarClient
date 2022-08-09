package com.github.balloonupdate.util
import com.github.balloonupdate.exception.SecurityReasonException
import java.lang.ClassCastException
import java.security.MessageDigest

object Utils
{
    /**
     * 统计文件数量
     */
    @JvmStatic
    fun countFiles(directory: FileObject): Int
    {
        var count = 0
        val files: List<FileObject>

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


    @JvmStatic
    fun walkFile(directory: FileObj, base: FileObj, callback: (dir: FileObj, path: String) -> Unit)
    {
        for (file in directory.files)
        {
            if (file.isFile)
                callback(file, file.relativizedBy(base))
            else
                walkFile(file, base, callback)
        }
    }

    fun sha1(content: String): String = hash(content, "SHA1")

    fun md5(content: String): String = hash(content, "MD5")

    private fun hash(content: String, method: String): String
    {
        val md = MessageDigest.getInstance(method)
        md.update(content.encodeToByteArray())
        return bin2str(md.digest())
    }

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

    fun getUrlFilename(url: String): String
    {
        if ("/" !in url)
            return ""
        return url.substring(url.lastIndexOf("/") + 1)
    }
}