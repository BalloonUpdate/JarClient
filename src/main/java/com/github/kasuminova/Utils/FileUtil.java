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
     * 根据传入大小返回合适的 byte[]
     * 适用于 FileInputStream 之类的环境
     * @param size 文件大小
     * @return 根据大小适应的 byte[] 大小
     */
    public static byte[] formatFileSizeByte(long size) {
        if (size <= 1024) {
            return new byte[1024];
        } else if (size <= 1024 * 1024) {
            return new byte[1024 * 64];
        } else if (size <= 1024 * 1024 * 128) {
            return new byte[1024 * 256];
        } else if (size <= 1024 * 1024 * 512) {
            return new byte[1024 * 1024];
        } else {
            return new byte[1024 * 1024 * 8];
        }
    }
}
