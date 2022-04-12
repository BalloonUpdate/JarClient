package com.github.fireworkupdate.data

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.pathString

class FileObj
{
    private val _file: File

    constructor(file: String)
    {
        this._file = File(file)
    }

    constructor(file: File)
    {
        this._file = file.absoluteFile
    }

    val file: File get() = _file

    val name: String get() = _file.name

    val isDirectory: Boolean get() = _file.isDirectory

    val isFile: Boolean get() = _file.isFile

    val exists: Boolean get() = _file.exists()

    val parent: FileObj get() = FileObj(_file.parent)

    fun mkdirs() = _file.mkdirs()

    fun makeParentDirs() = parent.mkdirs()

    fun rename(newName: String) = _file.renameTo(File(newName))

    fun touch(fileContent: String? =null)
    {
        content = fileContent ?: ""
    }

    var content: String
        get() {
            if(!exists)
                throw FileNotFoundException(path)
            FileInputStream(_file).use {
                return it.readBytes().decodeToString()
            }
        }
        set(value) {
            if(!exists)
                _file.createNewFile()
            FileOutputStream(_file).use {
                it.write(value.encodeToByteArray())
            }
        }

    fun append(content: String)
    {
        if(!exists)
            _file.createNewFile()
        FileOutputStream(_file, true).use {
            it.write(content.encodeToByteArray())
        }
    }

    val length: Long
        get() {
            if(!exists)
                throw FileNotFoundException(path)
            if(isDirectory)
                throw FileNotFoundException("is not a file: $path")
            return _file.length()
        }

    val modified: Long
        get() {
            if(!exists)
                throw FileNotFoundException(path)
            if(isDirectory)
                throw FileNotFoundException("is not a file: $path")
            return _file.lastModified()
        }

    val files: List<FileObj> get() = _file.listFiles().map { FileObj(it) }

    val isDirty: Boolean
        get() {
            if(!exists)
                throw FileNotFoundException(path)
            return if(isFile) length==0L else files.isEmpty()
        }

    fun clear()
    {
        if(!exists)
            return
        if(isDirectory)
            for (f in files)
                f.delete()
        else
            content = ""
    }

    fun delete()
    {
        if(!exists)
            return
        if(isDirectory)
            for (f in files)
                f.delete()
        _file.delete()
    }

    fun copy(target: FileObj)
    {
        _file.copyRecursively(target._file, overwrite = true)
    }

    fun move(target: FileObj)
    {
        copy(target)
        target.delete()
    }

    val path: String get() = platformPath.replace("\\", "/")

    val platformPath: String get() = _file.absolutePath

    fun relativize(target: FileObj, platformize: Boolean = false): String {
        return Paths.get(path).relativize(Paths.get(target.path)).pathString.run {
            if(!platformize) replace("\\", "/") else this
        }
    }

    fun relativizedBy(base: FileObj, platformize: Boolean = false): String {
        return Paths.get(base.path).relativize(Paths.get(path)).pathString.run {
            if(!platformize) replace("\\", "/") else this
        }
    }

    operator fun plus(value: String): FileObj
    {
        return FileObj(path + File.separator + value)
    }

    operator fun invoke(value: String): FileObj
    {
        return this + value
    }

    operator fun get(value: String): FileObj
    {
        return this + value
    }

    operator fun contains(value: String): Boolean
    {
        return (this + value).exists
    }

    val sha1: String get() = hash("SHA1")

    val md5: String get() = hash("MD5")

    private fun hash(method: String): String
    {
        val bufferLen = { filelen: Long ->
            val kb = 1024
            val mb = 1024 * 1024
            val gb = 1024 * 1024 * 1024
            when {
                filelen < 1 * mb -> 8 * kb
                filelen < 2 * mb -> 16 * kb
                filelen < 4 * mb -> 32 * kb
                filelen < 8 * mb -> 64 * kb
                filelen < 16 * mb -> 256 * kb
                filelen < 32 * mb -> 512 * kb
                filelen < 64 * mb -> 1 * mb
                filelen < 128 * mb -> 2 * mb
                filelen < 256 * mb -> 4 * mb
                filelen < 512 * mb -> 8 * mb
                filelen < 1 * gb -> 16 * mb
                else -> 32 * mb
            }
        }

        val md = MessageDigest.getInstance(method)
        FileInputStream(_file).use {
            var len = 0
            val buf = ByteArray(bufferLen(length))
            while (it.read(buf).also { len = it } != -1)
                md.update(buf, 0, len)
        }
        return bin2str(md.digest())
    }

    private fun bin2str(binary: ByteArray): String
    {
        fun cvt (num: Byte): String
        {
            val hi = (num.toInt() shr 4) and 0x0F
            val lo = num.toInt() and 0x0F
            val sh = if(hi>9) (hi - 10 + 'a'.code.toByte()).toChar() else (hi + '0'.code.toByte()).toChar()
            val sl = if(lo>9) (lo - 10 + 'a'.code.toByte()).toChar() else (lo + '0'.code.toByte()).toChar()
            return sh.toString() + sl.toString()
        }
        return binary.joinToString("") { cvt(it) }
    }
}