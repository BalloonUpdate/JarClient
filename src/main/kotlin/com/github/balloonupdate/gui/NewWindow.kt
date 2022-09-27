package com.github.balloonupdate.gui

import com.github.balloonupdate.event.Event
import com.github.kasuminova.GUI.VFlowLayout
import java.awt.BorderLayout
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import javax.swing.*

class NewWindow(width: Int, height: Int)
{
    var titleTextSuffix = ""

    private var window = JFrame()
    private var taskList = JPanel(VFlowLayout())
    private val taskListScroll = JScrollPane(taskList).apply { verticalScrollBar.unitIncrement = 22 }
    private val statusBar = JPanel(BorderLayout()).apply { border = BorderFactory.createEmptyBorder(4, 4, 4, 4) }
    private val statusBarLabel = JLabel("sd").also { statusBar.add(it, BorderLayout.WEST) }.apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 8) }
    private var statusBarProgressBar = JProgressBar(0, 1000).apply { isStringPainted = true }.also { statusBar.add(it, BorderLayout.CENTER) }

    val onWindowClosing = Event<NewWindow>()

    val taskRowMutex = Any()

    init {
        window.isUndecorated = false
        window.contentPane.layout = BorderLayout()
        window.isVisible = false
        window.setSize(width, height)
        window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        window.setLocationRelativeTo(null)
        window.add(taskListScroll, BorderLayout.CENTER)
        window.add(statusBar, BorderLayout.SOUTH)

        window.addWindowListener(object : WindowListener {
            override fun windowOpened(e: WindowEvent?) { }
            override fun windowClosing(e: WindowEvent?) {
                onWindowClosing.invoke(this@NewWindow)
            }
            override fun windowClosed(e: WindowEvent?) { }
            override fun windowIconified(e: WindowEvent?) { }
            override fun windowDeiconified(e: WindowEvent?) { }
            override fun windowActivated(e: WindowEvent?) { }
            override fun windowDeactivated(e: WindowEvent?) {}
        })
    }

    /**
     * 创建一个进度条行
     * @return 进度条行的引用
     */
    fun createTaskRow(): TaskRow
    {
        synchronized(taskRowMutex) {
            val taskRowPanel = JPanel(VFlowLayout())
            val box = Box(BoxLayout.LINE_AXIS)
            val label = JLabel().also { box.add(it, BorderLayout.WEST) }.apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 8) }
            val progress = JProgressBar(0, 1000).apply { isStringPainted = true }.also { box.add(it, BorderLayout.CENTER) }
            taskRowPanel.border = BorderFactory.createTitledBorder("")
            taskRowPanel.add(box)

            taskList.add(taskRowPanel)

            return TaskRow(taskRowPanel, label, progress)
        }
    }

    /**
     * 移除一个进度条行
     * @param taskRow 要移除的进度条行的引用
     */
    fun destroyTaskRow(taskRow: TaskRow)
    {
        synchronized(taskRowMutex) {
            taskList.remove(taskRow.rowPanel)
            taskList.updateUI()
        }
    }

    /**
     * 标题栏文字
     */
    var titleText: String
        get() = window.title
        set(value) = run { window.title = value + titleTextSuffix }

    /**
     * 显示窗口
     */
    fun show() = window.run { isVisible = true }

    /**
     * 隐藏窗口
     */
    fun hide() = window.run { isVisible = false }

    /**
     * 销毁窗口
     */
    fun destroy() = window.dispose()

    /**
     * 状态栏文本标签的值
     */
    var statusBarText: String
        get() = statusBarLabel.text
        set(value) = run { statusBarLabel.text = value }

    /**
     * 状态栏进度条的值
     */
    var statusBarProgressValue: Int
        get() = statusBarProgressBar.value
        set(value) = run { statusBarProgressBar.value = value }

    /**
     * 状态栏进度条的上的文本
     */
    var statusBarProgressText: String
        get() = statusBarProgressBar.string
        set(value) = run { statusBarProgressBar.string = value }

    /**
     * 进度条行，表示一个进度条面板中的相关UI对象的引用，相当于一个句柄
     */
    class TaskRow(
        val rowPanel: JPanel,
        val label: JLabel,
        val progress: JProgressBar,
    ) {
        private var borderTextCache = ""

        /**
         * 读写标签文本
         */
        var labelText: String
            get() = label.text
            set(value) = run { label.text = value }

        /**
         * 读写进度条的文本
         */
        var progressBarLabel: String
            get() = progress.string
            set(value) = run { progress.string = value }


        /**
         * 读写进度条的值
         */
        var progressBarValue: Int
            get() = progress.value
            set(value) = run { progress.value = value }

        /**
         * 读写边框上的文字
         */
        var borderText: String
            get() = borderTextCache
            set(value) = run {
                if (borderTextCache != value)
                {
                    rowPanel.border = BorderFactory.createTitledBorder(value)
                    borderTextCache = value
                }
            }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>)
        {
            val win = NewWindow(450, 315)
            win.window.isVisible = true

            val tr = win.createTaskRow()
            tr.labelText = "dasdsadsadsad"

//            win.destroyTaskRow(tr)
        }
    }
}