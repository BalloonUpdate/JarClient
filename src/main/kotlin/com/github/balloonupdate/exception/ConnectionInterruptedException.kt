package com.github.balloonupdate.exception

import com.github.balloonupdate.util.Utils

class ConnectionInterruptedException(url: String)
    : BaseException("连接中断(${Utils.getUrlFilename(url)}): $url")