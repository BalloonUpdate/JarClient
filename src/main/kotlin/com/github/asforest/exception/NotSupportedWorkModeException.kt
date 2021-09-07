package com.github.asforest.exception

class NotSupportedWorkModeException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "更新模式不支持"
}