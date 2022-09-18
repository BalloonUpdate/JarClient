package com.github.kasuminova.Utils;

public class FileUtil {
    /**
     * 根据传入大小返回合适的大小格式化文本
     * @param size 文件大小
     * @return Byte 或 KB 或 MB 或 GB
     */
    public static String formatFileSizeToStr(long size) {
        if (size <= 1024) {
            return size + " Byte";
        } else if (size <= 1024 * 1024) {
            return String.format("%.2f", (double) size / 1024) + " KB";
        } else if (size <= 1024 * 1024 * 1024) {
            return String.format("%.2f", (double) size / (1024 * 1024)) + " MB";
        } else {
            return String.format("%.2f", (double) size / (1024 * 1024 * 1024)) + " GB";
        }
    }

    /**
     * 根据传入大小返回合适的 int 大小
     * 适用于网络文件上传
     * @param size 文件大小
     * @return 根据大小适应的 int 大小
     */
    public static int formatFileSizeInt(long size) {
        if (size <= 1024) {
            return 1024;
        } else if (size <= 1024 * 1024) {
            return 1024 * 8;
        } else if (size <= 1024 * 1024 * 128) {
            return 1024 * 64;
        } else if (size <= 1024 * 1024 * 512){
            return 1024 * 1024;
        } else {
            return 1024 * 1024 * 8;
        }
    }
}
