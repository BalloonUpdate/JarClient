package com.github.asforest

import com.github.asforest.file.FileObj
import com.github.asforest.logging.ConsoleHandler
import com.github.asforest.logging.FileHandler
import com.github.asforest.logging.LogSys
import com.github.asforest.util.EnvUtil
import com.github.asforest.util.HttpUtil
import com.github.asforest.util.Utils
import com.github.asforest.workmode.AbstractMode
import com.github.asforest.workmode.CommonMode
import com.github.asforest.workmode.OnceMode
import java.awt.Desktop
import java.lang.instrument.Instrumentation

class JavaAgentMain : ClientBase()
{
    fun run()
    {
        // 输出调试信息
        LogSys.openRangedTag("环境")
        LogSys.info("更新目录: ${updateDir.path}")
        LogSys.info("工作目录: ${workDir.path}")
        LogSys.info("程序目录: ${if(EnvUtil.isPackaged) EnvUtil.jarFile.parent.path else "dev-mode"}")
        LogSys.info("应用版本: $appVersion (${EnvUtil.gitCommit})")
        LogSys.closeRangedTag()

        LogSys.info("正在连接到更新服务器...")
        LogSys.debug("请求的URL: ${options.server}")
        val indexResponse = fetchIndexResponse(client, options.server, options.noCache)

        LogSys.info("等待服务器返回最新文件结构数据")
        LogSys.debug("请求的URL: ${indexResponse.updateUrl}")
        val rawData = HttpUtil.httpFetch(client, indexResponse.updateUrl, options.noCache)
        val updateInfo = parseAsJsonArray(rawData)

        // 使用版本缓存
        var isVersionOutdate = true
        val versionFile = updateDir + options.versionCache

        if(options.versionCache.isNotEmpty())
        {
            versionFile.makeParentDirs()
            isVersionOutdate = if(versionFile.exists) {
                val versionCached = versionFile.content
                val versionRecieved = Utils.sha1(rawData)
                versionCached != versionRecieved
            } else {
                true
            }
        }

        LogSys.info("正在计算文件差异...")
        // 计算文件差异
        var diff = AbstractMode.Difference()

        if(isVersionOutdate)
        {
            // 对比文件差异
            val targetDirectory = updateDir
            val remoteFiles = unserializeFileStructure(updateInfo)

            // 文件对比进度
            val fileCount = Utils.countFiles(targetDirectory)
            var scannedCount = 0

            // 开始文件对比过程
            LogSys.openRangedTag("普通对比")
            diff = CommonMode(indexResponse.common_mode.asList(), targetDirectory, remoteFiles, options.modificationTimeCheck)() {
                scannedCount += 1
                print(".")
            }
            println()

            LogSys.closeRangedTag()

            LogSys.openRangedTag("补全对比")
            diff += OnceMode(indexResponse.once_mode.asList(), targetDirectory, remoteFiles, options.modificationTimeCheck)()
            LogSys.closeRangedTag()

            // 输出差异信息
            LogSys.info("文件差异计算完成，旧文件: ${diff.oldFiles.size}, 旧目录: ${diff.oldFolders.size}, 新文件: ${diff.newFiles.size}, 新目录: ${diff.newFolders.size}")
            diff.oldFiles.forEach { LogSys.debug("旧文件: $it") }
            diff.oldFolders.forEach { LogSys.debug("旧目录: $it") }
            diff.newFiles.forEach { LogSys.debug("新文件: ${it.key}") }
            diff.newFolders.forEach { LogSys.debug("新目录: $it") }

            // 删除旧文件和旧目录，还有创建新目录
            diff.oldFiles.map { (targetDirectory + it) }.forEach { it.delete() }
            diff.oldFolders.map { (targetDirectory + it) }.forEach { it.delete() }
            diff.newFolders.map { (targetDirectory + it) }.forEach { it.mkdirs() }

            // 下载新文件
            var totalBytes: Long = 0
            var totalBytesDownloaded: Long = 0
            var downloadedCount = 0
            diff.newFiles.values.forEach { totalBytes += it.first }

            // 开始下载
            for ((relativePath, lm) in diff.newFiles)
            {
                val url = indexResponse.updateSource + relativePath
                val file = targetDirectory + relativePath
                val rateUpdatePeriod = 1000 // 一秒更新一次下载速度，保证准确（区间太小易导致下载速度上下飘忽）

                // 获取下载开始（首个区段开始）时间戳，并且第一次速度采样从第100ms就开始，而非1s，避免多个小文件下载时速度一直显示为0
                var timeStart = System.currentTimeMillis() - (rateUpdatePeriod - 100)
                var downloadSpeedRaw = 0.0  // 初始化下载速度为 0
                var bytesDownloaded = 0L    // 初始化时间区段内下载的大小为 0

                val lengthExpected = lm.first
                val midifed = lm.second

                LogSys.info("正在下载: ${file.name} (${downloadedCount + 1}/${diff.newFiles.values.size})")
                LogSys.debug("发起请求: ${url}, 写入文件: ${file.path}")

                HttpUtil.httpDownload(
                    client,
                    url,
                    file,
                    lengthExpected,
                    midifed,
                    options.noCache
                ) { packageLength, received, total -> }

                downloadedCount += 1
            }
        }

        if(options.versionCache.isNotEmpty())
            versionFile.content = Utils.sha1(rawData)

        val totalUpdated = diff.newFiles.size + diff.oldFiles.size
        if (totalUpdated == 0)
            LogSys.info("所有文件已是最新！")
        else
            LogSys.info("成功更新${totalUpdated}个文件!")
        LogSys.info("程序结束，继续启动Minecraft！\n\n\n")
    }

    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, ins: Instrumentation)
        {
            if (Desktop.isDesktopSupported())
            {
                GraphicsMain.main(arrayOf())
                return
            }

            try {
                val logFile = if (EnvUtil.isPackaged) EnvUtil.jarFile.parent + "updater.log" else FileObj(System.getProperty("user.dir")) + "updater.log"
                val am = JavaAgentMain()
                LogSys.addHandler(FileHandler(LogSys, logFile))
                LogSys.addHandler(ConsoleHandler(LogSys, LogSys.LogLevel.INFO))
                LogSys.openRangedTag("更新助手")
                am.run()
            } catch (e: Throwable) {
                try {
                    LogSys.error(e.javaClass.name)
                    LogSys.error(e.stackTraceToString())
                } catch (e: Exception) {
                    System.err.println("------------------------")
                    System.err.println(e.javaClass.name)
                    System.err.println(e.stackTraceToString())
                }

                throw e
            }
        }
    }
}