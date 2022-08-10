package com.github.balloonupdate

import com.github.balloonupdate.data.*
import com.github.balloonupdate.diff.CommonModeCalculator
import com.github.balloonupdate.diff.DiffCalculatorBase
import com.github.balloonupdate.diff.OnceModeCalculator
import com.github.balloonupdate.exception.ConfigFileNotFoundException
import com.github.balloonupdate.exception.FailedToParsingException
import com.github.balloonupdate.exception.UpdateDirNotFoundException
import com.github.balloonupdate.gui.NewWindow
import com.github.balloonupdate.localization.LangNodes
import com.github.balloonupdate.localization.Localization
import com.github.balloonupdate.logging.ConsoleHandler
import com.github.balloonupdate.logging.FileHandler
import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.*
import com.github.balloonupdate.util.Utils.convertBytes
import com.github.kasuminova.Downloader.SetupSwing
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.awt.Desktop
import java.io.File
import java.io.InterruptedIOException
import java.lang.instrument.Instrumentation
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import javax.swing.JOptionPane

class Main
{
    /**
     * 更新助手主逻辑
     * @param startsWithGraphicsMode 是否以图形模式启动（桌面环境通常以图形模式启动，安卓环境通常不以图形模式启动）
     * @param startsFromJavaAgent 是否是从JavaAgent参数启动，还是双击独立启动（java -jar xx.jar也属于独立启动）
     */
    fun run(startsWithGraphicsMode: Boolean, startsFromJavaAgent: Boolean)
    {
        try {
            // 设置UI主题
            if (startsWithGraphicsMode)
                SetupSwing.init()

            val workDir = getWorkDirectory()
            val progDir = getProgramDirectory(workDir)
            val options = GlobalOptions.CreateFromMap(readConfig(progDir + "config.yml"))
            val updateDir = getUpdateDirectory(workDir, options)

            // 初始化日志系统
            LogSys.addHandler(FileHandler(LogSys, progDir + (if (startsWithGraphicsMode) "balloon_update.log" else "balloon_update.txt")))
            LogSys.addHandler(ConsoleHandler(LogSys, if (startsWithGraphicsMode) LogSys.LogLevel.DEBUG else LogSys.LogLevel.INFO))
            if (startsFromJavaAgent)
                LogSys.openRangedTag("BalloonUpdate")

            LogSys.info("GraphicsMode:         $startsWithGraphicsMode")
            LogSys.info("FromJavaAgent:        $startsFromJavaAgent")

            Localization.init(readLangs())

            // 初始化UI
            val window = if (startsWithGraphicsMode) NewWindow() else null
//            val window: MainWin? = null

            // 将更新任务单独分进一个线程执行，方便随时打断线程
            var ex: Throwable? = null
            val task = Thread { task(window, options, workDir, updateDir) }
            task.isDaemon = true
            task.setUncaughtExceptionHandler { _, e -> ex = e }

            if (!options.quietMode)
                window?.show()

            window?.titleTextSuffix = Localization[LangNodes.window_title_suffix, "APP_VERSION", EnvUtil.version]
            window?.titleText = Localization[LangNodes.window_title]
            window?.statusBarText = Localization[LangNodes.connecting_message]
            window?.onWindowClosing?.once { win ->
                win.hide()
                if (task.isAlive)
                    task.interrupt()
            }

            task.start()
            task.join()

            window?.destroy()

            // 处理工作线程里的异常
            if (ex != null)
            {
                if (//            ex !is SecurityException &&
                    ex !is InterruptedException &&
                    ex !is InterruptedIOException &&
                    ex !is ClosedByInterruptException)
                {
                    try {
                        LogSys.error(ex!!.javaClass.name)
                        LogSys.error(ex!!.stackTraceToString())
                    } catch (e: Exception) {
                        println("------------------------")
                        println(e.javaClass.name)
                        println(e.stackTraceToString())
                    }

                    if (startsWithGraphicsMode)
                    {
                        val errMessage = Utils.stringBreak(ex!!.message ?: "<No Exception Message>", 80)
                        val title = "Error occurred ${EnvUtil.version}"
                        var content = errMessage + "\n"
                        content += if (startsFromJavaAgent) "点击\"是\"显示错误详情并崩溃Minecraft，" else "点击\"是\"显示错误详情并退出，"
                        content += if (startsFromJavaAgent) "点击\"否\"继续启动Minecraft" else "点击\"否\"直接退出程序"
                        val choice = DialogUtil.confirm(title, content)
                        if (startsFromJavaAgent)
                        {
                            if (choice)
                            {
                                DialogUtil.error("Callstack", ex!!.stackTraceToString())
                                throw ex!!
                            }
                        } else {
                            if (choice)
                                DialogUtil.error("Callstack", ex!!.stackTraceToString())
                            throw ex!!
                        }
                    } else {
                        if (options.noThrowing)
                            println("文件更新失败！但因为设置了no-throwing参数，游戏仍会继续运行！\n\n\n")
                        else
                            throw ex!!
                    }
                } else {
                    LogSys.info("updating thread interrupted by user")
                }
            }
        } catch (e: UpdateDirNotFoundException) {
            if (startsWithGraphicsMode)
                DialogUtil.error("", e.message ?: "<No Exception Message>")
        } catch (e: ConfigFileNotFoundException) {
            if (startsWithGraphicsMode)
                DialogUtil.error("", e.message ?: "<No Exception Message>")
        } catch (e: FailedToParsingException) {
            if (startsWithGraphicsMode)
                DialogUtil.error("", e.message ?: "<No Exception Message>")
        }
    }

