package com.github.balloonupdate.exception

import com.github.balloonupdate.util.Utils

class ConnectionRejectedException(url: String, more: String)
    : BaseException("连接被拒绝(${Utils.getUrlFilename(url)}): $url ($more)")