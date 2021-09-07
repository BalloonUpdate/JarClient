package com.github.asforest.workmode

import com.github.asforest.logging.LogSys
import com.github.asforest.util.FileObj
import com.github.asforest.util.SimpleDirectory
import com.github.asforest.util.SimpleFile
import com.github.asforest.util.SimpleFileObject

/**
 * 默认同步指定文件夹内的所有文件，
 * 如果指定了正则表达式，则会使用正则表达式进行进一步筛选
 * 不匹配的文件会被忽略掉(不做任何变动)
 * 匹配的文件会与服务器进行同步
 */
class CommonMode(regexes: List<String>, target: FileObj, template: Array<SimpleFileObject>) : BasicMode(regexes, target, template)
{
    override fun compare(onScan: ((file: FileObj) -> Unit)?)
    {
        findOutNews(target, template, base, onScan)
        LogSys.debug("-------------------")
        findOutOlds(target, template, base, onScan)
    }

    /** 扫描需要下载的文件(不包括被删除的)
     * @param target 要拿来进行对比本地目录对象
     * @param template 与之对比用的模板对象
     * @param base 基准目录，用于计算相对路径
     */
    private fun findOutNews(target: FileObj, template: Array<SimpleFileObject>, base: FileObj, onScan: OnScanCallback?, indent: String ="")
    {
        for (r in template)
        {
            val l = target + r.name // 此文件可能不存在
            val direct = test(l.relativizedBy(base)) // direct=true时,indirect必定为true
            val indirect = checkIndirectMatches(r, target.relativizedBy(base), indent)

            val flag = (if(direct) "+" else (if(indirect) "-" else " "))

            LogSys.debug("N:  $flag   $indent${r.name}")
            if (onScan != null)
                onScan(l)

            if(!direct && !indirect)
                continue

            if(l.exists) // 文件存在的话要进行进一步判断
            {
                if(r is SimpleDirectory) // 模板中的文件是一个目录
                {
                    if(l.isFile) // 本地文件和模板中的文件类型对不上
                    {
                        markAsOld(l)
                        markAsNew(r, l)
                    } else { // 本地文件和模板中的文件都是目录，则进行进一步判断
                        findOutNews(l, r.files, base, onScan, "$indent    ")
                    }
                } else if(r is SimpleFile) { // 模板中的文件是一个文件
                    if(l.isFile) // 本地文件和模板中的文件都是文件，则对比校验
                    {
                        val lsha1 = l.sha1
                        if(lsha1 != r.hash)
                        {
                            LogSys.debug("   "+indent+"Hash not matched: L: " + lsha1 + "   R: " + r.hash)
                            markAsOld(l)
                            markAsNew(r, l)
                        }
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

    /** 扫描需要删除的文件
     * @param target 要拿来进行对比本地目录对象
     * @param template 与之对比用的模板对象
     * @param base 基准目录，用于计算相对路径
     */
    private fun findOutOlds(target: FileObj, template: Array<SimpleFileObject>, base: FileObj, onScan: OnScanCallback?, indent: String ="")
    {
        fun get(name: String, list: Array<SimpleFileObject>): SimpleFileObject?
        {
            for (n in list)
                if(n.name == name)
                    return n
            return null
        }

        for (l in target.files)
        {
            val r = get(l.name, template) // 参数模板中的对应对象，可能会返回None
            val direct = test(l.relativizedBy(base)) // direct=true时,indirect必定为true
            val indirect = checkIndirectMatches(l, target.relativizedBy(base), indent)

            val flag = (if(direct) "+" else (if(indirect) "-" else " "))

            LogSys.debug("O:  $flag   $indent${l.name}")
            if (onScan != null)
                onScan(l)

            if(direct)
            {
                if(r!=null) // 如果模板中也有这个文件
                {
                    if(l.isDirectory && r is SimpleDirectory)
                        findOutOlds(l, r.files, base, onScan, "$indent    ")
                } else { // 模板中没有这个文件，就直接删掉好了
                    markAsOld(l)
                }
            } else if(indirect) { // 此时direct必定为false,且l一定是个目录
                if(r!=null) // 如果模板中也有这个文件。如果没有，则不需要进行进一步判断，直接跳过即可
                    findOutOlds(l, (r as SimpleDirectory).files, base, onScan, "$indent    ")
            }
        }
    }

    private fun checkIndirectMatches(file: FileObj, parent: String, indent: String =""): Boolean
    {
        val parent1 = if(parent == "." || parent == "./") "" else  parent
        val path = parent1 + (if(parent1 != "") "/" else "") + file.name

        var result: Boolean
        if(file.isDirectory)
        {
            result = false
            for (f in file.files)
                result = result || checkIndirectMatches(f, path, "$indent    ")
        } else {
            result = test(path)
        }
        return result
    }

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