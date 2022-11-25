package com.github.balloonupdate

import com.github.balloonupdate.data.*
import com.github.balloonupdate.diff.CommonModeCalculator
import com.github.balloonupdate.diff.DiffCalculatorBase
import com.github.balloonupdate.diff.OnceModeCalculator
import com.github.balloonupdate.gui.NewWindow
import com.github.balloonupdate.localization.LangNodes
import com.github.balloonupdate.localization.Localization
import com.github.balloonupdate.logging.LogSys
import com.github.balloonupdate.util.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.swing.JOptionPane

class WorkThread(
    val window: NewWindow?,
    val options: GlobalOptions,
    val workDir: File2,
    val updateDir: File2
): Thread() {
    var difference: DiffCalculatorBase.Difference? = null

    /**
     * 更新助手工作线程
     */
    override fun run()
    {
        collectEnvInfo()

        if (!options.quietMode)
            window?.show()

        val okClient = OkHttpClient.Builder()
            .connectTimeout(options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(options.httpReadTimeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(options.httpWriteTimeout.toLong(), TimeUnit.MILLISECONDS).build()

        // 从服务器获取元信息
        val metaResponse = requestIndex(okClient, options.server, options.noCache)

        // 更新UI
        window?.statusBarText = Localization[LangNodes.fetch_metadata]

        // 获取结构数据
        val rawData = HttpUtil.httpFetchJsonMutiple(okClient, metaResponse.structureFileUrls, options.noCache, options.retryTimers, "结构文件请求", false).second!!
        val remoteFiles: List<SimpleFileObject> =  unserializeFileStructure(JSONArray(rawData))

        // 使用版本缓存
        var isVersionOutdate = true
        val versionFile = updateDir + options.versionCache

        if(options.versionCache.isNotEmpty())
        {
            versionFile.makeParentDirs()
            isVersionOutdate = if(!versionFile.exists) true else {
                val versionCached = versionFile.content
                val versionRecieved = Utils.sha1(rawData.toString(0))
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
                hashAlgorithm = metaResponse.hashAlgorithm,
            )

            val onceOpt = DiffCalculatorBase.Options(
                patterns = metaResponse.onceMode,
                checkModified = options.checkModified,
                hashAlgorithm = metaResponse.hashAlgorithm,
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
            diff.oldFiles.map { (updateDir + it) }.forEach { if (!EnvUtil.isPackaged || it.path != EnvUtil.jarFile.path) it.delete() }
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
            window?.statusBarText = "总进度"

            // 生成下载任务
            val tasks = diff.newFiles.map { (relativePath, lm) ->
                val lengthExpected = lm.first
                val modified = lm.second
                val urls = metaResponse.assetsDirUrls.map { it + relativePath }
                val file = updateDir + relativePath
                DownloadTask(lengthExpected, modified, urls, file, options.noCache)
            }.toMutableList()

            val lock = Any()
            var committedCount = 0
            var downloadedCount = 0
            val samplers = mutableListOf<SpeedSampler>()

            // 单个线程的下载逻辑
            fun download(task: DownloadTask, taskRow: NewWindow.TaskRow?)
            {
                val file = task.file
                val urls = task.urls
                val lengthExpected = task.lengthExpected
                val modified = task.modified

                val sampler = SpeedSampler(3000)
                synchronized(lock) {
                    samplers += sampler

                    committedCount += 1
                    LogSys.debug("request($committedCount/${diff.newFiles.values.size}): ${urls.joinToString()}, write to: ${file.path}")
                }

                var localDownloadedBytes: Long = 0

                var time = System.currentTimeMillis()

                HttpUtil.httpDownloadMutiple(okClient, urls, file, lengthExpected, options.noCache, options.retryTimers, { packageLength, received, total ->
                    if (taskRow == null)
                        return@httpDownloadMutiple

                    totalBytesDownloaded += packageLength
                    localDownloadedBytes += packageLength
                    val currentProgress = received / total.toFloat() * 100
                    val totalProgress = totalBytesDownloaded / totalBytes.toFloat() * 100

                    sampler.feed(packageLength)
                    val speed = sampler.speed()

                    // 每隔200ms更新一次ui
                    if (System.currentTimeMillis() - time < 400)
                        return@httpDownloadMutiple
                    time = System.currentTimeMillis()

//                    val currProgressInString = String.format("%.1f", currentProgress)
                    val totalProgressInString = if (totalProgress > 100) "114.514" else String.format("%.1f", totalProgress)

                    taskRow.borderText = file.name
                    taskRow.progressBarValue = (currentProgress * 10).toInt()
//                    taskRow.labelText = ""
                    taskRow.progressBarLabel = "${Utils.convertBytes(received)} / ${Utils.convertBytes(total)}   -   " +Utils.convertBytes(speed) + "/s"

                    val toatalSpeed: Long
                    synchronized(lock) { toatalSpeed = samplers.sumOf { it.speed() } }

                    window!!.statusBarProgressValue = (totalProgress * 10).toInt()
                    window.statusBarProgressText = "$totalProgressInString%  -  ${downloadedCount}/${diff.newFiles.values.size}   -   " + Utils.convertBytes(toatalSpeed) + "/s"
                    window.titleText = Localization[LangNodes.window_title_downloading, "PERCENT", totalProgressInString]
                }, {
                    totalBytesDownloaded -= localDownloadedBytes
                })

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
            val threads = options.downloadThreads
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
                        val task: DownloadTask?
                        synchronized(lock2){ task = tasks.removeFirstOrNull() }
                        if (task == null)
                            continue
                        try {
                            download(task, taskRow)
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
            versionFile.content = Utils.sha1(rawData.toString(0))

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

        difference = diff
    }

    /**
     * 收集并打印环境信息
     */
    fun collectEnvInfo()
    {
        val jvmVersion = System.getProperty("java.version")
        val jvmVender = System.getProperty("java.vendor")
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        val osVersion = System.getProperty("os.version")

        LogSys.info("updating directory:   ${updateDir.path}")
        LogSys.info("working directory:    ${workDir.path}")
        LogSys.info("executable directory: ${if(EnvUtil.isPackaged) EnvUtil.jarFile.parent.path else "dev-mode"}")
        LogSys.info("application version:  ${EnvUtil.version} (${EnvUtil.gitCommit})")
        LogSys.info("java virtual machine: $jvmVender $jvmVersion")
        LogSys.info("operating system: $osName $osVersion $osArch")
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
    fun requestIndex(client: OkHttpClient, urls: List<String>, noCache: String?): MetadataResponse
    {
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

        val meta = HttpUtil.httpFetchJsonMutiple(client, urls, noCache, options.retryTimers, "元数据文件请求", true).first!!

        val ha = if (meta.has("hash_algorithm")) (meta["hash_algorithm"] as String) else "sha1"
        val hashAlgorithm = HashAlgorithm.FromString(ha, HashAlgorithm.SHA1)
        val commonMode = (meta["common_mode"] as JSONArray).map { it as String }
        val onceMode = (meta["once_mode"]  as JSONArray).map { it as String }

        val structureFileUrls = mutableListOf<String>()
        val assetsDirUrls = mutableListOf<String>()

        for (url in urls)
        {
            val baseurl = url.substring(0, url.lastIndexOf('/') + 1)
            val assetDir = meta["update"] as? String ?: "res"
            val structureFileName = when (hashAlgorithm) {
                HashAlgorithm.SHA1 -> "${assetDir}.json"
                HashAlgorithm.MD5 -> "${assetDir}_md5.json"
                HashAlgorithm.CRC32 -> "${assetDir}_crc32.json"
            }
            val structureFileUrl = baseurl + if (assetDir.indexOf("?") != -1) assetDir else structureFileName
            val assetsDirUrl = baseurl + findSource(assetDir, assetDir) + "/"

            structureFileUrls += structureFileUrl
            assetsDirUrls += assetsDirUrl
        }

        return MetadataResponse(
            commonMode = commonMode,
            onceMode = onceMode,
            structureFileUrls = structureFileUrls,
            assetsDirUrls = assetsDirUrls,
            hashAlgorithm = hashAlgorithm,
        )
    }
}