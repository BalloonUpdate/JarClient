package com.github.balloonupdate.diff

import com.github.balloonupdate.data.HashAlgorithm
import com.github.balloonupdate.util.File2
import com.github.balloonupdate.data.SimpleDirectory
import com.github.balloonupdate.data.SimpleFile
import com.github.balloonupdate.data.SimpleFileObject
import com.github.balloonupdate.util.HashUtils
import com.hrakaroo.glob.GlobPattern
import java.lang.RuntimeException

typealias OnScanCallback = (file: File2) -> Unit

/**
 * 文件差异计算器的基本类
 *
 * 间接匹配：仅出现在目录上，表示目录本身不需要更新，但目录存在有需要更新的文件
 * 直接匹配：表示当前文件需要更新，或者当前目录下的所有文件都需要更新
 *
 * @param local 要比较的本地文件
 * @param remote 要比较的远程文件
 * @param opt 可调节的参数
 */
abstract class DiffCalculatorBase(
    val local: File2,
    val remote: List<SimpleFileObject>,
    var opt: Options,
) {
    val base = local
    val result: Difference = Difference()


    /**
     * 将一个文件文件或者目录标记为旧文件
     */
    protected fun markAsOld(file: File2)
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
    protected fun markAsNew(node: SimpleFileObject, dir: File2)
    {
        if(node is SimpleDirectory)
        {
            result.newFolders += dir.relativizedBy(base)
            for (n in node.files)
                markAsNew(n, dir + n.name)
        } else if (node is SimpleFile){
            val rp = dir.relativizedBy(base)
            result.newFiles[rp] = Pair(node.length, node.modified)
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

        if(opt.patterns.isEmpty())
            return false

        var result = false
        for (reg in opt.patterns)
        {
            // 以@打头的就是正则表达式，反之是Glob表达式
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

    /**
     * 计算文件的哈希值
     */
    protected fun calculateHash(file: File2): String
    {
        return when (opt.hashAlgorithm) {
            HashAlgorithm.CRC32 -> HashUtils.crc32(file._file)
            HashAlgorithm.MD5 -> HashUtils.md5(file._file)
            HashAlgorithm.SHA1 -> HashUtils.sha1(file._file)
        }
    }

    /**
     * 对比文件差异
     */
    protected abstract fun compare(onScan: OnScanCallback?)

    operator fun invoke(onScan: OnScanCallback? =null): Difference
    {
        compare(onScan)
        return result
    }

    /**
     * 计算出来的文件差异结果
     */
    class Difference (
        val oldFolders: MutableList<String> = mutableListOf(),
        val oldFiles: MutableList<String> = mutableListOf(),
        val newFolders: MutableList<String> = mutableListOf(),
        val newFiles: MutableMap<String, Pair<Long, Long>> = mutableMapOf() // 文件名: <长度, 修改时间>
    ) {
        operator fun plusAssign(other: Difference)
        {
            oldFolders += other.oldFolders
            oldFiles += other.oldFiles
            newFolders += other.newFolders
            newFiles += other.newFiles
        }
    }

    /**
     * 工作模式的选项参数
     */
    data class Options(
        /**
         * 路径匹配样式，此选项决定了哪些文件要更新，哪些被忽略。
         * 此选项可以包含Glob表达式，也可以包含正则表达式
         */
        val patterns: List<String>,

        /**
         * 是否检测文件修改时间，而不是每次都完整检查文件校验，此选项可以节省时间
         */
        val checkModified: Boolean,


        /**
         * 使用的哈希算法
         */
        val hashAlgorithm: HashAlgorithm
    )
}