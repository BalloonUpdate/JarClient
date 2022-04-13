package com.github.balloonupdate.data

import com.github.balloonupdate.exception.ConfigFileException

data class GlobalOptions (
    val server: String,
    val autoExit: Boolean,
    val basePath: String,
    val versionCache: String,
    val noCache: String?,
    val checkModified: Boolean,
    val androidPatch: String?,
    val noThrowing: Boolean
) {
    companion object {
        @JvmStatic
        fun CreateFromMap(map: Map<String, Any>): GlobalOptions
        {
            return GlobalOptions(
                server = getOption<String>(map, "server") ?: throw ConfigFileException("配置文件中的server选项无效"),
                autoExit = getOption<Boolean>(map, "auto-exit") ?: false,
                basePath = getOption<String>(map, "base-path") ?: "",
                versionCache = getOption<String>(map, "version-cache") ?: "",
                noCache = getOption<String>(map, "no-cache"),
                checkModified = getOption<Boolean>(map, "check-modified") ?: false,
                androidPatch = getOption<String>(map, "android-patch"),
                noThrowing = getOption<Boolean>(map, "no-throwing") ?: false,
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