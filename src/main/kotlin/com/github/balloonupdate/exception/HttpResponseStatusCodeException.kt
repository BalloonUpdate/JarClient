package com.github.balloonupdate.exception

import com.github.balloonupdate.util.Utils

class HttpResponseStatusCodeException(statusCode: Int, url: String, body: String?)
    : BaseException("Http状态码($statusCode)不在2xx-3xx之间(${Utils.getUrlFilename(url)})\n$url\n${body ?: "<No Body>"}")