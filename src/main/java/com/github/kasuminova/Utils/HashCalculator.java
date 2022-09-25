package com.github.kasuminova.Utils;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * 公用 Hash 计算类
 */
public class HashCalculator {
    /**
     * 计算文件 SHA1
     * @param file 目标文件
     * @return 文件的sha1
     **/
    public static String getSHA1(File file) throws NoSuchAlgorithmException, IOException {
        FileChannel channel = FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(chooseBufferSize(file.length()));
        int len;
        MessageDigest md = MessageDigest.getInstance("SHA1");
        while ((len = channel.read(buffer)) > 0) {
            md.update(buffer.array(), 0, len);
            buffer.clear();
        }
        channel.close();
        return new BigInteger(1, md.digest()).toString(16);
    }

    /**
     * 计算文件 CRC32
     * @param file 目标文件
     * @return 文件的crc32
     **/
    public static String getCRC32(File file) throws IOException {
        FileChannel channel = FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(chooseBufferSize(file.length()));
        int len;
        CRC32 crc32 = new CRC32();
        while ((len = channel.read(buffer)) > 0) {
            crc32.update(buffer.array(), 0, len);
            buffer.clear();
        }
        channel.close();
        return String.valueOf(crc32.getValue());
    }

    /**
     * 计算文件 MD5
     * @param file 目标文件
     * @return 文件的md5
     **/
    public static String getMD5(File file) throws IOException, NoSuchAlgorithmException {
        FileChannel channel = FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(chooseBufferSize(file.length()));
        int len;
        MessageDigest md = MessageDigest.getInstance("MD5");
        while ((len = channel.read(buffer)) > 0) {
            md.update(buffer.array(), 0, len);
            buffer.clear();
        }
        channel.close();
        return new BigInteger(1, md.digest()).toString(16);
    }


    /**
     * 根据文件大小选择合适的缓冲区大小
     * @param size 文件大小
     * @return 缓冲区大小
     */
    private static int chooseBufferSize(long size) {
        int kb = 1024;
        int mb = 1024 * 1024;
        int gb = 1024 * 1024 * 1024;

        if (size <= 1 * kb) {
            return 1 * kb;
        } else if (size <= 1 * mb) {
            return 32 * kb;
        } else if (size <= 128 * mb) {
            return 512 * kb;
        } else if (size <= 512 * mb){
            return 4 * mb;
        } else {
            return 16 * mb;
        }
    }
}