package com.github.kasuminova.MultiThreadDownloader;

import com.github.kasuminova.Utils.FileUtil;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FileDownloader implements Runnable {
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private final DownloadObject object;
    private final DownloadFrame downloadFrame;
    private final AtomicLong totalDownloaded;
    private final AtomicLong totalSize;
    private long progress;
    private final AtomicInteger runningThreadCount;
    private final AtomicInteger completedFiles;
    private final int maxThreads;
    private DownloadProgressComponents downloadProgressComponents;
    public FileDownloader(DownloadObject object, DownloadFrame downloadFrame, AtomicLong totalDownloaded, AtomicLong totalSize, AtomicInteger runningThreadCount, AtomicInteger completedFiles, int maxThreads) {
        this.object = object;
        this.downloadFrame = downloadFrame;
        this.totalDownloaded = totalDownloaded;
        this.totalSize = totalSize;
        this.runningThreadCount = runningThreadCount;
        this.completedFiles = completedFiles;
        this.maxThreads = maxThreads;
    }

    @Override
    public void run() {
        while (true) {
            if (runningThreadCount.get() < maxThreads) {
                break;
            } else {
                try {
                    Thread.sleep(250);
                } catch (Exception ignored) {}
            }
        }

        runningThreadCount.getAndIncrement();
        downloadProgressComponents = downloadFrame.addNewProgressBarPanel(object.target.getName());
        startDownload();
    }

    private void startDownload() {
        Request request = new Request.Builder().url(object.url).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!(response.code() == 200)) {
                    downloadProgressComponents.progressBar.setString("HTTP 状态码不正确:" + response.code());
                    Main.threadException = new Exception("HTTP 状态码不正确:" + response.code());
                    response.close();
                    completedFiles.getAndIncrement();
                    runningThreadCount.getAndDecrement();
                    return;
                }
                InputStream InputStream = null;
                byte[] buf = new byte[8192];
                int len;
                FileOutputStream fos = null;
                // 储存下载文件的目录
                File file = object.target;
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }

                try {
                    InputStream = Objects.requireNonNull(response.body()).byteStream();
                    long total = Objects.requireNonNull(response.body()).contentLength();
                    totalSize.addAndGet(total);
                    AtomicLong lastDownloaded = new AtomicLong(0);
                    Timer timer = new Timer(250, e -> {
                        if (progress != 0) {
                            downloadProgressComponents.speedLabel.setText(String.format("速度：%s/s", FileUtil.formatFileSizeToStr((progress - lastDownloaded.get()) * 4)));
                            lastDownloaded.set(progress);

                            downloadProgressComponents.progressBar.setString(String.format(
                                    "%s / %s",
                                    FileUtil.formatFileSizeToStr(progress),
                                    FileUtil.formatFileSizeToStr(total)));
                            downloadProgressComponents.progressBar.setValue((int) (double) (progress * 1000 / total));
                        }
                    });
                    timer.start();
                    downloadProgressComponents.progressBar.setIndeterminate(false);

                    fos = new FileOutputStream(file);
                    while ((len = InputStream.read(buf)) != -1) {
                        fos.write(buf, 0, len);

                        progress += len;
                        totalDownloaded.getAndAdd(len);
                    }
                    fos.flush();
                    // 下载完成
                    timer.stop();
                    //移除面板（极限套娃）
                    downloadProgressComponents.progressBar.getParent().getParent().getParent().remove(downloadProgressComponents.progressBar.getParent().getParent());
                } catch (Exception e) {
                    downloadProgressComponents.progressBar.setString(e.getLocalizedMessage());
                    Main.threadException = e;
                } finally {
                    try {
                        if (InputStream != null)
                            InputStream.close();
                        if (fos != null)
                            fos.close();
                    } catch (IOException e) {
                        Main.threadException = e;
                    }

                    completedFiles.getAndIncrement();
                    runningThreadCount.getAndDecrement();
                    response.close();
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                downloadProgressComponents.progressBar.setString(e.getMessage());
                Main.threadException = e;
                completedFiles.getAndIncrement();
                runningThreadCount.getAndDecrement();
            }
        });
    }
}
