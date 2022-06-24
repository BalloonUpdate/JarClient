package com.github.balloonupdate

import com.github.balloonupdate.logging.ConsoleHandler
import com.github.balloonupdate.logging.FileHandler
import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.EnvUtil
import com.github.balloonupdate.util.HttpUtil
import com.github.balloonupdate.util.Utils
import com.github.balloonupdate.diff.DiffCalculatorBase
import com.github.balloonupdate.diff.CommonModeCalculator
import com.github.balloonupdate.diff.OnceModeCalculator
import com.github.balloonupdate.exception.UnableToDecodeException
import com.github.balloonupdate.patch.AndroidPatch
import org.json.JSONArray
import org.json.JSONException
import java.awt.Desktop
import java.lang.instrument.Instrumentation

class JavaAgentMain : ClientBase()
{
    fun run()
    {
        // 输出调试信息
        LogSys.openRangedTag("环境")
        LogSys.info("初始化...")
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
        val updateInfo: JSONArray
        try {
            updateInfo = JSONArray(rawData)
        } catch (e: JSONException) {
            throw UnableToDecodeException("Json无法解码(对应URL: ${indexResponse.updateUrl}):\n"+e.message)
        }

        // 读取安卓补丁内容
        val androidPatch = if (options.checkModified && options.androidPatch != null) AndroidPatch(progDir + options.androidPatch) else null
        if (androidPatch != null)
        {
            val loaded = androidPatch.load()
            if (loaded == null)
                LogSys.info("没有找到补丁数据")
            else
                LogSys.info("读取到 $loaded 条补丁数据")
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

        LogSys.info("正在计算文件差异...")
        // 计算文件差异
        val remoteFiles = unserializeFileStructure(updateInfo)
        var diff = DiffCalculatorBase.Difference()

        if(isVersionOutdate)
        {
            // 对比文件差异
            val targetDirectory = updateDir

            // 文件对比进度
            var scannedCount = 0

            val opt = DiffCalculatorBase.Options(
                patterns = indexResponse.commonMode,
                checkModified = options.checkModified,
                androidPatch = androidPatch,
            )

            // 开始文件对比过程
            LogSys.openRangedTag("普通对比")
            diff = CommonModeCalculator(targetDirectory, remoteFiles, opt)() {
                scannedCount += 1
//                print(".")
            }
//            println()

            LogSys.closeRangedTag()

            LogSys.openRangedTag("补全对比")
            diff += OnceModeCalculator(targetDirectory, remoteFiles, opt)()
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
            var downloadedCount = 0

            // 开始下载
            for ((relativePath, lm) in diff.newFiles)
            {
                val url = indexResponse.updateSource + relativePath
                val file = targetDirectory + relativePath

                val lengthExpected = lm.first
                val modified = lm.second

                LogSys.info("下载(${downloadedCount + 1}/${diff.newFiles.values.size}): ${file.name}")
                LogSys.debug("发起请求: ${url}, 写入文件: ${file.path}")

                HttpUtil.httpDownload(client, url, file, lengthExpected, options.noCache) { _, _, _ -> }
                file.file.setLastModified(modified)

                downloadedCount += 1
            }
        }

        // 更新版本缓存文件
        if(options.versionCache.isNotEmpty())
            versionFile.content = Utils.sha1(rawData)

        // 更新安卓补丁内容
        androidPatch?.update(updateDir, remoteFiles)

        // 显示更新小节
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
            if (Desktop.isDesktopSupported() && agentArgs != "windowless")
            {
                GraphicsMain.main(true)
                return
            }

            val am = JavaAgentMain()

            try {
                LogSys.addHandler(FileHandler(LogSys, progDir + "balloon_update.txt"))
                LogSys.addHandler(ConsoleHandler(LogSys, LogSys.LogLevel.INFO))
                LogSys.openRangedTag("更新助手")
                am.run()
            } catch (e: Throwable) {
                try {
                    LogSys.error(e.javaClass.name)
                    LogSys.error(e.stackTraceToString())
                } catch (e: Exception) {
                    println("------------------------")
                    println(e.javaClass.name)
                    println(e.stackTraceToString())
                }

                if (am.options.noThrowing)
                {
                    println("文件更新失败！但因为设置了no-throwing参数，游戏仍会继续运行！\n\n\n")
                } else {
                    throw e
                }
            } finally {
                LogSys.destory()
            }
        }
    }
}