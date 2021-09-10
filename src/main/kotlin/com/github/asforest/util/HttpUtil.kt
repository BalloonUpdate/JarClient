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

    /**
     * 从HTTP服务器上下载文件（主要是大文件，二进制文件）
     */
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