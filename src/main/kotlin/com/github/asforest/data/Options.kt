package com.github.asforest.data

import com.github.asforest.LittleClientMain
import com.github.asforest.exception.ConfigFileException

data class Options (
    val server: String,
    val autoExit: Boolean,
    val basePath: String,
    val versionCache: String,
    val noCache: String?,
    val modificationTimeCheck: Boolean
) {
    companion object {
        @JvmStatic
        fun CreateFromMap(map: Map<String, Any>): Options
        {
            val server = getOption<String>(map, "server") ?: throw ConfigFileException("配置文件中的server选项无效")
            val autoExit = getOption<Boolean>(map, "auto-exit") ?: false
            val basePath = getOption<String>(map, "base-path") ?: ""
            val versionCache = getOption<String>(map, "version-cache") ?: ""
            val noCache: String? = getOption<String>(map, "no-cache")
            val modificationTimeCheck = getOption<Boolean>(map, "modification-time-prioritized") ?: false

            return Options(
                server = server,
                autoExit = autoExit,
                basePath = basePath,
                versionCache = versionCache,
                noCache = noCache,
                modificationTimeCheck = modificationTimeCheck
            )
        }

        /**
         * 从配置文件里读取东西，并校验
         */
        inline fun <reified Type> getOption(config: Map<String, Any>, key: String): Type?
        {
            return if(key in config && config[key] != null && config[key] is Type) config[key] as Type else null
        }
    }
}