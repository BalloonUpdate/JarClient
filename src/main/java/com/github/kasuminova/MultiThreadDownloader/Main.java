package com.github.kasuminova.MultiThreadDownloader;

import com.github.kasuminova.Utils.FileUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    static Exception threadException;
    /**
     * 从传入的 ArrayList<DownloadObject> 中下载指定文件
     * @param downloadObjects 链接以及保存路径
     * @param maxThreads 并发数量（通常会有 1~2 的波动）
     */
    public static void downloadFilesWithList(ArrayList<DownloadObject> downloadObjects, int maxThreads) throws Exception {
        threadException = null;
        //主窗口
        final DownloadFrame downloadFrame = new DownloadFrame();
        //总文件数量
        final int totalFiles = downloadObjects.size();
        //已完成文件数量
        final AtomicInteger completedFiles = new AtomicInteger(0);
        //正在运行的线程（用于控制线程）
        final AtomicInteger runningThreadCount = new AtomicInteger(0);
        //已下载 Byte
        final AtomicLong totalDownloaded = new AtomicLong(0);
        //总大小（仅计算正在下载和已下载的，位于队列中的不计入）
        final AtomicLong totalSize = new AtomicLong(0);
        ArrayList<Thread> threadList = new ArrayList<>();

        for (DownloadObject downloadObject : downloadObjects) {
            Thread thread = new Thread(new FileDownloader(downloadObject, downloadFrame, totalDownloaded, totalSize, runningThreadCount, completedFiles, maxThreads));
            threadList.add(thread);
            thread.start();
        }

        //计时器变量
        AtomicLong lastDownloaded = new AtomicLong();
        Timer timer = new Timer(250, e -> {
            downloadFrame.setTotalSpeedLabelText(String.format("总速度：%s/s", FileUtil.formatFileSizeToStr((totalDownloaded.get() - lastDownloaded.get()) * 4)));
            lastDownloaded.set(totalDownloaded.get());
            JProgressBar totalProgressBar = downloadFrame.getTotalProgressBar();
            totalProgressBar.setString(String.format("%s / %s",
                    FileUtil.formatFileSizeToStr(totalDownloaded.get()),
                    FileUtil.formatFileSizeToStr(totalSize.get())));
            if (totalSize.get() != 0) totalProgressBar.setValue((int) (double) (totalDownloaded.get() * 1000 / totalSize.get()));
            downloadFrame.setFileCountLabelText(String.format("已完成 / 总文件: %s / %s", completedFiles.get(), totalFiles));

        });
        timer.start();

        downloadFrame.showFrame();

        while (completedFiles.get() < totalFiles) {
            if (threadException != null) {
                for (Thread thread : threadList) {
                    thread.interrupt();
                }
                timer.stop();
                throw threadException;
            }
            Thread.sleep(250);
        }

        timer.stop();
    }
}
