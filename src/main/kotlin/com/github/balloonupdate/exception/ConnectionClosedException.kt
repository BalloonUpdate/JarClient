package com.github.balloonupdate.exception

class ConnectionClosedException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "连接关闭"
}