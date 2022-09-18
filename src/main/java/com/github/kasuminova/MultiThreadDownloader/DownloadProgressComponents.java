package com.github.kasuminova.MultiThreadDownloader;

import javax.swing.*;

public class DownloadProgressComponents {
    public JLabel speedLabel;
    public JProgressBar progressBar;
    public DownloadProgressComponents(JLabel speedLabel, JProgressBar progressBar) {
        this.speedLabel = speedLabel;
        this.progressBar = progressBar;
    }
}
