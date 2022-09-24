package com.github.kasuminova.GUI;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkContrastIJTheme;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kasumi_Nova
 * 一个工具类，用于初始化 Swing（即美化）
 */
public class SetupSwing {
    public static void init() {
        //抗锯齿字体
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        //UI 配置线程
        Thread uiThread = new Thread(() -> {
            //设置圆角弧度
            UIManager.put("Button.arc", 7);
            UIManager.put("Component.arc", 7);
            UIManager.put("ProgressBar.arc", 7);
            UIManager.put("TextComponent.arc", 5);
            UIManager.put("CheckBox.arc", 3);
            //设置滚动条
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.thumbArc", 7);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2,2,2,2));
            UIManager.put("ScrollBar.track", new Color(0,0,0,0));
            //选项卡分隔线/背景
            UIManager.put("TabbedPane.showTabSeparators", true);
        });
        uiThread.start();

        Thread themeThread = new Thread(() -> {
            //更新 UI
            try {
                UIManager.setLookAndFeel(new FlatAtomOneDarkContrastIJTheme());
            } catch (Exception e) {
                System.err.println("Failed to initialize LaF");
                e.printStackTrace();
            }
        });
        themeThread.start();

        try {
            uiThread.join();
            themeThread.join();
        } catch (InterruptedException ignored) {}
    }
}
