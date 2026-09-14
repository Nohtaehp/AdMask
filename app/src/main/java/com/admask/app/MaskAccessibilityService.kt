package com.admask.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/**
 * 无障碍悬浮层服务：负责绘制遮罩。
 *
 * 监听窗口切换事件得到当前前台包名（用于应用过滤），不读取任何屏幕内容。
 */
class MaskAccessibilityService : AccessibilityService() {

    private lateinit var controller: MaskController
    private val handler = Handler(Looper.getMainLooper())
    private var guardRunnable: Runnable? = null
    private var receiverRegistered = false

    /** 独立接收控制指令，保证主服务不在时也能停止 / 更新遮罩 */
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!::controller.isInitialized) return
            when (intent.action) {
                OverlayService.ACTION_STOP -> {
                    OverlayPrefs(context).enabled = false
                    controller.removeAll()
                }
                OverlayService.ACTION_UPDATE -> controller.refresh()
                OverlayService.ACTION_EDIT -> controller.showControls()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> controller.remount()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 整段兜底：服务进程内抛异常会让系统判定服务异常并可能禁用它，
        // 这里保证初始化失败也只是"这次没画出来"，不影响服务继续绑定
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            controller = MaskController(this, wm)
            instance = this
            ContextCompat.registerReceiver(
                this,
                commandReceiver,
                IntentFilter().apply {
                    addAction(OverlayService.ACTION_STOP)
                    addAction(OverlayService.ACTION_UPDATE)
                    addAction(OverlayService.ACTION_EDIT)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            startGuard()
            controller.refresh()
        }
    }

    /** 定时检查主服务心跳，主进程被杀后自动撤掉遮罩 */
    private fun startGuard() {
        guardRunnable?.let { handler.removeCallbacks(it) }
        guardRunnable = object : Runnable {
            override fun run() {
                if (::controller.isInitialized) controller.guardCheck()
                handler.postDelayed(this, GUARD_INTERVAL_MS)
            }
        }
        handler.post(guardRunnable!!)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            val pkg = event?.packageName?.toString()
            if (!pkg.isNullOrEmpty()) MaskController.eventForegroundPkg = pkg
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        guardRunnable?.let { handler.removeCallbacks(it) }
        guardRunnable = null
        if (receiverRegistered) {
            runCatching { unregisterReceiver(commandReceiver) }
            receiverRegistered = false
        }
        if (::controller.isInitialized) controller.removeAll()
        instance = null
        super.onDestroy()
    }

    internal fun doRefresh() {
        if (::controller.isInitialized) controller.refresh()
    }

    internal fun doShowControls() {
        if (::controller.isInitialized) controller.showControls()
    }

    internal fun doRemount() {
        if (::controller.isInitialized) controller.remount()
    }

    companion object {
        private const val GUARD_INTERVAL_MS = 2000L

        @Volatile
        private var instance: MaskAccessibilityService? = null

        /** 系统无障碍设置里是否已开启本服务 */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager ?: return false
            val enabled = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            return enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }

        /** 优先直达本服务的开关详情页，减少点击次数 */
        fun openSettings(context: Context) {
            context.openAccessibilityServiceDetail(MaskAccessibilityService::class.java)
        }

        fun refresh() {
            instance?.doRefresh()
        }

        fun showControls() {
            instance?.doShowControls()
        }

        /** 屏幕点亮等场景下重新挂载窗口，避免被系统降级或丢弃 */
        fun remount() {
            instance?.doRemount()
        }
    }
}
