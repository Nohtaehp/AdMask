package com.admask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后按用户设置自动恢复遮罩 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val prefs = OverlayPrefs(context)
        if (prefs.enabled && prefs.startOnBoot) {
            OverlayService.start(context)
        }
    }
}
