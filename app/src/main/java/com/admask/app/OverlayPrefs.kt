package com.admask.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * 所有用户配置的读写入口。
 */
class OverlayPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 总开关 */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 底部遮罩开关 */
    var bottomEnabled: Boolean
        get() = sp.getBoolean(KEY_BOTTOM_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_BOTTOM_ENABLED, value).apply()

    /** 底部遮罩高度 dp */
    var bottomHeight: Int
        get() = sp.getInt(KEY_BOTTOM_HEIGHT, 60)
        set(value) = sp.edit().putInt(KEY_BOTTOM_HEIGHT, value.coerceIn(0, MAX_SIZE)).apply()

    /** 底部遮罩距屏幕底边向上的偏移 dp */
    var bottomOffset: Int
        get() = sp.getInt(KEY_BOTTOM_OFFSET, 0)
        set(value) = sp.edit().putInt(KEY_BOTTOM_OFFSET, value.coerceIn(0, MAX_OFFSET)).apply()

    /** 顶部遮罩开关 */
    var topEnabled: Boolean
        get() = sp.getBoolean(KEY_TOP_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_TOP_ENABLED, value).apply()

    /** 顶部遮罩高度 dp */
    var topHeight: Int
        get() = sp.getInt(KEY_TOP_HEIGHT, 60)
        set(value) = sp.edit().putInt(KEY_TOP_HEIGHT, value.coerceIn(0, MAX_SIZE)).apply()

    /** 顶部遮罩距屏幕顶边向下的偏移 dp */
    var topOffset: Int
        get() = sp.getInt(KEY_TOP_OFFSET, 0)
        set(value) = sp.edit().putInt(KEY_TOP_OFFSET, value.coerceIn(0, MAX_OFFSET)).apply()

    /** 遮罩宽度占屏幕宽度的百分比 */
    var widthPercent: Int
        get() = sp.getInt(KEY_WIDTH, 100)
        set(value) = sp.edit().putInt(KEY_WIDTH, value.coerceIn(20, 100)).apply()

    /** 遮罩颜色 */
    var color: Int
        get() = sp.getInt(KEY_COLOR, Color.BLACK)
        set(value) = sp.edit().putInt(KEY_COLOR, value).apply()

    /** 不透明度百分比 */
    var alphaPercent: Int
        get() = sp.getInt(KEY_ALPHA, 100)
        set(value) = sp.edit().putInt(KEY_ALPHA, value.coerceIn(10, 100)).apply()

    /** 是否只在选定的应用中显示 */
    var appFilterEnabled: Boolean
        get() = sp.getBoolean(KEY_APP_FILTER, false)
        set(value) = sp.edit().putBoolean(KEY_APP_FILTER, value).apply()

    /** 选定的应用包名集合 */
    var selectedApps: Set<String>
        get() = sp.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_SELECTED_APPS, value).apply()

    /** 开机自动开启遮罩 */
    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, false)
        set(value) = sp.edit().putBoolean(KEY_BOOT, value).apply()

    /** 服务心跳时间戳，用于判断服务是否存活 */
    var heartbeatAt: Long
        get() = sp.getLong(KEY_HEARTBEAT, 0L)
        set(value) = sp.edit().putLong(KEY_HEARTBEAT, value).apply()

    companion object {
        const val MAX_SIZE = 400
        const val MAX_OFFSET = 300

        private const val PREF_NAME = "admask_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BOTTOM_ENABLED = "bottom_enabled"
        private const val KEY_BOTTOM_HEIGHT = "bottom_height"
        private const val KEY_BOTTOM_OFFSET = "bottom_offset"
        private const val KEY_TOP_ENABLED = "top_enabled"
        private const val KEY_TOP_HEIGHT = "top_height"
        private const val KEY_TOP_OFFSET = "top_offset"
        private const val KEY_WIDTH = "width_percent"
        private const val KEY_COLOR = "color"
        private const val KEY_ALPHA = "alpha_percent"
        private const val KEY_APP_FILTER = "app_filter_enabled"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_BOOT = "start_on_boot"
        private const val KEY_HEARTBEAT = "heartbeat_at"
    }
}
