package com.github.asforest.exception

class WrongWorkDirectoryException(message: String) : BaseException(message)
{
    override fun getDisplayName() = "找不到.minecraft目录"
}