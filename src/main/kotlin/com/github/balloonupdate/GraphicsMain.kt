@file:JvmName("LittleClientMain")
package com.github.balloonupdate

import com.github.balloonupdate.exception.BaseException
import com.github.balloonupdate.logging.ConsoleHandler
import com.github.balloonupdate.logging.FileHandler
import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.HttpUtil.httpDownload
import com.github.balloonupdate.util.HttpUtil.httpFetch
import com.github.balloonupdate.gui.MainWin
import com.github.balloonupdate.diff.DiffCalculatorBase
import com.github.balloonupdate.diff.CommonModeCalculator
import com.github.balloonupdate.diff.OnceModeCalculator
import com.github.balloonupdate.util.EnvUtil
import com.github.balloonupdate.util.Utils
import java.lang.Exception
import javax.swing.JOptionPane
import kotlin.system.exitProcess

class GraphicsMain : ClientBase()
{
    /**
     * 主窗口对象
     */
    val window = MainWin()

    fun run()
    {
        // 输出调试信息
        LogSys.openRangedTag("环境")
        LogSys.debug("更新目录: ${updateDir.path}")
        LogSys.debug("工作目录: ${workDir.path}")
        LogSys.debug("程序目录: ${if(EnvUtil.isPackaged) EnvUtil.jarFile.parent.path else "dev-mode"}")
        LogSys.debug("应用版本: $appVersion (${EnvUtil.gitCommit})")
        LogSys.closeRangedTag()

        // 初始化窗口
        window.titleTextSuffix = " $appVersion"
        window.titleText = "文件更新助手"
        window.stateText = "正在连接到更新服务器..."

        // 连接服务器获取主要更新信息
        val indexResponse = fetchIndexResponse(client, options.server, options.noCache)

        // 等待服务器返回最新文件结构数据
        window.stateText = "正在获取资源更新..."
        val rawData = httpFetch(client, indexResponse.updateUrl, options.noCache)
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

        // 计算文件差异
        var diff = DiffCalculatorBase.Difference()

        if(isVersionOutdate)
        {
            // 对比文件差异
            val targetDirectory = updateDir
            val remoteFiles = unserializeFileStructure(updateInfo)

            // 文件对比进度
            val fileCount = Utils.countFiles(targetDirectory)
            var scannedCount = 0

            val opt = DiffCalculatorBase.Options(
                patterns = indexResponse.commonMode,
                checkModified = options.checkModified,
                androidPatch = null,
            )

            // 开始文件对比过程
            LogSys.openRangedTag("普通对比")
            diff = CommonModeCalculator(targetDirectory, remoteFiles, opt)() {
                scannedCount += 1
                window.progress1text = "正在检查资源..."
                window.stateText = it.name
                window.progress1value = ((scannedCount/fileCount.toFloat())*1000).toInt()
            }
            window.progress1value = 0
            LogSys.closeRangedTag()

            LogSys.openRangedTag("补全对比")
            diff += OnceModeCalculator(targetDirectory, remoteFiles, opt)()
            LogSys.closeRangedTag()

            // 输出差异信息
            LogSys.openRangedTag("文件差异")
            LogSys.info("旧文件: ${diff.oldFiles.size}, 旧目录: ${diff.oldFolders.size}, 新文件: ${diff.newFiles.size}, 新目录: ${diff.newFolders.size}")
            diff.oldFiles.forEach { LogSys.info("旧文件: $it") }
            diff.oldFolders.forEach { LogSys.info("旧目录: $it") }
            diff.newFiles.forEach { LogSys.info("新文件: ${it.key}") }
            diff.newFolders.forEach { LogSys.info("新目录: $it") }
            LogSys.closeRangedTag()

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
                val modified = lm.second

                httpDownload(client, url, file, lengthExpected, options.noCache) { packageLength, received, total ->
                    totalBytesDownloaded += packageLength
                    val currentProgress = received / total.toFloat()*100
                    val totalProgress = totalBytesDownloaded / totalBytes.toFloat()*100

                    val currProgressInString = String.format("%.1f", currentProgress)
                    val totalProgressInString = String.format("%.1f", totalProgress)
                    
                    val timeEnd = System.currentTimeMillis()  // 获取当前（被回调时 / 区段结尾）时间戳

                    bytesDownloaded += packageLength    // 累加时间区段内的下载量
                    if (timeEnd - timeStart > rateUpdatePeriod) {
                        downloadSpeedRaw = bytesDownloaded.toDouble()/(timeEnd - timeStart)
                        timeStart = timeEnd    // 重新设定区段开始时间戳
                        bytesDownloaded = 0 // 重置区间下载量
                    }

                    window.stateText = "正在下载: ${file.name}" // 可能是画蛇添足的修改，看情况是否合并吧
                    window.progress1value = (currentProgress*10).toInt()
                    window.progress2value = (totalProgress*10).toInt()
                    // 转换并添加 KB/MB 单位（应该没有人下载速度 <1KB/s || >1GB/s 吧），放在这里是因为 stateText 已经显示文件名了
                    window.progress1text = downloadSpeedRaw.run { if (this > 1024) "${(this/1024).toInt()}MB/s" else "${this.toInt()}KB/s"} + "   -  $currProgressInString%"
                    window.progress2text = "$totalProgressInString%  -  ${downloadedCount + 1}/${diff.newFiles.values.size}"
                    window.titleText = "($totalProgressInString%) 文件更新助手"
                }
                file.file.setLastModified(modified)

                downloadedCount += 1
            }
        }

