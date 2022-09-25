package com.github.balloonupdate.data

import com.github.balloonupdate.util.FileObject

data class DownloadTask(
    val lengthExpected: Long,
    val modified: Long,
    val url: String,
    val file: FileObject,
    val noCache: String?,
)