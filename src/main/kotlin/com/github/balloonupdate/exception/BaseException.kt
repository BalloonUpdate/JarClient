package com.github.balloonupdate.exception

abstract class BaseException(message: String) : Exception(message)
{
    override fun toString(): String = message ?: "No Exception Message"
}