package com.github.fireworkupdate.exception

class ConnectionClosedException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "连接关闭"
}