    /**
     * 更新助手工作线程
     */
    fun task(window: NewWindow?, options: GlobalOptions, workDir: FileObject, updateDir: FileObject)
    {
        LogSys.info("updating directory:   ${updateDir.path}")
        LogSys.info("working directory:    ${workDir.path}")
        LogSys.info("executable directory: ${if(EnvUtil.isPackaged) EnvUtil.jarFile.parent.path else "dev-mode"}")
        LogSys.info("application version:  ${EnvUtil.version} (${EnvUtil.gitCommit})")

        if (!options.quietMode)
            window?.show()

        val okClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).build()

        // 从服务器获取元信息
        val metaResponse = requestIndex(okClient, options.server, options.noCache) // 错误处理

        // 更新UI
        window?.statusBarText = Localization[LangNodes.fetch_metadata]

        // 获取结构数据
        val rawData = HttpUtil.httpFetch(okClient, metaResponse.updateUrl, options.noCache)
        val remoteFiles: List<SimpleFileObject>
        try {
            remoteFiles = unserializeFileStructure(JSONArray(rawData))
        } catch (e: JSONException) {
            throw FailedToParsingException("结构文件请求", "json", "${metaResponse.updateUrl}\n${e.message}")
        }

        // 使用版本缓存
        var isVersionOutdate = true
        val versionFile = updateDir + options.versionCache

        if(options.versionCache.isNotEmpty())
        {
            versionFile.makeParentDirs()
            isVersionOutdate = if(!versionFile.exists) true else {
                val versionCached = versionFile.content
                val versionRecieved = Utils.sha1(rawData)
                versionCached != versionRecieved
            }
        }

        // 计算文件差异
        LogSys.info("正在计算文件差异...")
        window?.statusBarText = "正在计算文件差异..."

        var diff = DiffCalculatorBase.Difference()

