package com.github.balloonupdate.exception

class ManifestNotReadableException(message: String) : BaseException(message) {
    override fun getDisplayName(): String = "Manifest信息获取失败"
}