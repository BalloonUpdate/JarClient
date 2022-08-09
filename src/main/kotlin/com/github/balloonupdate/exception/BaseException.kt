package com.github.balloonupdate.exception

abstract class BaseException: Exception
{
    constructor(message: String): super(message)

    override fun toString(): String
    {
        return message ?: "No Exception Message"
    }
}