@file:JvmName("LittleClientMain")
package com.github.balloonupdate

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme
import com.github.balloonupdate.data.LanguageOptions
import com.github.balloonupdate.diff.CommonModeCalculator
import com.github.balloonupdate.diff.DiffCalculatorBase
import com.github.balloonupdate.diff.OnceModeCalculator
import com.github.balloonupdate.exception.BaseException
import com.github.balloonupdate.exception.UnableToDecodeException
import com.github.balloonupdate.gui.MainWin
import com.github.balloonupdate.logging.ConsoleHandler
import com.github.balloonupdate.logging.FileHandler
import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.EnvUtil
import com.github.balloonupdate.util.HttpUtil.httpDownload
import com.github.balloonupdate.util.HttpUtil.httpFetch
import com.github.balloonupdate.util.Utils
import org.json.JSONArray
import org.json.JSONException
import java.awt.Desktop
import java.io.InterruptedIOException
import java.nio.channels.ClosedByInterruptException
import javax.swing.JFrame
import javax.swing.JOptionPane

class GraphicsMain : ClientBase()
{
    /**
     * 主窗口对象
     */
    val window = MainWin()

    /**
     * 语言配置文件对象
     */
    val langs = LanguageOptions.CreateFromMap(readLangs())

    fun run()
    {
        // 输出调试信息
        LogSys.openRangedTag("环境")
        LogSys.debug("更新目录: ${updateDir.path}")
        LogSys.debug("工作目录: ${workDir.path}")
        LogSys.debug("程序目录: ${if(EnvUtil.isPackaged) EnvUtil.jarFile.parent.path else "dev-mode"}")
        LogSys.debug("应用版本: $appVersion (${EnvUtil.gitCommit})")
        LogSys.closeRangedTag()

        // 将更新任务单独分进一个线程执行，方便随时打断线程
        var ex: Throwable? = null
        val task = Thread {
            try {
                updatingTask()
            } catch (e: SecurityException) {
                ex = e
            } catch (e: InterruptedException) {
                ex = e
            } catch (e: ClosedByInterruptException) {
                ex = e
            } catch (e: InterruptedIOException) {
                ex = e
            } catch (e: Exception) {
                ex = e
            }
        }

        if (!options.quietMode)
            window.show()

        // 初始化窗口
        window.titleTextSuffix = langs.windowTitleSuffix.replace("{APP_VERSION}", "$appVersion")
        window.titleText = langs.windowTitle
        window.stateText = langs.connectingMessage

        window.window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        window.onWindowClosing.once { win ->
            win.hide()
            if (task.isAlive)
                task.interrupt()
        }

        // 启动并等待更新线程
        task.start()
        task.join()

        // 转发更新线程里的异常
        if (ex != null)
        {
            val e = ex!!

            if (
                e !is SecurityException &&
                e !is InterruptedException &&
                e !is InterruptedIOException &&
                e !is ClosedByInterruptException
            )
                throw ex!!
        }
    }

    fun updatingTask()
    {
        // 连接服务器获取主要更新信息
        val indexResponse = fetchIndexResponse(client, options.server, options.noCache)

        // 等待服务器返回最新文件结构数据
        window.stateText = langs.fetchMetadata
        val rawData = httpFetch(client, indexResponse.updateUrl, options.noCache)
        val updateInfo: JSONArray
        try {
            updateInfo = JSONArray(rawData)
        } catch (e: JSONException) {
            throw UnableToDecodeException("Json无法解码(对应URL: ${indexResponse.updateUrl}):\n"+e.message)
        }

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

        // 下载过的文件数量
        var downloadFileCount = 0

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

            val commonOpt = DiffCalculatorBase.Options(
                patterns = indexResponse.commonMode,
                checkModified = options.checkModified,
                androidPatch = null,
            )

            val onceOpt = DiffCalculatorBase.Options(
                patterns = indexResponse.onceMode,
                checkModified = options.checkModified,
                androidPatch = null,
            )

            // 开始文件对比过程
            LogSys.openRangedTag("普通对比")
            diff = CommonModeCalculator(targetDirectory, remoteFiles, commonOpt)() {
                scannedCount += 1
                window.progress1text = langs.checkLocalFiles
                window.stateText = it.name
                window.progress1value = ((scannedCount/fileCount.toFloat())*1000).toInt()
            }
            window.progress1value = 0
            LogSys.closeRangedTag()

            LogSys.openRangedTag("补全对比")
            diff += OnceModeCalculator(targetDirectory, remoteFiles, onceOpt)()
            LogSys.closeRangedTag()

            // 输出差异信息
            LogSys.openRangedTag("文件差异")
            LogSys.info("旧文件: ${diff.oldFiles.size}, 旧目录: ${diff.oldFolders.size}, 新文件: ${diff.newFiles.size}, 新目录: ${diff.newFolders.size}")
            diff.oldFiles.forEach { LogSys.info("旧文件: $it") }
            diff.oldFolders.forEach { LogSys.info("旧目录: $it") }
            diff.newFiles.forEach { LogSys.info("新文件: ${it.key}") }
            diff.newFolders.forEach { LogSys.info("新目录: $it") }
            LogSys.closeRangedTag()

            // 延迟打开窗口
            if (options.quietMode && diff.newFiles.isNotEmpty())
                window.show()

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
                    window.titleText = langs.windowTitleDownloading.replace("{PERCENT}", totalProgressInString)
                }
                file.file.setLastModified(modified)

                downloadedCount += 1
                downloadFileCount += 1
            }
        }

        if(options.versionCache.isNotEmpty())
            versionFile.content = Utils.sha1(rawData)

        // 程序结束
        if(!(options.quietMode && downloadFileCount == 0) && !options.autoExit)
        {
            val news = diff.newFiles
            val hasUpdate = news.isEmpty()
            val title = if(hasUpdate) langs.finishMessageTitleHasUpdate else langs.finishMessageTitleNoUpdate
            val content = if(hasUpdate) langs.finishMessageContentHasUpdate else langs.finishMessageContentNoUpdate.replace("{COUNT}", "${news.size}")
            JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
        }

        window.destroy()
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

                if (!isJavaAgentMode && Desktop.isDesktopSupported())
                {
                    //设置 GUI 主题
                    FlatOneDarkIJTheme.setup()
                }

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

                val title = "发生错误 ${ins.appVersion}"
                var content = if(e is BaseException) e.getDisplayName() else e.javaClass.name
                content += "\n" + Utils.stringBreak((e.message ?: "没有更多异常信息"), 80) + "\n\n"
                content += if (isJavaAgentMode) "点击\"是\"显示错误详情，" else "点击\"是\"显示错误详情，"
                content += if (isJavaAgentMode) "点击\"否\"继续启动Minecraft" else "点击\"否\"退出程序"

                val choice = DialogUtil.confirm(title, content)

                if (isJavaAgentMode)
                {
                    if (choice)
                    {
                        DialogUtil.error("调用堆栈", e.stackTraceToString())
                        throw e
                    }
                } else {
                    if (choice)
                        DialogUtil.error("调用堆栈", e.stackTraceToString())
                    throw e
                }
            } finally {
                try {
                    ins.window.destroy()
                } catch (_: UninitializedPropertyAccessException) { }
                LogSys.destory()
            }
        }
    }
}
