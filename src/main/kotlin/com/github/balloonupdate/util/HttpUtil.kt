package com.github.balloonupdate.util

import com.github.balloonupdate.exception.*
import com.github.balloonupdate.logging.LogSys
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

object HttpUtil
{
    /**
     * 多可用源版本的httpFetch
     */
    fun httpFetchJsonMutiple(
        client: OkHttpClient,
        urls: List<String>,
        noCache: String?,
        retryTimes: Int,
        description: String,
        parseAsJsonObject: Boolean
    ): Pair<JSONObject?, JSONArray?> {
        var ex: Exception? = null

        for (url in urls)
        {
            ex = try {
                return httpFetchJson(client, url, noCache, retryTimes, description, parseAsJsonObject)
            } catch (e: ConnectionRejectedException) { e }
            catch (e: ConnectionInterruptedException) { e }
            catch (e: ConnectionTimeoutException) { e }

            if (urls.size > 1)
                LogSys.error(ex!!.toString())
        }

        throw ex!!
    }

    /**
     * 多可用源版本的httpDownloadMutiple
     */
    fun httpDownloadMutiple(
        client: OkHttpClient,
        urls: List<String>,
        writeTo: File2,
        lengthExpected: Long,
        noCache: String?,
        retryTimes: Int,
        onProgress: (packageLength: Long, bytesReceived: Long, totalReceived: Long) -> Unit,
        onSourceFallback: () -> Unit,
    ) {
        var ex: Exception? = null

        for (url in urls)
        {
            ex = try {
                return httpDownload(client, url, writeTo, lengthExpected, noCache, retryTimes, onProgress)
            } catch (e: ConnectionRejectedException) { e }
            catch (e: ConnectionInterruptedException) { e }
            catch (e: ConnectionTimeoutException) { e }

            onSourceFallback()

            if (urls.size > 1)
                LogSys.error(ex!!.toString())
        }

        throw ex!!
    }

    /**
     * 从HTTP服务器上获取Json文件
     * @param client OkHttpClient客户端
     * @param url 要获取的URL
     * @param noCache 是否使用无缓存模式
     * @param description 这个文件的描述
     * @param parseAsJsonObject 是否解析成JsonObject对象，或者是JsonArray对象
     * @return 解析好的JsonObject对象，或者是JsonArray对象
     */
    fun httpFetchJson(
        client: OkHttpClient,
        url: String,
        noCache: String?,
        retryTimes: Int,
        description: String,
        parseAsJsonObject: Boolean
    ): Pair<JSONObject?, JSONArray?> {
        var link = url

        if(noCache != null)
            link = appendQueryParam(link, noCache, System.currentTimeMillis().toString())

        val req = Request.Builder().url(link).build()
        LogSys.debug("http request on $link")

        var ex: Throwable? = null
        var retries = retryTimes
        while (--retries >= 0)
        {
            try {
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        val body = r.body?.string()?.run { if (length > 300) substring(0, 300) + "\n..." else this }
                        throw HttpResponseStatusCodeException(r.code, link, body)
                    }

                    val body = r.body!!.string()

                    try {
                        return if (parseAsJsonObject) Pair(JSONObject(body), null) else Pair(null, JSONArray(body))
                    } catch (e: JSONException) {
                        ex = FailedToParsingException(description, "json", "$url ${e.message}")
                    }
                }
            } catch (e: ConnectException) {
                ex = ConnectionRejectedException(link, e.message ?: "")
            } catch (e: SocketException) {
                ex = ConnectionInterruptedException(link, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                ex = ConnectionTimeoutException(link, e.message ?: "")
            } catch (e: Throwable) {
                ex = e
            }

            LogSys.warn("")
            LogSys.warn(ex.toString())
            LogSys.warn("retrying $retries ...")

            Thread.sleep(1000)
        }

//        if (ex != null)
        throw ex!!
    }

    /**
     * 从HTTP服务器上下载文件（主要是大文件，二进制文件）
     */
    fun httpDownload(
        client: OkHttpClient,
        url: String,
        writeTo: File2,
        lengthExpected: Long,
        noCache: String?,
        retryTimes: Int,
        onProgress: (packageLength: Long, bytesReceived: Long, totalReceived: Long) -> Unit
    ) {
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
        var retries = retryTimes
        while (--retries >= 0)
        {
            try {
                client.newCall(req).execute().use { r ->
                    if(!r.isSuccessful)
                        throw HttpResponseStatusCodeException(r.code, link, r.body?.string())

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
                }
                ex = null
                break
            } catch (e: ConnectException) {
                ex = ConnectionInterruptedException(link, e.message ?: "")
            } catch (e: SocketException) {
                ex = ConnectionRejectedException(link, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                ex = ConnectionTimeoutException(link, e.message ?: "")
            } catch (e: Throwable) {
                ex = e
            }

            LogSys.warn("")
            LogSys.warn(ex.toString())
            LogSys.warn("retrying $retries ...")

            Thread.sleep(1000)
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