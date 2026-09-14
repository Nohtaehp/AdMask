package com.admask.app

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** 快捷设置磁贴：一键开关遮罩 */
@RequiresApi(Build.VERSION_CODES.N)
class MaskTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 后台启动前台服务受限，交由 Activity 在前台完成切换
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_TOGGLE, true)
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
            return
        }
        val prefs = OverlayPrefs(this)
        // 以服务真实状态为准，进程被杀后 recorded 的 enabled 可能已失效
        val on = prefs.enabled && OverlayService.isRunning()
        prefs.enabled = !on
        if (prefs.enabled) OverlayService.start(this) else OverlayService.stop(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = OverlayPrefs(this).enabled && OverlayService.isRunning()
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
