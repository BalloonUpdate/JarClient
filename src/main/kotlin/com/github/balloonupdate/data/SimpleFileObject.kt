package com.github.balloonupdate.data

abstract class SimpleFileObject(var name: String)

class SimpleDirectory(name: String, var files: List<SimpleFileObject>) : SimpleFileObject(name)
{
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

class SimpleFile(name: String, var length: Long, var hash: String, var modified: Long) : SimpleFileObject(name)