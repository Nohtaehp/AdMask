package com.admask.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 常驻前台服务：只负责保活、心跳与指令转发，
 * 遮罩由 [MaskAccessibilityService] 绘制。
 *
 * 心跳同时作为"用户是否开启遮罩"的依据：
 * 本服务停止后，无障碍服务会在 15 秒内自动撤掉遮罩。
 */
class OverlayService : Service() {

    private lateinit var prefs: OverlayPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP -> {
                    prefs.enabled = false
                    MaskAccessibilityService.refresh()
                    stopSelf()
                }
                ACTION_UPDATE -> MaskAccessibilityService.refresh()
                ACTION_EDIT -> MaskAccessibilityService.showControls()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT ->
                    MaskAccessibilityService.remount()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        alive = true
        prefs = OverlayPrefs(this)
        createChannel()
        runCatching { startForeground(NOTI_ID, buildNotification()) }
        startHeartbeat()
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter().apply {
                addAction(ACTION_STOP)
                addAction(ACTION_UPDATE)
                addAction(ACTION_EDIT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = object : Runnable {
            override fun run() {
                prefs.heartbeatAt = System.currentTimeMillis()
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        handler.post(heartbeatRunnable!!)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 划掉任务后不自动重启，让遮罩随用户意图消失
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MaskAccessibilityService.refresh()
        if (intent?.action == ACTION_EDIT) {
            MaskAccessibilityService.showControls()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MaskAccessibilityService.refresh()
    }

    override fun onDestroy() {
        alive = false
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
        runCatching { unregisterReceiver(commandReceiver) }
        MaskAccessibilityService.refresh()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 通知

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mask)
            .setContentTitle(getString(R.string.noti_title))
            .setContentText(getString(R.string.noti_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(Color.parseColor("#4F46E5"))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.noti_action_edit), pendingBroadcast(ACTION_EDIT, 1))
            .addAction(0, getString(R.string.noti_action_stop), pendingBroadcast(ACTION_STOP, 2))
            .build()
    }

    private fun pendingBroadcast(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        @Volatile
        internal var alive = false

        const val ACTION_STOP = "com.admask.app.ACTION_STOP"
        const val ACTION_UPDATE = "com.admask.app.ACTION_UPDATE"
        const val ACTION_EDIT = "com.admask.app.ACTION_EDIT"

        private const val CHANNEL_ID = "admask_status"
        private const val NOTI_ID = 1001
        private const val HEARTBEAT_INTERVAL_MS = 2000L

        /**
         * 本服务当前是否真的在运行。
         *
         * 进程被杀后静态变量随进程一起消失，新进程中一定是 false，
         * 因此它可以准确区分"界面记录为开启"与"服务实际在跑"。
         */
        fun isRunning(): Boolean = alive

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun update(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_UPDATE)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 停止遮罩：先广播让绘制方移除，再停服务 */
        fun stop(context: Context) {
            context.sendBroadcast(Intent(ACTION_STOP).setPackage(context.packageName))
            runCatching { context.stopService(Intent(context, OverlayService::class.java)) }
        }

        fun edit(context: Context) {
            context.sendBroadcast(Intent(ACTION_EDIT).setPackage(context.packageName))
        }
    }
}
