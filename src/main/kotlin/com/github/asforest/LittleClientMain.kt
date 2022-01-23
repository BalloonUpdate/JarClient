@file:JvmName("LittleClientMain")
package com.github.asforest

import com.github.asforest.exception.*
import com.github.asforest.logging.LogSys
import com.github.asforest.model.IndexResponse
import com.github.asforest.util.*
import com.github.asforest.util.HttpUtil.httpDownload
import com.github.asforest.util.HttpUtil.httpFetch
import com.github.asforest.window.MainWin
import com.github.asforest.workmode.AbstractMode
import com.github.asforest.workmode.CommonMode
import com.github.asforest.workmode.OnceMode
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.scanner.ScannerException
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.swing.JOptionPane
import kotlin.system.exitProcess

object LittleClientMain
{
    /**
     * 入口程序
     */
    @JvmStatic
    fun main(args: Array<String>)
    {
        val versionText = readVersionFromManifest() ?: "0"

        try {
            LogSys.initialize()
            run(versionText)
        } catch (e: Exception) {
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
                if(DialogUtil.confirm("发生错误 $versionText", content))
                    DialogUtil.error("调用堆栈", e.stackTraceToString())
            } else {
                DialogUtil.error(e.getDisplayName() +" $versionText", e.message ?: "")
            }
            exitProcess(1)
        }
    }

    fun run(versionText: String)
    {
        val window = MainWin()
        var workDir = System.getProperty("user.dir").run { FileObj(if(EnvUtil.isPackaged) this else "$this${File.separator}workdir") }

        // 配置文件
        val config = readConfigContent(if(EnvUtil.isPackaged) EnvUtil.jarFile.parent else workDir, "config.yml")
        val server = readFromConfig<String>(config, "server") ?: throw ConfigFileException("配置文件中的server选项无效")
        val autoExit = readFromConfig<Boolean>(config, "auto-exit") ?: false
        val workdirExplicitly = readFromConfig<String>(config, "base-path") ?: ""
        val versionCache = readFromConfig<String>(config, "version-cache") ?: ""
        val noCache: String? = readFromConfig<String>(config, "no-cache")
        val modifiedTimePrioritized = readFromConfig<Boolean>(config, "modification-time-prioritized") ?: false

        // .minecraft目录检测
        workDir = if(EnvUtil.isPackaged && workdirExplicitly == "") {
            try {
                searchDotMinecraft(workDir)
            } catch (e: FileNotFoundException) {
                throw WrongWorkDirectoryException("请将软件放到能够搜索到.minecraft目录的位置上")
            }
        } else {
            EnvUtil.jarFile.parent + workdirExplicitly
        }
        workDir.mkdirs()

        // 准备HTTP客户端
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).build()

        // 初始化窗口
        window.titleTextSuffix = " v$versionText"
        window.titleText = "文件更新助手"
        window.stateText = "正在连接到更新服务器..."

        // 连接服务器获取主要更新信息
        val indexResponse = fetchIndexResponse(client, server, noCache)

        // 等待服务器返回最新文件结构数据
        window.stateText = "正在获取资源更新..."
        val rawData = httpFetch(client, indexResponse.updateUrl, noCache)
        val updateInfo = parseAsJsonArray(rawData)

        // 使用版本缓存
        var isVersionOutdate = true
        val versionFile = workDir + versionCache

        if(versionCache.isNotEmpty())
        {
            versionFile.makeParentDirs()
            isVersionOutdate = if(versionFile.exists) {
                val versionCached = versionFile.content
                val versionRecieved = HashUtil.sha1(rawData)
                versionCached != versionRecieved
            } else {
                true
            }
        }

        var diff = AbstractMode.Difference()

        if(isVersionOutdate)
        {
            // 对比文件差异
            val targetDirectory = workDir
//            val remoteFiles = unserializeFileStructure(updateInfo as List<Map<String, Any>>)
            val remoteFiles = unserializeFileStructure(updateInfo as JSONArray)

            // 文件对比进度条
            val fileCount = FileUtil.countFiles(targetDirectory)
            var scannedCount = 0

            // 开始文件对比过程
            LogSys.info("-----CommonMode-----")
            diff = CommonMode(indexResponse.common_mode.asList(), targetDirectory, remoteFiles, modifiedTimePrioritized)() {
                scannedCount += 1
                window.progress1text = "正在检查资源..."
                window.stateText = it.name
                window.progress1value = ((scannedCount/fileCount.toFloat())*1000).toInt()
            }
            window.progress1value = 0
            LogSys.info("-----OnceMode-----")
            diff += OnceMode(indexResponse.once_mode.asList(), targetDirectory, remoteFiles, modifiedTimePrioritized)()

            // 输出差异信息
            LogSys.info("----------")
            diff.oldFiles.forEach { LogSys.info("旧文件: $it") }
            LogSys.info("----------")
            diff.oldFolders.forEach { LogSys.info("旧目录: $it") }
            LogSys.info("----------")
            diff.newFiles.forEach { LogSys.info("新文件: ${it.key}") }
            LogSys.info("----------")
            diff.newFolders.forEach { LogSys.info("新目录: $it") }
            LogSys.info("----------")
            LogSys.info("旧文件: "+diff.oldFiles.size)
            LogSys.info("旧目录: "+diff.oldFolders.size)
            LogSys.info("新文件: "+diff.newFiles.size)
            LogSys.info("新目录: "+diff.newFolders.size)

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
                // 获取下载开始（首个区段开始）时间戳，并且第一次速度采样从100ms就开始，而非1s，避免多个小文件下载时速度一直显示为0
                var timeStart = System.currentTimeMillis() - (rateUpdatePeriod - 100)
                var downloadSpeedRaw = 0.0  // 初始化下载速度为 0
                var bytesDownloaded = 0L    // 初始化时间区段内下载的大小为 0

                val lengthExpected = lm.first
                val midifed = lm.second

                httpDownload(client, url, file, lengthExpected, midifed, noCache) { packageLength, received, total ->
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

                downloadedCount += 1
            }
        }

        if(versionCache.isNotEmpty())
            versionFile.content = HashUtil.sha1(rawData)

        // 程序结束
        if(!autoExit)
        {
            val news = diff.newFiles
            val hasUpdate = news.isEmpty()
            val title = if(hasUpdate) "检查更新完毕" else "文件更新完毕"
            val content = if(hasUpdate) "所有文件已是最新!" else "成功更新${news.size}个文件!"
            JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
        }
        window.close()
    }

    /**
     * 从外部/内部读取配置文件并将内容返回
     * @param workdir 工作目录
     * @param filename 配置文件文件名
     * @return 解码后的配置文件对象
     */
    fun readConfigContent(workdir: FileObj, filename: String): Map<String, Any>
    {
        val externalFile = workdir + filename
        try {
            val content: String
            if(!externalFile.exists)
            {
                if(!EnvUtil.isPackaged)
                    throw ConfigFileNotFoundException("找不到配置文件config.yml")
                JarFile(EnvUtil.jarFile.path).use { jar ->
                    val configFileInZip = jar.getJarEntry("config.yml") ?: throw ConfigFileNotFoundException("找不到配置文件config.yml")
                    jar.getInputStream(configFileInZip).use { content = it.readBytes().decodeToString() }
                }
            } else {
                content = externalFile.content
            }
            return Yaml().load(content)
        } catch (e: ScannerException) {
            throw UnableToDecodeException("配置文件无法解码:\n"+e.message)
        }
    }

    /**
     * 读取版本信息（程序打包成Jar后才有效）
     * @return Application版本号，如果无法读取则返回null
     */
    fun readVersionFromManifest(): String?
    {
        if(!EnvUtil.isPackaged)
            return null
        JarFile(EnvUtil.jarFile.path).use { jar ->
            jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF")).use {
                return Manifest(it).mainAttributes.getValue("Application-Version")
            }
        }
    }

    /**
     * 从配置文件里读取东西，并校验
     */
    inline fun <reified Type> readFromConfig(config: Map<String, Any>, key: String): Type?
    {
        return if(key in config && config[key] != null && config[key] is Type) config[key] as Type else null
    }

    /**
     * 将服务器返回的文件结构信息反序列化成SimpleFileObject对象便于使用
     */
    fun unserializeFileStructure(raw: JSONArray): Array<SimpleFileObject>
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
                val length = f["length"] as Int
                val hash = f["hash"] as String
                val modified =( f["modified"] ?: -1 ) as Int
                res += SimpleFile(name, length.toLong(), hash, modified.toLong())
            }
        }
        return res.toTypedArray()
    }

    /**
     * 从服务器获取文件更新信息
     */
    fun fetchIndexResponse(client: OkHttpClient, indexUrl: String, noCache: String?): IndexResponse
    {
        val baseurl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1)
        val resp = parseAsJsonObject(httpFetch(client, indexUrl, noCache))
        val update = resp["update"] as? String ?: "res"

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

        return IndexResponse().apply {
            common_mode = (resp["common_mode"] as JSONArray).map { it as String }.toTypedArray()
            once_mode = (resp["once_mode"]  as JSONArray).map { it as String }.toTypedArray()
            updateUrl = baseurl + if (update.indexOf("?") !== -1) update else "$update.yml"
            updateSource = baseurl + findSource(update, update) + "/"
        }
    }

    fun parseAsJsonObject(content: String): JSONObject
    {
        try {
            return JSONObject(content)
        } catch (e: ScannerException) {
            throw UnableToDecodeException("Json无法解码:\n"+e.message)
        }
    }

    fun parseAsJsonArray(content: String): JSONArray
    {
        try {
            return JSONArray(content)
        } catch (e: ScannerException) {
            throw UnableToDecodeException("Json无法解码:\n"+e.message)
        }
    }

    fun searchDotMinecraft(basedir: FileObj): FileObj 
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
            throw FileNotFoundException("The .minecraft directory not found.")
        }
        throw FileNotFoundException("The .minecraft directory not found.")
    }

}
