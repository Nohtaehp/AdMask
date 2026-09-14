package com.admask.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 遮罩绘制控制器：绘制顶部 / 底部遮挡条。
 *
 * 窗口固定在无障碍悬浮层（TYPE_ACCESSIBILITY_OVERLAY）上，
 * 层级高于普通悬浮窗，小米等 ROM 上不易被应用盖住。
 *
 * 遮罩窗口 FLAG_NOT_TOUCHABLE，不接收任何触摸事件，不影响正常阅读与翻页。
 */
class MaskController(
    private val context: Context,
    private val wm: WindowManager
) {

    private val prefs = OverlayPrefs(context)
    private val handler = Handler(Looper.getMainLooper())

    private var bottomBar: View? = null
    private var bottomParams: WindowManager.LayoutParams? = null
    private var topBar: View? = null
    private var topParams: WindowManager.LayoutParams? = null

    private var controlBar: View? = null
    private var editTargetIsTop = false
    private val hideControlRunnable = Runnable { hideControls() }

    private var watchRunnable: Runnable? = null
    private var lastForegroundPkg: String? = null

    /** 主服务最后存活状态，用于只在状态翻转时处理一次 */
    private var lastAlive = true

    // ---------------------------------------------------------------- 对外接口

    /** 全量刷新：按当前配置重建 / 更新窗口 */
    fun refresh() {
        if (!shouldDraw()) {
            removeAll()
            return
        }
        lastAlive = true

        if (prefs.bottomEnabled) showBar(isTop = false) else removeBar(isTop = false)
        if (prefs.topEnabled) showBar(isTop = true) else removeBar(isTop = true)
        refreshControlText()
        startAppWatch()
    }

    /** 轻量守护：只同步差异，不重建未变化的窗口 */
    fun guardCheck() {
        if (!shouldDraw()) {
            removeAll()
            return
        }
        val wasAlive = lastAlive
        lastAlive = true

        if (prefs.bottomEnabled && bottomBar == null) showBar(isTop = false)
        if (!prefs.bottomEnabled && bottomBar != null) removeBar(isTop = false)
        if (prefs.topEnabled && topBar == null) showBar(isTop = true)
        if (!prefs.topEnabled && topBar != null) removeBar(isTop = true)
        if (!wasAlive || watchRunnable == null) startAppWatch()
    }

    /** 先移除再重新添加，提升层级、修复被系统丢弃的情况 */
    fun remount() {
        if (!shouldDraw()) {
            removeAll()
            return
        }
        removeBar(isTop = true)
        removeBar(isTop = false)
        refresh()
    }

    fun removeAll() {
        stopAppWatch()
        hideControls()
        removeBar(isTop = true)
        removeBar(isTop = false)
    }

    fun showControls() {
        if (!prefs.enabled) return
        if (controlBar != null) {
            refreshControlText()
            resetControlTimer()
            return
        }
        editTargetIsTop = !(prefs.bottomEnabled)

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_control, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = context.dp(4)
        }
        runCatching { wm.addView(view, params) }.onFailure { return }
        controlBar = view

        view.findViewById<View>(R.id.btn_switch).setOnClickListener {
            editTargetIsTop = !editTargetIsTop
            refreshControlText()
            resetControlTimer()
        }
        view.findViewById<View>(R.id.btn_up).setOnClickListener { adjustOffset(+2) }
        view.findViewById<View>(R.id.btn_down).setOnClickListener { adjustOffset(-2) }
        view.findViewById<View>(R.id.btn_inc).setOnClickListener { adjustHeight(+2) }
        view.findViewById<View>(R.id.btn_dec).setOnClickListener { adjustHeight(-2) }
        view.findViewById<View>(R.id.btn_done).setOnClickListener { hideControls() }

        bottomBar?.findViewById<View>(R.id.bar_border)?.visibility = View.VISIBLE
        topBar?.findViewById<View>(R.id.bar_border)?.visibility = View.VISIBLE
        refreshControlText()
        resetControlTimer()
    }

    fun hideControls() {
        handler.removeCallbacks(hideControlRunnable)
        controlBar?.let { runCatching { wm.removeView(it) } }
        controlBar = null
        bottomBar?.findViewById<View>(R.id.bar_border)?.visibility = View.GONE
        topBar?.findViewById<View>(R.id.bar_border)?.visibility = View.GONE
    }

    // ---------------------------------------------------------------- 遮罩窗口

    private fun showBar(isTop: Boolean) {
        val existing = if (isTop) topBar else bottomBar
        if (existing == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_bar, null)
            val params = buildParams(isTop)
            runCatching { wm.addView(view, params) }.onFailure { return }
            if (isTop) {
                topBar = view
                topParams = params
            } else {
                bottomBar = view
                bottomParams = params
            }
        }
        updateBar(isTop)
    }

    private fun updateBar(isTop: Boolean) {
        val view = (if (isTop) topBar else bottomBar) ?: return
        val params = (if (isTop) topParams else bottomParams) ?: return

        val heightPx = context.dp(if (isTop) prefs.topHeight else prefs.bottomHeight)
        val offsetPx = context.dp(if (isTop) prefs.topOffset else prefs.bottomOffset)
        val widthPx =
            (context.resources.displayMetrics.widthPixels * prefs.widthPercent / 100f).roundToInt()

        params.width = widthPx
        params.height = heightPx
        params.y = offsetPx
        params.gravity =
            if (isTop) Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        // 窗口自身保持不透明，避免部分 ROM 合成时出现"半透明黑"
        params.alpha = 1f
        params.format =
            if (prefs.alphaPercent >= 100) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

        runCatching { wm.updateViewLayout(view, params) }

        val body = view.findViewById<View>(R.id.bar_body)
        applyBodyStyle(body)

        val border = view.findViewById<View>(R.id.bar_border)
        border.visibility = if (controlBar != null) View.VISIBLE else View.GONE
    }

    /** 背景色用 ARGB 编码透明度，View.alpha 恒为 1 */
    private fun applyBodyStyle(body: View) {
        val base = prefs.color
        val alpha255 = (255f * prefs.alphaPercent / 100f).roundToInt()
        body.setBackgroundColor(
            Color.argb(alpha255, Color.red(base), Color.green(base), Color.blue(base))
        )
        body.alpha = 1f
    }

    private fun removeBar(isTop: Boolean) {
        val view = (if (isTop) topBar else bottomBar) ?: return
        runCatching { wm.removeView(view) }
        if (isTop) {
            topBar = null
            topParams = null
        } else {
            bottomBar = null
            bottomParams = null
        }
    }

    private fun buildParams(isTop: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            0,
            0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            alpha = 1f
            gravity =
                if (isTop) Gravity.TOP or Gravity.CENTER_HORIZONTAL
                else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

    // -------------------------------------------------------------- 控制条

    private fun resetControlTimer() {
        handler.removeCallbacks(hideControlRunnable)
        handler.postDelayed(hideControlRunnable, CONTROL_AUTO_HIDE_MS)
    }

    private fun adjustOffset(delta: Int) {
        if (editTargetIsTop) prefs.topOffset = prefs.topOffset + delta
        else prefs.bottomOffset = prefs.bottomOffset + delta
        updateBar(editTargetIsTop)
        refreshControlText()
        resetControlTimer()
    }

    private fun adjustHeight(delta: Int) {
        if (editTargetIsTop) prefs.topHeight = prefs.topHeight + delta
        else prefs.bottomHeight = prefs.bottomHeight + delta
        updateBar(editTargetIsTop)
        refreshControlText()
        resetControlTimer()
    }

    private fun refreshControlText() {
        val view = controlBar ?: return
        val target = view.findViewById<TextView>(R.id.tv_target)
        val info = view.findViewById<TextView>(R.id.tv_info)
        val visible = editTargetIsTop && prefs.topEnabled || !editTargetIsTop && prefs.bottomEnabled
        target.text = if (editTargetIsTop) "顶部" else "底部"
        target.alpha = if (visible) 1f else 0.35f
        val h = if (editTargetIsTop) prefs.topHeight else prefs.bottomHeight
        val o = if (editTargetIsTop) prefs.topOffset else prefs.bottomOffset
        info.text = "${h}dp\n+$o"
    }

    // ------------------------------------------------------------ 应用白名单

    private fun startAppWatch() {
        stopAppWatch()
        if (!prefs.appFilterEnabled) {
            bottomBar?.visibility = View.VISIBLE
            topBar?.visibility = View.VISIBLE
            return
        }
        val allowed = prefs.selectedApps
        if (allowed.isEmpty()) {
            bottomBar?.visibility = View.GONE
            topBar?.visibility = View.GONE
            return
        }
        val task = object : Runnable {
            override fun run() {
                // 前台应用包名由无障碍服务的窗口事件提供
                val current = eventForegroundPkg
                val show = if (current == null) true else current in allowed
                val vis = if (show) View.VISIBLE else View.GONE
                bottomBar?.visibility = vis
                topBar?.visibility = vis
                lastForegroundPkg = current
                handler.postDelayed(this, WATCH_INTERVAL_MS)
            }
        }
        watchRunnable = task
        handler.post(task)
    }

    private fun stopAppWatch() {
        watchRunnable?.let { handler.removeCallbacks(it) }
        watchRunnable = null
    }

    /** 当前是否应该由本控制器绘制 */
    private fun shouldDraw(): Boolean {
        if (!prefs.enabled) return false
        // 以主服务心跳判断主进程是否还在运行，
        // 进程被杀后系统重启无障碍服务时不会继续绘制。
        if (!isHostAlive()) {
            lastAlive = false
            return false
        }
        return true
    }

    /** 主服务是否存活（依据心跳时间戳） */
    private fun isHostAlive(): Boolean {
        val last = prefs.heartbeatAt
        if (last == 0L) return false
        return System.currentTimeMillis() - last < SERVICE_ALIVE_TIMEOUT_MS
    }

    companion object {
        private const val WATCH_INTERVAL_MS = 1000L
        private const val CONTROL_AUTO_HIDE_MS = 20_000L
        private const val SERVICE_ALIVE_TIMEOUT_MS = 15_000L

        /** 由无障碍服务写入的最近前台包名 */
        @Volatile
        var eventForegroundPkg: String? = null
    }
}
