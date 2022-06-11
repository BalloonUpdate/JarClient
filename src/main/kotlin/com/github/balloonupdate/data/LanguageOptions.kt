package com.github.balloonupdate.data

import com.github.balloonupdate.exception.ConfigFileException

data class LanguageOptions (
    val windowTitle: String,
    val windowTitleDownloading: String,
    val windowTitleSuffix: String,
    val connectingMessage: String,
    val fetchMetadata: String,
    val checkLocalFiles: String,
    val finishMessageTitleHasUpdate: String,
    val finishMessageTitleNoUpdate: String,
    val finishMessageContentHasUpdate: String,
    val finishMessageContentNoUpdate: String,
) {
    companion object {
        @JvmStatic
        fun CreateFromMap(map: Map<String, String>): LanguageOptions
        {
            return LanguageOptions(
                windowTitle = getLangField(map, "window-title"),
                windowTitleDownloading = getLangField(map, "window-title-downloading"),
                windowTitleSuffix = getLangField(map, "window-title-suffix"),
                connectingMessage = getLangField(map, "connecting-message"),
                fetchMetadata = getLangField(map, "fetch-metadata"),
                checkLocalFiles = getLangField(map, "check-local-files"),
                finishMessageTitleHasUpdate = getLangField(map, "finish-message-title-has-update"),
                finishMessageTitleNoUpdate = getLangField(map, "finish-message-title-no-update"),
                finishMessageContentHasUpdate = getLangField(map, "finish-message-content-has-update"),
                finishMessageContentNoUpdate = getLangField(map, "finish-message-content-no-update"),

            )
        }

        /**
         * 从配置文件里读取东西，并校验
         */
        fun getLangField(config: Map<String, Any>, key: String): String
        {
            return (if(key in config &&
                    config[key] != null &&
                    config[key] is String)
                        config[key] as String else null)
                ?: throw ConfigFileException("语言配置文件中的${key}选项无效")
        }
    }
}