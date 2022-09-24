package com.github.balloonupdate.data

data class MetadataResponse(
    val updateUrl: String,
    val updateSource: String,
    val commonMode: List<String>,
    val onceMode: List<String>,
    val hashAlgorithm: String,
)