        if(isVersionOutdate)
        {
            var scannedCount = 0
            val totalFileCount = Utils.countFiles(updateDir)

            val commonOpt = DiffCalculatorBase.Options(
                patterns = metaResponse.commonMode,
                checkModified = options.checkModified,
            )

            val onceOpt = DiffCalculatorBase.Options(
                patterns = metaResponse.onceMode,
                checkModified = options.checkModified,
            )

            // calculate the file-differences between the local and the remote
            // use common-mode
            LogSys.openRangedTag("CommonMode")
            diff = CommonModeCalculator(updateDir, remoteFiles, commonOpt)() {
                scannedCount += 1
                window?.statusBarProgressText = it.name
                window?.statusBarText = Localization[LangNodes.check_local_files]
                window?.statusBarProgressValue = ((scannedCount / totalFileCount.toFloat()) * 1000).toInt()
            }
            LogSys.closeRangedTag()

            // use once-mode
            LogSys.openRangedTag("OnceMode  ")
            diff += OnceModeCalculator(updateDir, remoteFiles, onceOpt)()
            LogSys.closeRangedTag()

            window?.statusBarText = ""

            // 输出差异信息
            LogSys.info("文件差异计算完成，旧文件: ${diff.oldFiles.size}, 旧目录: ${diff.oldFolders.size}, 新文件: ${diff.newFiles.size}, 新目录: ${diff.newFolders.size}")
            diff.oldFiles.forEach { LogSys.debug("旧文件: $it") }
            diff.oldFolders.forEach { LogSys.debug("旧目录: $it") }
            diff.newFiles.forEach { LogSys.debug("新文件: ${it.key}") }
            diff.newFolders.forEach { LogSys.debug("新目录: $it") }

            // 删除旧文件和旧目录，还有创建新目录
            diff.oldFiles.map { (updateDir + it) }.forEach { it.delete() }
            diff.oldFolders.map { (updateDir + it) }.forEach { it.delete() }
            diff.newFolders.map { (updateDir + it) }.forEach { it.mkdirs() }

            // 延迟打开窗口
            if (window != null && options.quietMode && diff.newFiles.isNotEmpty())
                window.show()

            if (diff.newFiles.isNotEmpty())
                LogSys.info("开始下载文件...")

            // 下载新文件
            var totalBytesDownloaded: Long = 0
            var totalBytes: Long = 0
            diff.newFiles.values.forEach { totalBytes += it.first } // calculate the total bytes to be downloaded

            // 生成下载任务
            val tasks = diff.newFiles.map { (relativePath, lm) ->
                val lengthExpected = lm.first
                val modified = lm.second
                val url = metaResponse.updateSource + relativePath
                val file = updateDir + relativePath
                DownloadTask(lengthExpected, modified, url, file, options.noCache)
            }.toMutableList()

            val lock = Any()
            var committedCount = 0
            var downloadedCount = 0
            val samplers = mutableListOf<SpeedSampler>()

            // 单个线程的下载逻辑
            fun download(task: DownloadTask, taskRow: NewWindow.TaskRow?, threadIndex: Int)
            {
                val file = task.file
                val url = task.url
                val lengthExpected = task.lengthExpected
                val modified = task.modified

                val sampler = SpeedSampler(1000, 100)
                synchronized(lock) {
                    samplers += sampler

                    committedCount += 1
                    LogSys.debug("request($committedCount/${diff.newFiles.values.size}): ${url}, write to: ${file.path}")
                }


                HttpUtil.httpDownload(okClient, url, file, lengthExpected, options.noCache) { packageLength, received, total ->
                    if (taskRow == null)
                        return@httpDownload

                    totalBytesDownloaded += packageLength
                    val currentProgress = received / total.toFloat() * 100
                    val totalProgress = totalBytesDownloaded / totalBytes.toFloat() * 100

                    sampler.sample(packageLength)
                    val speed = sampler.speed()

                    val currProgressInString = String.format("%.1f", currentProgress)
                    val totalProgressInString = String.format("%.1f", totalProgress)

                    taskRow.borderText = file.name
                    taskRow.progressBarValue = (currentProgress * 10).toInt()
                    taskRow.labelText = convertBytes(speed) + "/s   -  $currProgressInString%"
                    taskRow.progressBarLabel = "${convertBytes(received)} / ${convertBytes(total)}"
                    window!!.statusBarProgressValue = (totalProgress * 10).toInt()
                    window.statusBarProgressText = "$totalProgressInString%  -  ${downloadedCount}/${diff.newFiles.values.size}"
                    window.statusBarText = convertBytes(samplers.sumOf { it.speed() }) + "/s"
                    window.titleText = Localization[LangNodes.window_title_downloading, "PERCENT", totalProgressInString]
                }

                file.file.setLastModified(modified)

                synchronized(lock) {
                    if (window == null)
                        LogSys.info("downloaded($downloadedCount/${diff.newFiles.values.size}): ${file.name}")

                    downloadedCount += 1
                    samplers -= sampler
                }
            }

            // 启动工作线程
            val lock2 = Any()
            val threads = if (options.downloadThreads <= 0) Runtime.getRuntime().availableProcessors() * 2 else options.downloadThreads
            val windowTaskRows = mutableListOf<NewWindow.TaskRow>()
            val workers = mutableListOf<Thread>()
            var ex: Throwable? = null
            val mainThread = Thread.currentThread()
            for (i in 0 until threads)
            {
                workers += Thread {
                    val taskRow = window?.createTaskRow()?.also { windowTaskRows.add(it) }
                    while (synchronized(lock2) { tasks.isNotEmpty() })
                    {
                        val task: DownloadTask
                        synchronized(lock2){ task = tasks.removeFirst() }
                        try {
                            download(task, taskRow, i)
                        } catch (_: InterruptedIOException) { break }
                          catch (_: InterruptedException) { break }
                    }
                    window?.destroyTaskRow(taskRow!!)
                }.apply {
                    isDaemon = true
                    setUncaughtExceptionHandler { _, e ->
                        ex = e
                        mainThread.interrupt()
                    }
                }
            }

            // 等待所有线程完成
            try {
                for (worker in workers)
                    worker.start()
                for (worker in workers)
                    worker.join()
            } catch (e: InterruptedException) {
                for (worker in workers)
                    worker.interrupt()
                throw ex ?: e
            }
        }

