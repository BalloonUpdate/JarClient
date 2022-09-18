package com.github.kasuminova.MultiThreadDownloader;

import java.io.File;

/**
 * DownloadObject, 用于存储下载文件的链接和保存路径
 */
public class DownloadObject {
    public String url;
    public File target;
    public DownloadObject(String url, File target) {
        this.url = url;
        this.target = target;
    }
}
