package com.github.balloonupdate.gui

import com.github.balloonupdate.event.Event
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import javax.swing.*

class MainWin
{
    var window = JFrame()
    var stateLabel = JLabel()
    var progressBar1 = JProgressBar()
    var progressBar2 = JProgressBar()

    var titleTextSuffix = ""

    val onWindowClosing = Event<MainWin>()

    constructor()
    {
        // 主窗口
        window.run {
            title = "文件更新助手"
            isUndecorated = false
//            isAlwaysOnTop = true
//            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            contentPane.layout = null
            isVisible = false
//            isResizable = false
            setSize(325, 140)
            setLocationRelativeTo(null)
        }

        window.contentPane.run {
            add(stateLabel.apply { setBounds(10, 5, 280, 20); horizontalAlignment = JLabel.CENTER })
            add(JLabel("当前进度").apply { setBounds(5, 30, 80, 20) })
            add(JLabel("总进度").apply { setBounds(18, 65, 80, 20) })
            add(progressBar1).apply { setBounds(60, 30, 240, 25) }
            add(progressBar2.apply { setBounds(60, 65, 240, 25) })
        }

        progressBar1.isStringPainted = true // 用字符串代替进度百分比
        progressBar1.maximum = 1000 // 设置进度条最大值
        progressBar2.isStringPainted = true // 用字符串代替进度百分比
        progressBar2.maximum = 1000 // 设置进度条最大值

        window.addWindowListener(object : WindowListener {
            override fun windowOpened(e: WindowEvent?) { }

            override fun windowClosing(e: WindowEvent?) {
                onWindowClosing.invoke(this@MainWin)
            }

            override fun windowClosed(e: WindowEvent?) { }

            override fun windowIconified(e: WindowEvent?) { }

            override fun windowDeiconified(e: WindowEvent?) { }

            override fun windowActivated(e: WindowEvent?) { }

            override fun windowDeactivated(e: WindowEvent?) {}

        })
    }

    /**
     * 进度条上方的文本
     */
    var stateText: String
        get() = stateLabel.text
        set(value) = run { stateLabel.text = value; stateLabel.toolTipText = value }

    /**
     * 上方进度条的值
     */
    var progress1value: Int
        get() = progressBar1.value
        set(value) = run { progressBar1.value = value }

    /**
     * 下方进度条的值
     */
    var progress2value: Int
        get() = progressBar2.value
        set(value) = run { progressBar2.value = value }

    /**
     * 上方进度条的文本
     */
    var progress1text: String
        get() = progressBar1.string
        set(value) = run { progressBar1.string = value }

    /**
     * 下方进度条的值
     */
    var progress2text: String
        get() = progressBar2.string
        set(value) = run { progressBar2.string = value }

    /**
     * 标题栏文字
     */
    var titleText: String
        get() = window.title
        set(value) = run { window.title = value + titleTextSuffix }

    fun show() = window.run { isVisible = true }

    fun hide() = window.run { isVisible = false }

    fun destroy() = window.dispose()

    companion object {
        @JvmStatic
        fun main(args: Array<String>)
        {
            MainWin()
        }
    }
}