        // 更新版本缓存文件
        if(options.versionCache.isNotEmpty())
            versionFile.content = Utils.sha1(rawData)

        // 显示更新小结
        if(window != null)
        {
            if(!(options.quietMode && diff.newFiles.isEmpty()) && !options.autoExit)
            {
                val news = diff.newFiles
                val hasUpdate = news.isNotEmpty()
                val title = if(hasUpdate) Localization[LangNodes.finish_message_title_has_update] else Localization[LangNodes.finish_message_title_no_update]
                val content = if(hasUpdate) Localization[LangNodes.finish_message_content_has_update, "COUNT", "${news.size}"] else Localization[LangNodes.finish_message_content_no_update]
                JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
            }
        } else {
            val totalUpdated = diff.newFiles.size + diff.oldFiles.size
            if (totalUpdated == 0)
                LogSys.info("所有文件已是最新！")
            else
                LogSys.info("成功更新${totalUpdated}个文件!")
            LogSys.info("程序结束，继续启动Minecraft！\n\n\n")
        }
    }

    /**
     * 将服务器返回的文件结构信息反序列化成SimpleFileObject对象便于使用
     */
    fun unserializeFileStructure(raw: JSONArray): List<SimpleFileObject>
    {
        val res = ArrayList<SimpleFileObject>()
        for (ff in raw)
        {
            val f = ff as JSONObject
            val name = f["name"] as String
            if(f.has("children"))
            {
                val files = f["children"] as JSONArray
                res += SimpleDirectory(name, unserializeFileStructure(files))
            } else {
                val length = Utils.parseAsLong(f["length"])
                val hash = f["hash"] as String
                val modified = Utils.parseAsLong(f["modified"] ?: -1)
                res += SimpleFile(name, length, hash, modified * 1000) // 服务端返回的是秒，这里需要转换成毫秒
            }
        }
        return res
    }

    /**
     * 发起http请求获取index.json的内容并解析
     */
    fun requestIndex(client: OkHttpClient, url: String, noCache: String?): MetadataResponse
    {
        val baseurl = url.substring(0, url.lastIndexOf('/') + 1)
        val response = HttpUtil.httpFetch(client, url, noCache)
        val data: JSONObject
        try {
            data = JSONObject(response)
        } catch (e: JSONException) {
            throw FailedToParsingException("元数据文件请求", "json", "$url\n${e.message}")
        }
        val update = data["update"] as? String ?: "res"

        fun findSource(text: String, def: String): String
        {
            if(text.indexOf('?') != -1)
            {
                val paramStr = text.split('?')
                if(paramStr[1] != "")
                {
                    for (it in paramStr[1].split("&"))
                    {
                        val pp = it.split("=")
                        if(pp.size == 2 && pp[0] == "source" && pp[1] != "")
                            return pp[1]
                    }
                }
                return paramStr[0]
            }
            return def
        }

        return MetadataResponse().apply {
            commonMode = (data["common_mode"] as JSONArray).map { it as String }
            onceMode = (data["once_mode"]  as JSONArray).map { it as String }
            updateUrl = baseurl + if (update.indexOf("?") != -1) update else "$update.json"
            updateSource = baseurl + findSource(update, update) + "/"
        }
    }

    /**
     * 向上搜索，直到有一个父目录包含.minecraft目录，然后返回这个父目录。最大搜索7层目录
     * @param basedir 从哪个目录开始向上搜索
     * @return 包含.minecraft目录的父目录。如果找不到则返回Null
     */
    fun searchDotMinecraft(basedir: FileObject): FileObject?
    {
        try {
            if(basedir.contains(".minecraft"))
                return basedir
            if(basedir.parent.contains(".minecraft"))
                return basedir.parent
            if(basedir.parent.parent.contains(".minecraft"))
                return basedir.parent.parent
            if(basedir.parent.parent.parent.contains(".minecraft"))
                return basedir.parent.parent.parent
            if(basedir.parent.parent.parent.parent.contains(".minecraft"))
                return basedir.parent.parent.parent.parent
            if(basedir.parent.parent.parent.parent.parent.contains(".minecraft"))
                return basedir.parent.parent.parent.parent.parent
            if(basedir.parent.parent.parent.parent.parent.parent.contains(".minecraft"))
                return basedir.parent.parent.parent.parent.parent.parent
        } catch (e: NullPointerException) {
            return null
        }
        return null
    }

    /**
     * 从外部/内部读取配置文件并将内容返回（当外部不可用时会从内部读取）
     * @param externalConfigFile 外部配置文件
     * @return 解码后的配置文件对象
     * @throws ConfigFileNotFoundException 配置文件找不到时
     * @throws FailedToParsingException 配置文件无法解码时
     */
    fun readConfig(externalConfigFile: FileObject): Map<String, Any>
    {
        try {
            val content: String
            if(!externalConfigFile.exists)
            {
                if(!EnvUtil.isPackaged)
                    throw ConfigFileNotFoundException("config.yml")
                JarFile(EnvUtil.jarFile.path).use { jar ->
                    val configFileInZip = jar.getJarEntry("config.yml") ?: throw ConfigFileNotFoundException("config.yml")
                    jar.getInputStream(configFileInZip).use { content = it.readBytes().decodeToString() }
                }
            } else {
                content = externalConfigFile.content
            }
            return Yaml().load(content)
        } catch (e: JSONException) {
            throw FailedToParsingException("配置文件config.yml", "yaml", e.message ?: "")
        }
    }

    /**
     * 从Jar文件内读取语言配置文件（仅图形模式启动时有效）
     * @return 语言配置文件对象
     * @throws ConfigFileNotFoundException 配置文件找不到时
     * @throws FailedToParsingException 配置文件无法解码时
     */
    fun readLangs(): Map<String, String>
    {
        try {
            val content: String
            if (EnvUtil.isPackaged)
                JarFile(EnvUtil.jarFile.path).use { jar ->
                    val langFileInZip = jar.getJarEntry("lang.yml") ?: throw ConfigFileNotFoundException("lang.yml")
                    jar.getInputStream(langFileInZip).use { content = it.readBytes().decodeToString() }
                }
            else
                content = (FileObject(System.getProperty("user.dir")) + "src/main/resources/lang.yml").content

            return Yaml().load(content)
        } catch (e: JSONException) {
            throw FailedToParsingException("语言配置文件lang.yml", "yaml", e.message ?: "")
        }
    }

    /**
     * 获取进程的工作目录
     */
    fun getWorkDirectory(): FileObject
    {
        return System.getProperty("user.dir").run {
            if(EnvUtil.isPackaged)
                FileObject(this)
            else
                FileObject("$this${File.separator}debug-directory").also { it.mkdirs() }
        }
    }

    /**
     * 获取需要更新的起始目录
     * @throws UpdateDirNotFoundException 当.minecraft目录搜索不到时
     */
    fun getUpdateDirectory(workDir: FileObject, options: GlobalOptions): FileObject
    {
        return if(EnvUtil.isPackaged) {
            if (options.basePath != "") EnvUtil.jarFile.parent + options.basePath
            else searchDotMinecraft(workDir) ?: throw UpdateDirNotFoundException()
        } else {
            workDir // 调试状态下永远使用project/workdir作为更新目录
        }.apply { mkdirs() }
    }

    /**
     * 获取Jar文件所在的目录
     */
    fun getProgramDirectory(workDir: FileObject): FileObject
    {
        return if(EnvUtil.isPackaged) EnvUtil.jarFile.parent else workDir
    }

    companion object {
        /**
         * 从JavaAgent启动
         */
        @JvmStatic
        fun premain(agentArgs: String?, ins: Instrumentation?)
        {
            val useGraphicsMode = agentArgs != "windowless" && Desktop.isDesktopSupported()
            Main().run(startsWithGraphicsMode = useGraphicsMode, startsFromJavaAgent = true)
            LogSys.info("finished!")
        }

        /**
         * 独立启动
         */
        @JvmStatic
        fun main(args: Array<String>)
        {
            val useGraphicsMode = !(args.isNotEmpty() && args[0] == "windowless") && Desktop.isDesktopSupported()
            Main().run(startsWithGraphicsMode = useGraphicsMode, startsFromJavaAgent = false)
            LogSys.info("finished!")
        }
    }

    private data class DownloadTask(
        val lengthExpected: Long,
        val modified: Long,
        val url: String,
        val file: FileObject,
        val noCache: String?,
    )
}