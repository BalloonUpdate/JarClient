package com.github.asforest.exception

abstract class BaseException: Exception
{
    constructor(message: String): super(message)

    abstract fun getDisplayName(): String

    override fun toString(): String
    {
        return getDisplayName() + ", Reason: " + message
    }
}