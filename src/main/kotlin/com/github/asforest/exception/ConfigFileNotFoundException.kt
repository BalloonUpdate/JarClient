package com.github.asforest.exception

class ConfigFileNotFoundException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "找不到配置文件"
}
