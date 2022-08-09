package com.github.balloonupdate.data

import com.github.balloonupdate.exception.ConfigFieldException

data class GlobalOptions (
    /**
     * 服务端index.json文件的URL，用来获取服务端的文件并计算差异
     */
    val server: String,

    /**
     * 更新完成后是否自动关闭窗口并退出程序
     */
    val autoExit: Boolean,

    /**
     * 更新的基本路径
     */
    val basePath: String,

    /**
     * 是否开启版本缓存功能
     */
    val versionCache: String,

    /**
     * 是否开启无缓存模式
     */
    val noCache: String?,

    /**
     * 是否开启文件修改时间检测
     */
    val checkModified: Boolean,

    /**
     * 是否开启不抛异常模式，以避免在更新失败时，不打断Minecraft游戏的启动
     */
    val noThrowing: Boolean,

    /**
     * 按键模式，仅在有文件需要被更新时显示下载窗口
     */
    val quietMode: Boolean,
) {
    companion object {
        @JvmStatic
        fun CreateFromMap(map: Map<String, Any>): GlobalOptions
        {
            return GlobalOptions(
                server = getOption<String>(map, "server") ?: throw ConfigFieldException("server"),
                autoExit = getOption<Boolean>(map, "auto-exit") ?: false,
                basePath = getOption<String>(map, "base-path") ?: "",
                versionCache = getOption<String>(map, "version-cache") ?: "",
                noCache = getOption<String>(map, "no-cache"),
                checkModified = getOption<Boolean>(map, "check-modified") ?: false,
                noThrowing = getOption<Boolean>(map, "no-throwing") ?: false,
                quietMode = getOption<Boolean>(map, "quiet-mode") ?: false,
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