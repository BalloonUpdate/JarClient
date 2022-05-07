package com.github.balloonupdate.exception

class SecurityReasonException(message: String) : BaseException(message) {
    override fun getDisplayName(): String = "安全机制导致操作失败"
}