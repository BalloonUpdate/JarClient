package com.github.asforest.util

import java.security.MessageDigest

object HashUtil
{
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
}