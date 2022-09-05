package com.github.balloonupdate.util

import com.github.balloonupdate.exception.ConnectionRejectedException
import com.github.balloonupdate.exception.ConnectionInterruptedException
import com.github.balloonupdate.exception.ConnectionTimeoutException
import com.github.balloonupdate.exception.HttpResponseStatusCodeException
import com.github.balloonupdate.logging.LogSys
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

object HttpUtil
{
    /**
     * 从HTTP服务器上获取文件内容（主要是小文件）
     */
    fun httpFetch(client: OkHttpClient, url: String, noCache: String?): String
    {
        var link = url

        if(noCache != null)
            link = appendQueryParam(link, noCache, System.currentTimeMillis().toString())

        val req = Request.Builder().url(link).build()
        LogSys.debug("http request on $link")
        try {
            client.newCall(req).execute().use { r ->
                if(r.isSuccessful)
                    return r.body!!.string()

                val body = r.body?.string()?.run { if(length> 300) substring(0, 300) + "\n..." else this }
                throw HttpResponseStatusCodeException(r.code, link, body)
            }
        } catch (e: ConnectException) {
            throw ConnectionRejectedException(link)
        } catch (e: SocketException) {
            throw ConnectionInterruptedException(link)
        } catch (e: SocketTimeoutException) {
            throw ConnectionTimeoutException(link)
        }
    }

    /**
     * 从HTTP服务器上下载文件（主要是大文件，二进制文件）
     */
    fun httpDownload(client: OkHttpClient, url: String, writeTo: File2, lengthExpected: Long, noCache: String?, onProgress: (packageLength: Long, bytesReceived: Long, totalReceived: Long) -> Unit)
    {
        var link = url.replace("+", "%2B")

        if(noCache != null)
            link = appendQueryParam(link, noCache, System.currentTimeMillis().toString())

        writeTo.makeParentDirs()
        val req = Request.Builder().url(link).build()

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

        var ex: Throwable? = null
        var retries = 5
        while (--retries >= 0)
        {
            try {
                client.newCall(req).execute().use { r ->
                    if(r.isSuccessful)
                    {
                        r.body!!.byteStream().use { input ->
                            FileOutputStream(writeTo.path).use { output ->
                                var bytesReceived: Long = 0
                                var len: Int
                                val buffer = ByteArray(bufferLen(lengthExpected))
                                while (input.read(buffer).also { len = it; bytesReceived += it } != -1)
                                {
                                    output.write(buffer, 0, len)
                                    onProgress(len.toLong(), bytesReceived, lengthExpected)
                                }
                            }
                        }
                    } else {
                        throw HttpResponseStatusCodeException(r.code, link, r.body?.string())
                    }
                }
                ex = null
                break
            } catch (e: ConnectException) {
                ex = ConnectionInterruptedException(link)
            } catch (e: SocketException) {
                ex = ConnectionRejectedException(link)
            } catch (e: SocketTimeoutException) {
                throw ConnectionTimeoutException(link)
            }
        }

        if (ex != null)
            throw ex
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