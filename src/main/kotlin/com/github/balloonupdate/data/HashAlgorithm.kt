package com.github.balloonupdate.data

/**
 * 支持的哈希算法
 */
enum class HashAlgorithm
{
    SHA1, CRC32, MD5;

    companion object {
        @JvmStatic
        fun FromString(str: String, default: HashAlgorithm): HashAlgorithm
        {
            return when (str) {
                "crc32" -> CRC32
                "md5" -> MD5
                "sha1" -> SHA1
                else -> default
            }
        }
    }
}