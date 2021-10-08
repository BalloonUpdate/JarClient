package com.github.asforest.workmode

import com.github.asforest.util.FileObj
import com.github.asforest.util.SimpleDirectory
import com.github.asforest.util.SimpleFile
import com.github.asforest.util.SimpleFileObject
import com.hrakaroo.glob.GlobPattern
import java.lang.RuntimeException

typealias OnScanCallback = (file: FileObj) -> Unit

abstract class AbstractMode
{
    val regexes: List<String>
    val base: FileObj
    val local: FileObj
    val remote: Array<SimpleFileObject>
    val result: Difference = Difference()

    /**
     * @param regexes 要比较的路径
     * @param local 要比较的本地文件
     * @param remote 要比较的远程文件
     */
    constructor(regexes: List<String>, local: FileObj, remote: Array<SimpleFileObject>)
    {
        this.regexes = regexes
        this.base = local
        this.local = local
        this.remote = remote
    }

    /**
     * 将一个文件文件或者目录标记为旧文件
     */
    protected fun markAsOld(file: FileObj)
    {
        if(file.isDirectory)
        {
            for (f in file.files)
            {
                if(f.isDirectory)
                    markAsOld(f)
                else
                    result.oldFiles += f.relativizedBy(base)
            }
            result.oldFolders += file.relativizedBy(base)
        } else {
            result.oldFiles += file.relativizedBy(base)
        }
    }

    /**
     * 将一个文件文件或者目录标记为新文件
     */
    protected fun markAsNew(node: SimpleFileObject, dir: FileObj)
    {
        if(node is SimpleDirectory)
        {
            result.newFolders += dir.relativizedBy(base)
            for (n in node.files)
                markAsNew(n, dir + n.name)
        } else if (node is SimpleFile){
            val rp = dir.relativizedBy(base)
            result.newFiles[rp] = node.length
        }
    }

    /** 测试指定的目录是否能通过路径匹配
     * @param path 需要测试的相对路径字符串
     * @return 是否通过了匹配
     */
    protected fun test(path: String): Boolean
    {
        if("\\" in path)
            throw RuntimeException("Not uniform separator style: $path")

        if(regexes.isEmpty())
            return false

        var result = false
        for (reg in regexes)
        {
            val plain = !reg.startsWith("@")
            val regx = if(plain) reg else reg.substring(1)
            result = result || if(plain) {
                val pattern = GlobPattern.compile(regx)
                pattern.matches(path)
            } else {
                Regex(regx).matches(path)
            }
        }
        return result
    }

    protected abstract fun compare(onScan: OnScanCallback?)

    operator fun invoke(onScan: OnScanCallback? =null): Difference
    {
        compare(onScan)
        return result
    }

    class Difference (
        val oldFolders: MutableList<String> = mutableListOf(),
        val oldFiles: MutableList<String> = mutableListOf(),
        val newFolders: MutableList<String> = mutableListOf(),
        val newFiles: MutableMap<String, Long> = mutableMapOf() // 文件名: 长度
    ) {
        operator fun plusAssign(other: Difference)
        {
            oldFolders += other.oldFolders
            oldFiles += other.oldFiles
            newFolders += other.newFolders
            newFiles += other.newFiles
        }
    }
}