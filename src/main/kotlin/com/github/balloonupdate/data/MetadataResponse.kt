package com.github.balloonupdate.data

data class MetadataResponse(
    val structureFileUrl: String,
    val assetsDirUrl: String,
    val commonMode: List<String>,
    val onceMode: List<String>,
    val hashAlgorithm: HashAlgorithm,
)