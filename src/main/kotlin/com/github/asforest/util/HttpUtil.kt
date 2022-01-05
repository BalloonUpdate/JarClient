package com.github.asforest.util

import com.github.asforest.exception.ConnectionClosedException
import com.github.asforest.exception.HttpRequestFailException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketException

object HttpUtil
{
    /**
     * 从HTTP服务器上获取文件内容（主要是小文件）
     */
    fun httpFetch(client: OkHttpClient, url: String, noCache: String?): String
    {
        var url_ = url

        // 避免CDN缓存
        if(noCache != null)
            url_ = appendQueryParam(url_, noCache, System.currentTimeMillis().toString())

        val req = Request.Builder().url(url_).build()

        try {
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful)
                    throw HttpRequestFailException("Http状态码不正确(不在2xx-3xx之间)\n$url_ with httpcode(${r.code})\n"+ r.body?.charStream().use {
                        it?.readText()?.run { if(length> 300) substring(0, 300)+"\n..." else this } ?: "_None_"
                    })
                return r.body!!.string()
            }
        } catch (e: ConnectException) {
            throw ConnectionClosedException("无法连接到更新服务器")
        } catch (e: SocketException) {
            throw ConnectionClosedException("连接中断")
        }
    }

    /**
     * 从HTTP服务器上下载文件（主要是大文件，二进制文件）
     */
    fun httpDownload(client: OkHttpClient, url: String, file: FileObj, lengthExpected: Long, noCache: String?, onProgress: (packageLength: Long, bytesReceived: Long, totalReceived: Long) -> Unit)
    {
        var url_ = url.replace("+", "%2B")

        // 避免CDN缓存
        if(noCache != null)
            url_ = appendQueryParam(url_, noCache, System.currentTimeMillis().toString())

        file.makeParentDirs()
        val req = Request.Builder().url(url_).build()

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
                    throw HttpRequestFailException("Http状态码不正确(不在2xx-3xx之间)\n$url_ with httpcode(${r.code})")
                }
            }
        } catch (e: ConnectException) {
            throw ConnectionClosedException("无法连接到服务器(通常是网络原因或者配置不正确)")
        } catch (e: SocketException) {
            throw ConnectionClosedException("连接中断(通常是网络原因)")
        }
    }

    fun appendQueryParam(url: String, key: String, value: String): String
    {
        var result = url

        if(result.indexOf("?") == -1)
            result += "?"

        result += (if(result.endsWith("?")) "" else "&") + key + '=' + value

        return result
    }

//    fun encodeUri(uri: String): String {
//        var newUri: String = ""
//        val st = StringTokenizer(uri, "/ ", true)
//        while (st.hasMoreTokens()) {
//            val tok = st.nextToken()
//            if (tok == "/") {
//                newUri += "/"
//            } else if (tok == " ") {
//                newUri += "%20"
//            } else {
//                try {
//                    newUri += URLEncoder.encode(tok, "UTF-8")
//                } catch (ignored: UnsupportedEncodingException) { }
//            }
//        }
//        return newUri
//    }
}