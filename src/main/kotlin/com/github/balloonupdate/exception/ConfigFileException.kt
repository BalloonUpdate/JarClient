package com.github.balloonupdate.exception

class ConfigFileException(message: String) : BaseException(message)
{
    override fun getDisplayName(): String = "配置文件读取失败"
}