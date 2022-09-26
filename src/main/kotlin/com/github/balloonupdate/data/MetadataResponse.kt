package com.github.balloonupdate.data

/**
 * 代表一个index.json文件的响应内容
 */
data class MetadataResponse(
    /**
     * 结构文件的URL们，多个URL直接为备用源的关系
     */
    val structureFileUrls: List<String>,

    /**
     * 资源目录的URL们，多个URL直接为备用源的关系
     */
    val assetsDirUrls: List<String>,

    /**
     * 普通更新模式下的更新规则
     */
    val commonMode: List<String>,

    /**
     * 补全更新模式下的更新规则
     */
    val onceMode: List<String>,

    /**
     * 服务端要求使用的哈希算法
     */
    val hashAlgorithm: HashAlgorithm,
)