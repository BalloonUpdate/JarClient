//@file:JvmName("Utils")
package com.github.asforest.util

import com.github.asforest.exception.ManifestNotReadableException
import com.github.asforest.file.FileObj
import java.net.URLDecoder
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

object EnvUtil
{
    /**
     * 程序是否被打包
     */
    @JvmStatic
    val isPackaged: Boolean get() = javaClass.getResource("").protocol != "file"

    /**
     * 获取当前Jar文件路径（仅打包后有效）
     */
    @JvmStatic
    val jarFile: FileObj
        get() = FileObj(URLDecoder.decode(EnvUtil.javaClass.protectionDomain.codeSource.location.file, "UTF-8"))

    val version: String by lazy { manifest["Version"] ?: "0.0.0" }

    val gitCommit: String by lazy { manifest["Git-Commit"] ?: "<development>" }

    val compileTime: String by lazy { manifest["Compile-Time"] ?: "<no compile time>" }

    val compileTimeMs: Long by lazy { manifest["Compile-Time-Ms"]?.toLong() ?: 0L }

    /**
     * 读取版本信息（程序打包成Jar后才有效）
     * @return Application版本号，如果为打包成Jar则返回null
     */
    val manifest: Map<String, String> get() {
        return try {
            originManifest.entries.associate { it.key.toString() to it.value.toString() }
        } catch (e: ManifestNotReadableException) {
            mapOf()
        }
    }

    val originManifest: Attributes
        get() {
        if(!EnvUtil.isPackaged)
            throw ManifestNotReadableException("This plugin has not been packaged yet")

        JarFile(jarFile.path).use { jar ->
            jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF")).use {
                return Manifest(it).mainAttributes
            }
        }
    }
}