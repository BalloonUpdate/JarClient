package com.github.balloonupdate.exception

class SecurityReasonException(directory: String)
    : BaseException("由于安全机制或者IO错误，无法访问目录: $directory")