        if(options.versionCache.isNotEmpty())
            versionFile.content = Utils.sha1(rawData)

        // 程序结束
        if(!options.autoExit)
        {
            val news = diff.newFiles
            val hasUpdate = news.isEmpty()
            val title = if(hasUpdate) "检查更新完毕" else "文件更新完毕"
            val content = if(hasUpdate) "所有文件已是最新!" else "成功更新${news.size}个文件!"
            JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
        }

        window.close()
    }

    object DialogUtil
    {
        @JvmStatic
        fun confirm(title: String, content: String): Boolean
            = JOptionPane.showConfirmDialog(null, content, title, JOptionPane.YES_NO_OPTION) == 0

        @JvmStatic
        fun error(title: String, content: String)
            = JOptionPane.showMessageDialog(null, content, title, JOptionPane.ERROR_MESSAGE)

        @JvmStatic
        fun info(title: String, content: String)
            = JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
    }

    companion object {
        lateinit var ins: GraphicsMain

        /**
         * 入口程序
         */
        @JvmStatic
        fun main(args: Array<String>)
        {
            main(false)
        }

        @JvmStatic
        fun main(isJavaAgentMode: Boolean)
        {
            try {
                LogSys.addHandler(FileHandler(LogSys, progDir + "balloon_update.log"))
                LogSys.addHandler(ConsoleHandler(LogSys, LogSys.LogLevel.DEBUG))

                ins = GraphicsMain()
                ins.run()
            } catch (e: Throwable) {
                try {
                    LogSys.error(e.javaClass.name)
                    LogSys.error(e.stackTraceToString())
                } catch (e: Exception) {
                    System.err.println("------------------------")
                    System.err.println(e.javaClass.name)
                    System.err.println(e.stackTraceToString())
                }

                if(e !is BaseException)
                {
                    val content = "${e.javaClass.name}\n${e.message}\n\n点击\"是\"显示错误详情，点击\"否\"退出程序"

                    if(DialogUtil.confirm("发生错误 ${ins.appVersion}", content))
                        DialogUtil.error("调用堆栈", e.stackTraceToString())
                } else {
                    DialogUtil.error(e.getDisplayName() + " ${ins.appVersion}", e.message ?: "")
                }

                LogSys.destory()

                if (!isJavaAgentMode)
                    exitProcess(1)
            }
        }
    }
}
