package com.github.balloonupdate.diff

import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.File2
import com.github.balloonupdate.data.SimpleDirectory
import com.github.balloonupdate.data.SimpleFile
import com.github.balloonupdate.data.SimpleFileObject

/**
 * 仅下载不存在的文件，如果文件存在，会直接跳过，不会做任何变动
 * 此模式不具有删除文件的功能，因此任何情况下不会删除任何文件
 * 如果本地和远程的文件类型不同，也不会作任何文件变动
 * 如果规则指向一个文件夹，则仅在第一次不存在时下载这个文件夹及其子目录
 * 之后除非这个文件夹被删除，否则不会再进行下载任何相关文件
 * 顺便，此模式也不具有创建空文件夹的功能
 */
class OnceModeCalculator (local: File2, remote: List<SimpleFileObject>, opt: Options)
    : DiffCalculatorBase(local, remote, opt)
{
    override fun compare(onScan: OnScanCallback?)
    {
        findOutNews(local, remote, base, onScan)
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
        indent: String =""
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
                if(l.isDirectory && r is SimpleDirectory)
                    findOutNews(l, r.files, base, onScan, "$indent    ")
            } else { // 如果文件不存在的话，就不用校验了，可以直接进行下载
                LogSys.debug("   " + indent + "Not found, download " + r.name)
                markAsNew(r, l)
            }
        }
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