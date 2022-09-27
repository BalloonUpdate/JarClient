package com.github.balloonupdate.data

import com.github.balloonupdate.util.File2

/**
 * 代表一个文件下载任务
 */
data class DownloadTask(
    /**
     * 预期的文件长度
     */
    val lengthExpected: Long,

    /**
     * 预期的文件修改时间
     */
    val modified: Long,

    /**
     * 文件的下载URL们，多个URL之间为备用关系
     */
    val urls: List<String>,

    /**
     * 需要写出到的本地文件
     */
    val file: File2,

    /**
     * 下载时是否使用无缓存模式
     */
    val noCache: String?,
)