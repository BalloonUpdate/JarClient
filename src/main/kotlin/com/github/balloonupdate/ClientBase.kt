package com.github.balloonupdate

import com.github.balloonupdate.data.*
import com.github.balloonupdate.exception.ConfigFileNotFoundException
import com.github.balloonupdate.exception.UnableToDecodeException
import com.github.balloonupdate.exception.UpdateDirNotFoundException
import com.github.balloonupdate.util.EnvUtil
import com.github.balloonupdate.util.HttpUtil
import com.github.balloonupdate.util.Utils
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
        } catch (e: JSONException) {
            throw UnableToDecodeException("配置文件无法解码:\n"+e.message)
        }
    }

    /**
     * 从Jar文件内读取语言配置文件（仅图形模式启动时有效）
     * @return 语言配置文件对象
     */
    fun readLangs(): Map<String, String>
    {
        try {
            val content: String
            if (EnvUtil.isPackaged)
                JarFile(EnvUtil.jarFile.path).use { jar ->
                    val langFileInZip = jar.getJarEntry("lang.yml") ?: throw ConfigFileNotFoundException("找不到配置文件lang.yml")
                    jar.getInputStream(langFileInZip).use { content = it.readBytes().decodeToString() }
                }
            else
                content = (FileObj(System.getProperty("user.dir")) + "src/main/resources/lang.yml").content

            return Yaml().load(content)
        } catch (e: JSONException) {
            throw UnableToDecodeException("语言配置文件无法解码:\n" + e.message)
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
     * 从服务器获取文件更新信息
     */
    fun fetchIndexResponse(client: OkHttpClient, indexUrl: String, noCache: String?): IndexResponse
    {
        val baseurl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1)
        val content = HttpUtil.httpFetch(client, indexUrl, noCache)
        val resp: JSONObject
        try {
            resp = JSONObject(content)
        } catch (e: JSONException) {
            throw UnableToDecodeException("Json无法解码(对应URL: $indexUrl):\n"+e.message)
        }
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

    companion object {
        /**
         * 工作目录
         */
        @JvmStatic
        val workDir = System.getProperty("user.dir").run {
           if(EnvUtil.isPackaged)
               FileObj(this)
           else
               FileObj("$this${File.separator}workdir").also { it.mkdirs() }
        }

        /**
         * 程序所在目录
         */
        @JvmStatic
        val progDir = if(EnvUtil.isPackaged) EnvUtil.jarFile.parent else workDir
    }
}