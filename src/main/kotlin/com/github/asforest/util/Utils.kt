//@file:JvmName("Utils")
package com.github.asforest.util

import java.net.URLDecoder

class Utils
{
    companion object {
        @JvmStatic
        val isPackaged: Boolean get() = javaClass.getResource("").protocol != "file"

        @JvmStatic
        val jarFile: FileObj get() = FileObj(URLDecoder.decode(Utils.javaClass.protectionDomain.codeSource.location.file, "UTF-8"))

        @JvmStatic
        fun countFiles(directory: FileObj): Int
        {
            var count = 0
            for (f in directory.files)
                count += if(f.isFile) 1 else countFiles(f)
            return count
        }
    }
}