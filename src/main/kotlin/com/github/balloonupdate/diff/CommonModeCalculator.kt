package com.github.balloonupdate.diff

import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.File2
import com.github.balloonupdate.data.SimpleDirectory
import com.github.balloonupdate.data.SimpleFile
import com.github.balloonupdate.data.SimpleFileObject
import kotlin.math.abs

/**
 * 默认同步指定文件夹内的所有文件，
 * 如果指定了正则表达式，则会使用正则表达式进行进一步筛选
 * 不匹配的文件会被忽略掉(不做任何变动)
 * 匹配的文件会与服务器进行同步
 */
class CommonModeCalculator(local: File2, remote: List<SimpleFileObject>, opt: Options)
    : DiffCalculatorBase(local, remote, opt)
{
    override fun compare(onScan: OnScanCallback?)
    {
        findOutNews(local, remote, base, onScan)
        LogSys.debug("-------------------")
        findOutOlds(local, remote, base, onScan)
    }

    /** 扫描需要下载的文件(不包括被删除的)
     * @param local 要拿来进行对比的本地目录
     * @param remote 要拿来进行对比的远程目录
     * @param base 基准目录，用于计算相对路径（一般等于local）
     * @param onScan 扫描回调，用于报告计算进度
     */
    private fun findOutNews(
        local: File2,
        remote: List<SimpleFileObject>,
        base: File2,
        onScan: OnScanCallback?,
        indent: String = ""
    ) {
        for (r in remote)
        {
            val l = local + r.name // 此文件可能不存在
            val direct = test(l.relativizedBy(base)) // direct=true时,indirect必定为true
            val indirect = checkIndirectMatches(r, local.relativizedBy(base), indent)

            val flag = (if(direct) "+" else (if(indirect) "-" else " "))

            LogSys.debug("N:  $flag   $indent${r.name}")
            if (onScan != null)
                onScan(l)

            if(!direct && !indirect)
                continue

            if(l.exists) // 文件存在的话要进行进一步判断
            {
                if(r is SimpleDirectory) // 远程文件是一个目录
                {
                    if(l.isFile) // 本地文件和远程文件的文件类型对不上
                    {
                        markAsOld(l)
                        markAsNew(r, l)
                    } else { // 本地文件和远程文件都是目录，则进行进一步判断
                        findOutNews(l, r.files, base, onScan, "$indent    ")
                    }
                } else if(r is SimpleFile) { // 远程文件是一个文件
                    if(l.isFile) // 本地文件和远程文件都是文件，则对比校验
                    {
                        compareSingleFile(l, r, indent)
                    } else { // 本地文件是一个目录
                        markAsOld(l)
                        markAsNew(r, l)
                    }
                }
            } else { // 如果文件不存在的话，就不用校验了，可以直接进行下载
                LogSys.debug("   " + indent + "Not found, download " + r.name)
                markAsNew(r, l)
            }
        }
    }

    /**
     * 对比两个路径相同的文件是否一致
     */
    private fun compareSingleFile(l: File2, r: SimpleFile, indent: String)
    {
        var isUpToDate = false

        if(opt.checkModified)
            isUpToDate = abs(l.modified - r.modified) < 5000

        if(!isUpToDate)
        {
            val lhash = calculateHash(l)
            if(lhash != r.hash)
            {
                LogSys.debug("   "+indent+"Hash not matched: Local: " + lhash + "   Remote: " + r.hash)
                markAsOld(l)
                markAsNew(r, l)
            } else if (opt.checkModified && r.modified != -1L) {
                // 更新修改时间
//                if (opt.androidPatch == null)
                l.file.setLastModified(r.modified)
            }
        }
    }

    /** 扫描需要删除的文件
     * @param local 要拿来进行对比的本地目录
     * @param remote 要拿来进行对比的远程目录
     * @param base 基准目录，用于计算相对路径（一般等于local）
     * @param onScan 扫描回调，用于报告计算进度
     */
    private fun findOutOlds(
        local: File2,
        remote: List<SimpleFileObject>,
        base: File2,
        onScan: OnScanCallback?,
        indent: String =""
    ) {
        for (l in local.files)
        {
            val r = remote.firstOrNull { it.name == l.name } // 尝试获取对应远程文件
            val direct = test(l.relativizedBy(base)) // direct=true时, indirect必定为true
            val indirect = checkIndirectMatches(l, local.relativizedBy(base), indent)

            val flag = (if(direct) "+" else (if(indirect) "-" else " "))

            LogSys.debug("O:  $flag   $indent${l.name}")
            if (onScan != null)
                onScan(l)

            if(direct)
            {
                if(r!=null) // 如果远程文件存在
                {
                    if(l.isDirectory && r is SimpleDirectory)
                        findOutOlds(l, r.files, base, onScan, "$indent    ")
                } else { // 远程文件不存在，就直接删掉好了
                    markAsOld(l)
                }
            } else if(indirect) { // 此时direct必定为false,且l一定是个目录
                if(r!=null) // 如果没有这个远程文件，则不需要进行进一步判断，直接跳过即可
                    findOutOlds(l, (r as SimpleDirectory).files, base, onScan, "$indent    ")
            }
        }
    }

    /**
     * 检查file是否满足间接匹配条件
     */
    private fun checkIndirectMatches(file: File2, parent: String, indent: String =""): Boolean
    {
        val parent1 = if(parent == "." || parent == "./") "" else parent
        val path = parent1 + (if(parent1 != "") "/" else "") + file.name

        var result = false
        if(file.isDirectory)
        {
            for (f in file.files)
                result = result || checkIndirectMatches(f, path, "$indent    ")
        } else {
            result = test(path)
        }
        return result
    }

    /**
     * 检查file是否满足间接匹配条件
     */
    private fun checkIndirectMatches(file: SimpleFileObject, parent: String, indent: String =""): Boolean
    {
        val parent1 = if(parent == "." || parent == "./") "" else parent
        val path = parent1 + (if(parent1 != "") "/" else "") + file.name

        var result = false
        if(file is SimpleDirectory)
        {
            for (f in file.files)
                result = result || checkIndirectMatches(f, path, "$indent    ")
        } else if(file is SimpleFile) {
            result = test(path)
        }
        return result
    }
}