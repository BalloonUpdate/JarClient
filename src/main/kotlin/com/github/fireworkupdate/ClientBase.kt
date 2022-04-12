package com.github.fireworkupdate

import com.github.fireworkupdate.data.IndexResponse
import com.github.fireworkupdate.data.GlobalOptions
import com.github.fireworkupdate.exception.ConfigFileNotFoundException
import com.github.fireworkupdate.exception.UnableToDecodeException
import com.github.fireworkupdate.exception.UpdateDirNotFoundException
import com.github.fireworkupdate.data.FileObj
import com.github.fireworkupdate.data.SimpleDirectory
import com.github.fireworkupdate.data.SimpleFile
import com.github.fireworkupdate.data.SimpleFileObject
import com.github.fireworkupdate.util.EnvUtil
import com.github.fireworkupdate.util.HttpUtil
import com.github.fireworkupdate.util.Utils
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.scanner.ScannerException
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

open class ClientBase
{
    /**
     * 应用程序版本号
     */
    val appVersion by lazy { EnvUtil.version }

    /**
     * 工作目录
     */
    val workDir = System.getProperty("user.dir").run { FileObj(if(EnvUtil.isPackaged) this else "$this${File.separator}workdir") }

    val progDir = if(EnvUtil.isPackaged) EnvUtil.jarFile.parent else workDir

    /**
     * 配置文件对象
     */
    val options = GlobalOptions.CreateFromMap(readConfig(progDir + "config.yml"))

    /**
     * 更新目录（更新目录指从哪个目录起始，更新所有子目录）
     */
    val updateDir by lazy {
        if(EnvUtil.isPackaged) {
            // .minecraft目录检测，如果在配置文件指定了base-path，则禁用搜索，改为使用用户指定的路径
            if (options.basePath != "") EnvUtil.jarFile.parent + options.basePath
            else searchDotMinecraft(workDir) ?: throw UpdateDirNotFoundException()
        } else {
            workDir // 调试状态下永远使用project/workdir作为更新目录
        }.apply { mkdirs() }
    }

    /**
     * OkHttp客户端对象
     */
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS).build()

    /**
     * 从外部/内部读取配置文件并将内容返回（当外部不可用时会从内部读取）
     * @param externalConfigFile 外部配置文件
     * @return 解码后的配置文件对象
     */
    fun readConfig(externalConfigFile: FileObj): Map<String, Any>
    {
        try {
            val content: String
            if(!externalConfigFile.exists)
            {
                if(!EnvUtil.isPackaged)
                    throw ConfigFileNotFoundException("找不到配置文件config.yml")
                JarFile(EnvUtil.jarFile.path).use { jar ->
                    val configFileInZip = jar.getJarEntry("config.yml") ?: throw ConfigFileNotFoundException("找不到配置文件config.yml")
                    jar.getInputStream(configFileInZip).use { content = it.readBytes().decodeToString() }
                }
            } else {
                content = externalConfigFile.content
            }
            return Yaml().load(content)
        } catch (e: ScannerException) {
            throw UnableToDecodeException("配置文件无法解码:\n"+e.message)
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
                res += SimpleFile(name, length.toLong(), hash, modified.toLong())
            }
        }
        return res
    }

    /**
     * 从服务器获取文件更新信息
     */
    fun fetchIndexResponse(client: OkHttpClient, indexUrl: String, noCache: String?): IndexResponse
    {
        val baseurl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1)
        val resp = parseAsJsonObject(HttpUtil.httpFetch(client, indexUrl, noCache))
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
            commonMode = (resp["common_mode"] as JSONArray).map { it as String }
            onceMode = (resp["once_mode"]  as JSONArray).map { it as String }
            updateUrl = baseurl + if (update.indexOf("?") != -1) update else "$update.json"
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

    /**
     * 向上搜索，直到有一个父目录包含.minecraft目录，然后返回这个父目录
     * @param basedir 从哪个目录开始向上搜索
     * @return 包含.minecraft目录的父目录
     * @throws FileNotFoundException 找不到包含.minecraft目录的父目录
     */
    fun searchDotMinecraft(basedir: FileObj): FileObj?
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
}