package com.github.kasuminova.MultiThreadDownloader;

import com.github.kasuminova.GUI.VFlowLayout;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class DownloadFrame {
    private final JFrame downloadFrame = new JFrame("下载中");
    private final JPanel downloadListPanel = new JPanel(new VFlowLayout());
    private final JLabel totalSpeedLabel = new JLabel("总速度: 0 Byte/s");
    private final JLabel fileCountLabel = new JLabel("已完成 / 总文件: 0 / 0");
    private final JProgressBar totalProgressBar = new JProgressBar(0,1000);
    public JProgressBar getTotalProgressBar() {
        return totalProgressBar;
    }
    public void setTotalSpeedLabelText(String totalSpeed) {
        totalSpeedLabel.setText(totalSpeed);
    }
    public void setFileCountLabelText(String text) {
        fileCountLabel.setText(text);
    }
    public DownloadFrame() {
        downloadFrame.setSize(new Dimension(650,400));

        JScrollPane downloadListScrollPane = new JScrollPane(
                downloadListPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        downloadListScrollPane.getVerticalScrollBar().setUnitIncrement(35);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(downloadListScrollPane);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new CompoundBorder(new LineBorder(Color.DARK_GRAY), new EmptyBorder(4, 4, 4, 4)));
        statusPanel.add(totalSpeedLabel, BorderLayout.WEST);
        totalProgressBar.setStringPainted(true);
        totalProgressBar.setBorder(new EmptyBorder(0,15,0,15));
        statusPanel.add(totalProgressBar, BorderLayout.CENTER);
        statusPanel.add(fileCountLabel, BorderLayout.EAST);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        downloadFrame.add(mainPanel);
        downloadFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }
    public void showFrame() {
        downloadFrame.setLocationRelativeTo(null);
        downloadFrame.setVisible(true);
    }
    public DownloadProgressComponents addNewProgressBarPanel(String fileName) {
        //单个线程的面板
        JPanel downloadPanel = new JPanel(new VFlowLayout());
        //单个线程的 Box
        Box box = new Box(BoxLayout.LINE_AXIS);
        //文件名 IP
        downloadPanel.setBorder(BorderFactory.createTitledBorder(fileName));
        //状态
        box.add(new JLabel("进度："), BorderLayout.WEST);
        //进度条
        JProgressBar progressBar = new JProgressBar(0,1000);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("连接中...");
        //向 Box 添加进度条
        box.add(progressBar, BorderLayout.EAST);
        downloadPanel.add(box);
        JLabel speedLabel = new JLabel("速度：0 Byte/s");
        downloadPanel.add(speedLabel);

        downloadListPanel.add(downloadPanel);
        downloadListPanel.updateUI();
        return new DownloadProgressComponents(speedLabel, progressBar);
    }
}
