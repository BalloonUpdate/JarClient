package com.github.asforest.data

abstract class SimpleFileObject(var name: String)

class SimpleDirectory: SimpleFileObject
{
    var files: List<SimpleFileObject>

    constructor(name: String, files: List<SimpleFileObject>) : super(name)
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
    var modified: Long

    constructor(name: String, length: Long, hash: String, modified: Long) : super(name)
    {
        this.length = length
        this.hash = hash
        this.modified = modified
    }
}