package com.github.fireworkupdate.util
import com.github.fireworkupdate.data.FileObj
import java.lang.ClassCastException
import java.security.MessageDigest

object Utils
{
    /**
     * 统计文件数量
     */
    @JvmStatic
    fun countFiles(directory: FileObj): Int
    {
        var count = 0
        for (f in directory.files)
            count += if(f.isFile) 1 else countFiles(f)
        return count
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
}