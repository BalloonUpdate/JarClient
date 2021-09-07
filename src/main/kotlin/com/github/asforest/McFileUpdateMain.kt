@file:JvmName("McFileUpdateMain")
package com.github.asforest

import com.github.asforest.exception.*
import com.github.asforest.logging.LogSys
import com.github.asforest.model.IndexResponse
import com.github.asforest.util.*
import com.github.asforest.window.MainWin
import com.github.asforest.workmode.CommonMode
import okhttp3.*
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.scanner.ScannerException
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.lang.RuntimeException
import java.net.ConnectException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.swing.JOptionPane
import kotlin.system.exitProcess

object McFileUpdateMain
{
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

            if(e is BaseException)
            {
                JOptionPane.showMessageDialog(null, e.javaClass.simpleName+"\n"+e.message, e.getDisplayName(), JOptionPane.ERROR_MESSAGE)
            } else {
                val choice = JOptionPane.showConfirmDialog(null, e.javaClass.name+"\n"+e.message+"\n\n点击\"是\"显示错误详情，点击\"否\"退出程序", "发生错误", JOptionPane.YES_NO_OPTION)
                if(choice==0)
                    JOptionPane.showMessageDialog(null, e.stackTraceToString(), "调用堆栈", JOptionPane.ERROR_MESSAGE)
            }
            exitProcess(1)
        }
    }

    fun run()
    {
        val window = MainWin()
        val workDir = System.getProperty("user.dir").run { FileObj(if(Utils.isPackaged) this else "$this${File.separator}workdir") }

        if(Utils.isPackaged && ".minecraft" !in workDir)
            throw WrongWorkDirectoryException("请将软件放到.minecraft目录旁运行(与启动器同级)")

        val configContent: String
        val manifest = Manifest()
        var version = "0"

        // 读取配置文件
        val externalConfigFile = workDir("config.yml")
        if(!externalConfigFile.exists)
        {
            if(!Utils.isPackaged)
                throw ConfigFileNotFoundException("找不到配置文件config.yml")
            JarFile(Utils.jarFile.path).use { jar ->
                val configFileInZip = jar.getJarEntry("config.yml") ?: throw ConfigFileNotFoundException("找不到配置文件config.yml")
                jar.getInputStream(configFileInZip).use { configContent = it.readBytes().decodeToString() }
            }
        } else {
            configContent = externalConfigFile.content
        }
        // 读取版本信息
        if(Utils.isPackaged)
        {
            JarFile(Utils.jarFile.path).use { jar ->
                val manifestInZip = jar.getJarEntry("META-INF/MANIFEST.MF") ?: throw RuntimeException("找不到META-INF/MANIFEST.MF")
                jar.getInputStream(manifestInZip).use { manifest.read(it) }
                version = manifest.mainAttributes.getValue("Application-Version") ?: "?.?"
            }
        }

        // 解析配置文件
        val parse = { content: String ->
            try {
                Yaml().load<Map<String, Any>>(content)
            } catch (e: ScannerException) {
                throw UnableToDecodeException("配置文件无法解码:\n"+e.message)
            }
        }
        val config = parse(configContent)
        val server = readFromConfig<String>(config, "server") ?: throw ConfigFileException("配置文件中的server选项无效.")
        val autoExit = readFromConfig<Boolean>(config, "auto_exit") ?: false

        // 初始化窗口
        window.titleTextSuffix = " v$version"
        window.titleText = "文件更新助手"
        window.stateText = "正在连接到更新服务器..."

        // 准备HTTP客户端
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).build()
        val indexResponse = fetchIndexResponse(client, server)

        window.stateText = "正在获取资源更新..."
        val updateInfo = yamlParseAsList(httpFetch(client, indexResponse.updateUrl))
        if(indexResponse.mode != "common")
            throw NotSupportedWorkModeException("不支持的工作模式: ${indexResponse.mode}, 工作模式只支持common")

        // 对比文件差异
        val regexes = indexResponse.paths.asList()
        val targetDirectory = (if(!Utils.isPackaged) workDir + "download" else workDir).also { it.mkdirs() }
        val template = toSimpleFileObject(updateInfo as List<Map<String, Any>>)

        // 文件对比进度条
        val fileCount = Utils.countFiles(targetDirectory)
        var scannedCount = 0

        // 开始对比
        val diff = CommonMode(regexes, targetDirectory, template)() {
            scannedCount += 1
            window.progress1text = "正在检查资源..."
            window.stateText = it.name
            window.progress1value = ((scannedCount/fileCount.toFloat())*1000).toInt()
        }
        window.progress1value = 0

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

    inline fun <reified Type> readFromConfig(config: Map<String, Any>, key: String): Type?
    {
        return if(key in config && config[key] != null || config[key]!! is Type) config[key]!! as Type else null
    }

    fun toSimpleFileObject(raw: List<Map<String, Any>>): Array<SimpleFileObject>
    {
        val res = ArrayList<SimpleFileObject>()

        for (f in raw)
        {
            val name = f["name"] as String

            if("children" in f)
            {
                val files = f["children"] as List<Map<String, Any>>
                res += SimpleDirectory(name, toSimpleFileObject(files))
            } else {
                val length = f["length"] as Integer
                val hash = f["hash"] as String
                res += SimpleFile(name, length.toLong(), hash)
            }
        }

        return res.toTypedArray()
    }

    fun fetchIndexResponse(client: OkHttpClient, indexUrl: String): IndexResponse {
        val baseurl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1)
        val resp = yamlParseAsMap(httpFetch(client, indexUrl))
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

    fun yamlParseAsMap(content: String): Map<String, Any>
    {
        try {
            return Yaml().load<Map<String, Any>>(content)!!
        } catch (e: ScannerException) {
            throw UnableToDecodeException("Yaml无法解码:\n"+e.message)
        }
    }

    fun yamlParseAsList(content: String): List<Any>
    {
        try {
            return Yaml().load<List<Any>>(content)!!
        } catch (e: ScannerException) {
            throw UnableToDecodeException("Yaml无法解码:\n"+e.message)
        }
    }

    fun httpFetch(client: OkHttpClient, url: String): String
    {
        val req = Request.Builder().url(url).build()

        try {
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful)
                    throw HttpRequestFailException("Http状态码不正确(不在2xx-3xx之间)\n$url with httpcode(${r.code})\n"+ r.body?.charStream().use {
                        it?.readText()?.run { if(length> 300) substring(0, 300)+"\n..." else this } ?: "_None_"
                    })
                return r.body!!.string()
            }
        } catch (e: ConnectException) {
            throw ConnectionClosedException("无法连接到服务器")
        } catch (e: SocketException) {
            throw ConnectionClosedException("连接中断")
        }
    }

    fun httpDownload(client: OkHttpClient, url: String, file: FileObj, lengthExpected: Long, onProgress: (packageLength: Long, bytesReceived: Long, totalReceived: Long) -> Unit)
    {
        file.makeParentDirs()
        val req = Request.Builder().url(url).build()

        val bufferLen = { filelen: Long ->
            val kb = 1024
            val mb = 1024 * 1024
            val gb = 1024 * 1024 * 1024
            when {
                filelen < 1 * mb -> 8 * kb
                filelen < 2 * mb -> 16 * kb
                filelen < 4 * mb -> 32 * kb
                filelen < 8 * mb -> 64 * kb
                filelen < 16 * mb -> 256 * kb
                filelen < 32 * mb -> 512 * kb
                filelen < 64 * mb -> 1 * mb
                filelen < 128 * mb -> 2 * mb
                filelen < 256 * mb -> 4 * mb
                filelen < 512 * mb -> 8 * mb
                filelen < 1 * gb -> 16 * mb
                else -> 32 * mb
            }
        }

        try {
            client.newCall(req).execute().use { r ->
                if(r.isSuccessful)
                {
                    r.body!!.byteStream().use { input ->
                        FileOutputStream(file.path).use { output ->
                            var bytesReceived: Long = 0
                            var len = 10
                            val buffer: ByteArray = ByteArray(bufferLen(lengthExpected))
                            while (input.read(buffer).also { len = it; bytesReceived += it } != -1)
                            {
                                output.write(buffer, 0, len)
                                onProgress(len.toLong(), bytesReceived, lengthExpected)
                            }
                        }
                    }
                } else {
                    throw HttpRequestFailException("Http状态码不正确(不在2xx-3xx之间)\n$url with httpcode(${r.code})")
                }
            }
        } catch (e: ConnectException) {
            throw ConnectionClosedException("无法连接到服务器(通常是网络原因或者配置不正确)")
        } catch (e: SocketException) {
            throw ConnectionClosedException("连接中断(通常是网络原因)")
        }
    }
}

