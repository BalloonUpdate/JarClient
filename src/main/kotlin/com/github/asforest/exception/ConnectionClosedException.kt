package com.github.asforest.exception

class ConnectionClosedException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "连接关闭"
}