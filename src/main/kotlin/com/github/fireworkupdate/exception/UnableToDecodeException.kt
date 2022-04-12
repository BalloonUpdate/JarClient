package com.github.fireworkupdate.exception

class UnableToDecodeException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "数据无法解码"
}