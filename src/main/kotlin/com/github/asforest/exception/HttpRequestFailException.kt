package com.github.asforest.exception

class HttpRequestFailException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "服务器未能响应正确的数据"
}