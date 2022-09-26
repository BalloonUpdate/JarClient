package com.github.balloonupdate.exception

class FailedToParsingException(source: String, format: String, more: String)
    : BaseException("$source 无法解码为 $format 格式 ($more)")