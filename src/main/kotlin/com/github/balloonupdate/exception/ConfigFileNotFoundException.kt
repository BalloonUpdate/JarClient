package com.github.balloonupdate.exception

class ConfigFileNotFoundException(file: String)
    : BaseException("找不到配置文件: $file")