package com.github.asforest.util

object FileUtil
{
    /**
     * 统计文件数量
     */
    @JvmStatic
    fun countFiles(directory: FileObj): Int
    {
        var count = 0
        for (f in directory.files)
            count += if(f.isFile) 1 else countFiles(f)
        return count
    }
}