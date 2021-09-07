//@file:JvmName("SimpleFileObject")
package com.github.asforest.util

abstract class SimpleFileObject
{
    var name: String

    constructor(name: String)
    {
        this.name = name
    }
}

class SimpleDirectory: SimpleFileObject
{
    var files: Array<SimpleFileObject>

    constructor(name: String, files: Array<SimpleFileObject>) : super(name)
    {
        this.files = files
    }

    operator fun get(name: String): SimpleFileObject?
    {
        for(f in files)
            if(name == f.name)
                return f
        return null
    }

    operator fun contains(name: String): Boolean
    {
        return name in files.map {it.name}
    }
}

class SimpleFile: SimpleFileObject
{
    var length: Long
    var hash: String

    constructor(name: String, length: Long, hash: String) : super(name)
    {
        this.length = length
        this.hash = hash
    }
}