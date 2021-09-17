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
import okhttp3.*
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.scanner.ScannerException
import java.io.File
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
        try {
            LogSys.initialize()
            run()
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
                if(DialogUtil.confirm("发生错误", content))
                    DialogUtil.error("调用堆栈", e.stackTraceToString())
            } else {
                DialogUtil.error(e.getDisplayName(), e.javaClass.simpleName+"\n"+e.message)
            }
            exitProcess(1)
        }
    }

    fun run()
    {
        val window = MainWin()
        val workDir = System.getProperty("user.dir").run { FileObj(if(EnvUtil.isPackaged) this else "$this${File.separator}workdir") }
        val versionText = readVersionFromManifest() ?: "0"

        // 配置文件
        val config = readConfigContent(workDir, "config.yml")
        val server = readFromConfig<String>(config, "server") ?: throw ConfigFileException("配置文件中的server选项无效")
        val autoExit = readFromConfig<Boolean>(config, "auto-exit") ?: false
        val minecraftCheck = readFromConfig<Boolean>(config, "minecraft-check") ?: true
        val versionCache = readFromConfig<String>(config, "version-cache") ?: ""

        // .minecraft目录检测
        if(EnvUtil.isPackaged && minecraftCheck && ".minecraft" !in workDir)
            throw WrongWorkDirectoryException("请将软件放到.minecraft目录旁运行(与启动器同级)")

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
        val indexResponse = fetchIndexResponse(client, server)

        // 等待服务器返回最新文件结构数据
        window.stateText = "正在获取资源更新..."
        val rawData = httpFetch(client, indexResponse.updateUrl)
        val updateInfo = parseYaml<List<Any>>(rawData)
        if(indexResponse.mode != "common")
            throw NotSupportedWorkModeException("不支持的工作模式: ${indexResponse.mode}, 工作模式只支持common")

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
            val regexes = indexResponse.paths.asList()
            val targetDirectory = workDir.apply { mkdirs() }
            val remoteFiles = unserializeFileStructure(updateInfo as List<Map<String, Any>>)

            // 文件对比进度条
            val fileCount = FileUtil.countFiles(targetDirectory)
            var scannedCount = 0

            // 开始文件对比过程
            diff = CommonMode(regexes, targetDirectory, remoteFiles)() {
                scannedCount += 1
                window.progress1text = "正在检查资源..."
                window.stateText = it.name
                window.progress1value = ((scannedCount/fileCount.toFloat())*1000).toInt()
            }
            window.progress1value = 0

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
            diff.newFiles.values.forEach { totalBytes += it }

            // 开始下载
            for ((relativePath, lengthExpected) in diff.newFiles)
            {
                val url = indexResponse.updateSource + relativePath
                val file = targetDirectory + relativePath

                httpDownload(client, url, file, lengthExpected) { packageLength, received, total ->
                    totalBytesDownloaded += packageLength
                    val currentProgress = received / total.toFloat()*100
                    val totalProgress = totalBytesDownloaded / totalBytes.toFloat()*100

                    val currProgressInString = String.format("%.1f", currentProgress)
                    val totalProgressInString = String.format("%.1f", totalProgress)

                    window.stateText = file.name
                    window.progress1value = (currentProgress*10).toInt()
                    window.progress2value = (totalProgress*10).toInt()
                    window.progress1text = file.name.run { if(length>10) substring(0, 10)+".." else this } + "   -  $currProgressInString%"
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
    fun unserializeFileStructure(raw: List<Map<String, Any>>): Array<SimpleFileObject>
    {
        val res = ArrayList<SimpleFileObject>()
        for (f in raw)
        {
            val name = f["name"] as String
            if("children" in f)
            {
                val files = f["children"] as List<Map<String, Any>>
                res += SimpleDirectory(name, unserializeFileStructure(files))
            } else {
                val length = f["length"] as Int
                val hash = f["hash"] as String
                res += SimpleFile(name, length.toLong(), hash)
            }
        }
        return res.toTypedArray()
    }

    /**
     * 从服务器获取文件更新信息
     */
    fun fetchIndexResponse(client: OkHttpClient, indexUrl: String): IndexResponse
    {
        val baseurl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1)
        val resp = parseYaml<Map<String, Any>>(httpFetch(client, indexUrl))
        val update = resp["update"] as String ?: "res"

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
            serverVersion = resp["version"] as String
            serverType = resp["server_type"] as String
            mode = resp["mode"] as String
            paths = (resp["paths"] as List<String>).toTypedArray()
            updateUrl = baseurl + if (update.indexOf("?") !== -1) update else "$update.yml"
            updateSource = baseurl + findSource(update, update) + "/"
        }
    }

    fun <Type> parseYaml(content: String): Type
    {
        try {
            return Yaml().load(content)
        } catch (e: ScannerException) {
            throw UnableToDecodeException("Yaml无法解码:\n"+e.message)
        }
    }

}