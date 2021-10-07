package com.github.asforest.util

import com.github.asforest.exception.ManifestNotReadableException
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

object ManifestUtil
{
    val version: String get() = manifest["Application-Version"] ?: "0.0.0"

    val author: String get() = manifest["Author"] ?: "asforest"

    val gitCommit: String get() = manifest["Git-Commit"] ?: "<development>"

    val compileTime: String get() = manifest["Compile-Time"] ?: "<no compile time>"

    val compileTimeMs: Long get() = manifest["Compile-Time-Ms"]?.toLong() ?: 0L

    /**
     * 读取版本信息（程序打包成Jar后才有效）
     * @return Application版本号，如果为打包成Jar则返回null
     */
    val manifest: Map<String, String> get()
    {
        return try {
            (originManifest as Map<Attributes.Name, String>)
                .filterValues { it.isNotEmpty() }.mapKeys { it.key.toString() }
        } catch (e: ManifestNotReadableException) {
            mapOf()
        }
    }

    val originManifest: Attributes get()
    {
        if(!EnvUtil.isPackaged)
            throw ManifestNotReadableException("This plugin has not been packaged yet")

        JarFile(EnvUtil.jarFile.path).use { jar ->
            jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF")).use {
                return Manifest(it).mainAttributes
            }
        }
    }
}