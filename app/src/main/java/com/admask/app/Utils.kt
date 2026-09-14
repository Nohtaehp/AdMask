package com.admask.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import kotlin.math.roundToInt

/** dp 转 px */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** 只关心最终文本的 TextWatcher */
fun EditText.afterTextChanged(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            block(s?.toString().orEmpty())
        }
    })
}

/**
 * 尽量直达本应用无障碍服务的开关详情页，
 * 失败时退回无障碍列表页。
 */
fun Context.openAccessibilityServiceDetail(serviceClass: Class<*>) {
    val component = ComponentName(packageName, serviceClass.name)
    // 优先直达本服务的详情页（不同 ROM 支持程度不同，失败则退回列表页）
    val direct = runCatching {
        startActivity(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
    if (!direct) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** 是否已加入电池优化白名单（保证后台不被系统杀掉） */
fun Context.isIgnoringBatteryOptimization(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(packageName)
}

/** 申请加入电池优化白名单 */
fun Context.requestIgnoreBatteryOptimization() {
    if (isIgnoringBatteryOptimization()) return
    runCatching {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
