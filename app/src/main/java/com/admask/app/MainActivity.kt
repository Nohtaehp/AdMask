package com.admask.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 主设置界面：开关、尺寸外观、生效范围与无障碍授权引导。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: OverlayPrefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var swEnable: MaterialSwitch
    private lateinit var swBottom: MaterialSwitch
    private lateinit var swTop: MaterialSwitch
    private lateinit var swAppFilter: MaterialSwitch
    private lateinit var swBoot: MaterialSwitch

    private lateinit var seekBottomHeight: SeekBar
    private lateinit var seekBottomOffset: SeekBar
    private lateinit var seekTopHeight: SeekBar
    private lateinit var seekTopOffset: SeekBar
    private lateinit var seekWidth: SeekBar
    private lateinit var seekAlpha: SeekBar

    private lateinit var tvBottomHeight: TextView
    private lateinit var tvBottomOffset: TextView
    private lateinit var tvTopHeight: TextView
    private lateinit var tvTopOffset: TextView
    private lateinit var tvWidth: TextView
    private lateinit var tvAlpha: TextView
    private lateinit var tvNotifyPerm: TextView
    private lateinit var tvBatteryPerm: TextView
    private lateinit var tvAccState: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvSelectedApps: TextView
    private lateinit var tvService: TextView
    private lateinit var tvVersion: TextView
    private lateinit var colorRow: LinearLayout
    private lateinit var colorPicker: ColorPickerView
    private lateinit var colorPreview: View
    private lateinit var etHex: EditText
    private lateinit var etR: EditText
    private lateinit var etG: EditText
    private lateinit var etB: EditText

    private val presetColors = intArrayOf(
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF9E9E9E.toInt(),
        0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(),
        0xFFC2185B.toInt()
    )

    /** 上次请求拉起服务的时间，避免短时间内重复 start */
    private var lastAutoStartAt = 0L

    /** 用户想开启、但所需权限已被系统关闭（如应用被强制停止） */
    private var waitingPermission = false

    private val statusRunnable = object : Runnable {
        override fun run() {
            // 界面停留期间服务若被系统回收，自动拉回，避免"显示开启但没生效"
            if (prefs.enabled && System.currentTimeMillis() - lastAutoStartAt > START_COOLDOWN_MS) {
                reconcileServiceState()
            }
            updateServiceStatus()
            tvAccState.text = getString(
                if (MaskAccessibilityService.isEnabled(this@MainActivity)) R.string.perm_granted
                else R.string.acc_need_enable
            )
            handler.postDelayed(this, 2000)
        }
    }

    private val notifyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果由 onResume 刷新 */ }

    private val appPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshSelectedApps()
            applyChange()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = OverlayPrefs(this)

        resetEnabledOnColdStart()
        bindViews()
        setupColors()
        setupColorPicker()
        setupListeners()
        askNotificationPermissionIfNeeded()

        if (intent?.getBooleanExtra(EXTRA_TOGGLE, false) == true) {
            toggleEnabled()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        reconcileServiceState()
        refreshControls()
        refreshSelectedApps()
        updateSummary()
        handler.removeCallbacks(statusRunnable)
        handler.post(statusRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusRunnable)
    }

    // ------------------------------------------------------------------ 初始化

    /**
     * 冷启动校正：默认关闭「启用遮罩」开关。
     *
     * 首次打开应用、或进程被杀 / 被强制停止后，系统不会保留本应用的无障碍授权，
     * 而持久化的 enabled 仍是 true，界面就会显示"已开启"但遮罩根本没生效。
     * 因此每个新进程第一次进入界面时，只要无障碍服务当前不可用，
     * 就把开关复位为关闭，等用户重新授权后再手动打开。
     */
    private fun resetEnabledOnColdStart() {
        if (!ProcessState.isColdStart()) return
        ProcessState.markReconciled()
        if (!prefs.enabled) return
        if (MaskAccessibilityService.isEnabled(this)) return
        prefs.enabled = false
        prefs.heartbeatAt = 0L
        OverlayService.stop(this)
    }

    private fun bindViews() {
        swEnable = findViewById(R.id.sw_enable)
        swBottom = findViewById(R.id.sw_bottom)
        swTop = findViewById(R.id.sw_top)
        swAppFilter = findViewById(R.id.sw_app_filter)
        swBoot = findViewById(R.id.sw_boot)

        seekBottomHeight = findViewById(R.id.seek_bottom_height)
        seekBottomOffset = findViewById(R.id.seek_bottom_offset)
        seekTopHeight = findViewById(R.id.seek_top_height)
        seekTopOffset = findViewById(R.id.seek_top_offset)
        seekWidth = findViewById(R.id.seek_width)
        seekAlpha = findViewById(R.id.seek_alpha)

        tvBottomHeight = findViewById(R.id.tv_bottom_height)
        tvBottomOffset = findViewById(R.id.tv_bottom_offset)
        tvTopHeight = findViewById(R.id.tv_top_height)
        tvTopOffset = findViewById(R.id.tv_top_offset)
        tvWidth = findViewById(R.id.tv_width)
        tvAlpha = findViewById(R.id.tv_alpha)
        tvNotifyPerm = findViewById(R.id.tv_perm_notify)
        tvBatteryPerm = findViewById(R.id.tv_perm_battery)
        tvAccState = findViewById(R.id.tv_acc_state)
        tvSummary = findViewById(R.id.tv_summary)
        tvSelectedApps = findViewById(R.id.tv_selected_apps)
        tvService = findViewById(R.id.tv_service)
        tvVersion = findViewById(R.id.tv_version)
        colorRow = findViewById(R.id.color_row)
        colorPicker = findViewById(R.id.color_picker)
        colorPreview = findViewById(R.id.view_color_preview)
        etHex = findViewById(R.id.et_hex)
        etR = findViewById(R.id.et_r)
        etG = findViewById(R.id.et_g)
        etB = findViewById(R.id.et_b)
        colorPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), 0x33000000)
        }
        showVersion()
    }

    private fun showVersion() {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val name = info?.versionName ?: "-"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info?.longVersionCode?.toInt() ?: 0
        } else {
            @Suppress("DEPRECATION")
            info?.versionCode ?: 0
        }
        tvVersion.text = getString(R.string.version_label, name, code)
    }

    private fun setupColors() {
        colorRow.removeAllViews()
        val size = dp(40)
        presetColors.forEachIndexed { index, color ->
            val wrapper = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size + dp(8), size + dp(8)).apply {
                    marginEnd = if (index == presetColors.lastIndex) 0 else dp(8)
                }
                gravity = Gravity.CENTER
            }
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), 0x33000000)
                }
            }
            wrapper.addView(dot)
            wrapper.tag = color
            wrapper.setOnClickListener { applyColor(color) }
            colorRow.addView(wrapper)
        }
    }

    // ---------------------------------------------------------------- 颜色选择

    private fun setupColorPicker() {
        colorPicker.setColor(prefs.color)
        colorPicker.onColorChanged = { applyColor(it, syncPicker = false) }

        etHex.afterTextChanged { text ->
            if (!etHex.isFocused) return@afterTextChanged
            parseHexColor(text)?.let { applyColor(it) }
        }
        setupChannelInput(etR) { r, g, b -> Color.rgb(r, g, b) }
        setupChannelInput(etG) { r, g, b -> Color.rgb(r, g, b) }
        setupChannelInput(etB) { r, g, b -> Color.rgb(r, g, b) }

        syncColorInputs(prefs.color)
    }

    /** 单个 RGB 通道输入框：值合法即实时生效 */
    private fun setupChannelInput(edit: EditText, compose: (Int, Int, Int) -> Int) {
        edit.afterTextChanged { text ->
            if (!edit.isFocused) return@afterTextChanged
            val value = text.toIntOrNull() ?: return@afterTextChanged
            if (value !in 0..255) return@afterTextChanged
            val current = prefs.color
            val channels = intArrayOf(
                Color.red(current), Color.green(current), Color.blue(current)
            )
            when (edit.id) {
                R.id.et_r -> channels[0] = value
                R.id.et_g -> channels[1] = value
                R.id.et_b -> channels[2] = value
            }
            applyColor(compose(channels[0], channels[1], channels[2]))
        }
    }

    /** 统一入口：写入配置、同步所有颜色控件并通知绘制方 */
    private fun applyColor(color: Int, syncPicker: Boolean = true) {
        val opaque = 0xFF000000.toInt() or (color and 0x00FFFFFF)
        prefs.color = opaque
        syncColorInputs(opaque, syncPicker)
        applyChange()
        refreshColorSelection()
    }

    /** 把当前颜色回写到调色盘、预览与输入框（正在编辑的控件不打断） */
    private fun syncColorInputs(color: Int, syncPicker: Boolean = true) {
        if (syncPicker) colorPicker.setColor(color)
        (colorPreview.background as? GradientDrawable)?.setColor(color)
        if (!etHex.isFocused) {
            etHex.setText(String.format(Locale.US, "#%06X", color and 0x00FFFFFF))
        }
        if (!etR.isFocused) etR.setText(Color.red(color).toString())
        if (!etG.isFocused) etG.setText(Color.green(color).toString())
        if (!etB.isFocused) etB.setText(Color.blue(color).toString())
    }

    /** 解析 #RGB / #RRGGBB / #AARRGGBB，只保留 RGB 分量 */
    private fun parseHexColor(raw: String): Int? {
        val text = raw.trim().removePrefix("#")
        if (text.isEmpty()) return null
        val hex = if (text.length == 3) {
            text.map { "$it$it" }.joinToString("")
        } else {
            text
        }
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return 0xFF000000.toInt() or (value.toInt() and 0x00FFFFFF)
    }

    private fun refreshColorSelection() {
        val accent = 0xFF4F46E5.toInt()
        for (i in 0 until colorRow.childCount) {
            val wrapper = colorRow.getChildAt(i)
            val selected = wrapper.tag as? Int == prefs.color
            wrapper.background = if (selected) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(3), accent)
                }
            } else null
        }
    }

    private fun setupListeners() {
        swEnable.setOnCheckedChangeListener { _, checked ->
            if (checked && !isReady()) {
                swEnable.isChecked = false
                prefs.enabled = false
                requestRequiredPermission()
                return@setOnCheckedChangeListener
            }
            prefs.enabled = checked
            if (checked) OverlayService.start(this) else OverlayService.stop(this)
            updateSummary()
        }

        swBottom.setOnCheckedChangeListener { _, checked ->
            prefs.bottomEnabled = checked
            applyChange()
            updateSummary()
        }
        swTop.setOnCheckedChangeListener { _, checked ->
            prefs.topEnabled = checked
            applyChange()
            updateSummary()
        }
        swAppFilter.setOnCheckedChangeListener { _, checked ->
            prefs.appFilterEnabled = checked
            applyChange()
            updateSummary()
        }
        swBoot.setOnCheckedChangeListener { _, checked -> prefs.startOnBoot = checked }

        tvService.setOnClickListener {
            if (waitingPermission) requestRequiredPermission()
        }

        setupSeek(seekBottomHeight, tvBottomHeight, 0, OverlayPrefs.MAX_SIZE, "dp",
            { prefs.bottomHeight }, { prefs.bottomHeight = it })
        setupSeek(seekBottomOffset, tvBottomOffset, 0, OverlayPrefs.MAX_OFFSET, "dp",
            { prefs.bottomOffset }, { prefs.bottomOffset = it })
        setupSeek(seekTopHeight, tvTopHeight, 0, OverlayPrefs.MAX_SIZE, "dp",
            { prefs.topHeight }, { prefs.topHeight = it })
        setupSeek(seekTopOffset, tvTopOffset, 0, OverlayPrefs.MAX_OFFSET, "dp",
            { prefs.topOffset }, { prefs.topOffset = it })
        setupSeek(seekWidth, tvWidth, 20, 100, "%",
            { prefs.widthPercent }, { prefs.widthPercent = it })
        setupSeek(seekAlpha, tvAlpha, 10, 100, "%",
            { prefs.alphaPercent }, { prefs.alphaPercent = it })

        findViewById<View>(R.id.btn_perm_notify).setOnClickListener { askNotificationPermissionIfNeeded(true) }
        findViewById<View>(R.id.btn_perm_battery).setOnClickListener {
            if (isIgnoringBatteryOptimization()) {
                Toast.makeText(this, R.string.toast_already_granted, Toast.LENGTH_SHORT).show()
            } else {
                requestIgnoreBatteryOptimization()
                Toast.makeText(this, R.string.toast_battery_hint, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<View>(R.id.btn_acc_settings).setOnClickListener {
            MaskAccessibilityService.openSettings(this)
        }
        findViewById<View>(R.id.btn_pick_apps).setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<View>(R.id.btn_adjust).setOnClickListener { openAdjustPanel() }
        findViewById<View>(R.id.btn_stop).setOnClickListener {
            prefs.enabled = false
            swEnable.isChecked = false
            OverlayService.stop(this)
            updateSummary()
        }
    }

    private fun setupSeek(
        seek: SeekBar,
        label: TextView,
        min: Int,
        max: Int,
        unit: String,
        getter: () -> Int,
        setter: (Int) -> Unit
    ) {
        seek.max = max - min
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + min
                label.text = "$value$unit"
                if (fromUser) {
                    setter(value)
                    applyChange()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    private fun refreshControls() {
        swEnable.setOnCheckedChangeListener(null)
        swBottom.setOnCheckedChangeListener(null)
        swTop.setOnCheckedChangeListener(null)
        swAppFilter.setOnCheckedChangeListener(null)
        swBoot.setOnCheckedChangeListener(null)

        swEnable.isChecked = prefs.enabled
        swBottom.isChecked = prefs.bottomEnabled
        swTop.isChecked = prefs.topEnabled
        swAppFilter.isChecked = prefs.appFilterEnabled
        swBoot.isChecked = prefs.startOnBoot

        seekBottomHeight.progress = prefs.bottomHeight
        seekBottomOffset.progress = prefs.bottomOffset
        seekTopHeight.progress = prefs.topHeight
        seekTopOffset.progress = prefs.topOffset
        seekWidth.progress = prefs.widthPercent - 20
        seekAlpha.progress = prefs.alphaPercent - 10

        tvBottomHeight.text = "${prefs.bottomHeight}dp"
        tvBottomOffset.text = "${prefs.bottomOffset}dp"
        tvTopHeight.text = "${prefs.topHeight}dp"
        tvTopOffset.text = "${prefs.topOffset}dp"
        tvWidth.text = "${prefs.widthPercent}%"
        tvAlpha.text = "${prefs.alphaPercent}%"

        refreshColorSelection()
        syncColorInputs(prefs.color)
        setupListeners()
    }

    // -------------------------------------------------------------------- 权限

    private fun refreshPermissionState() {
        val notifyOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        tvNotifyPerm.text = getString(if (notifyOk) R.string.perm_granted else R.string.perm_missing)
        tvBatteryPerm.text = getString(
            if (isIgnoringBatteryOptimization()) R.string.perm_granted else R.string.perm_missing
        )
        tvAccState.text = getString(
            if (MaskAccessibilityService.isEnabled(this)) R.string.perm_granted
            else R.string.acc_need_enable
        )
    }

    /** 无障碍服务是否已在系统设置中开启 */
    private fun isReady(): Boolean = MaskAccessibilityService.isEnabled(this)

    /**
     * 状态自愈：让"开关显示的值"与"服务真实状态"保持一致。
     *
     * 进程被杀 / 被强制停止时没有回调，持久化的 enabled 会停留在 true，
     * 下次打开界面就会显示"已开启"但遮罩并不生效。
     * 这里在每次进入界面时校正一次：
     * - 权限还在但服务没跑：真正把服务拉起来；
     * - 权限已被系统关闭：保留开启意图并提示重新授权。
     */
    private fun reconcileServiceState() {
        if (!prefs.enabled) {
            waitingPermission = false
            return
        }
        if (!isReady()) {
            // 权限被系统关闭：保留用户的开启意图，只提示重新授权，
            // 重新授权后无障碍服务会自动绑定并恢复绘制
            if (!waitingPermission) {
                Toast.makeText(this, R.string.toast_need_reauth, Toast.LENGTH_LONG).show()
            }
            waitingPermission = true
            updateServiceStatus()
            return
        }
        waitingPermission = false
        if (!OverlayService.isRunning()) {
            lastAutoStartAt = System.currentTimeMillis()
            OverlayService.start(this)
            // 无障碍服务可能刚被系统重新绑定，稍后再刷一次确保遮罩出现
            handler.postDelayed({
                MaskAccessibilityService.refresh()
                updateServiceStatus()
            }, 600)
            Toast.makeText(this, R.string.toast_service_restored, Toast.LENGTH_SHORT).show()
        }
    }

    /** 引导用户开启无障碍服务（本应用唯一需要的敏感权限） */
    private fun requestRequiredPermission() {
        Toast.makeText(this, R.string.acc_need_enable, Toast.LENGTH_LONG).show()
        MaskAccessibilityService.openSettings(this)
    }

    private fun askNotificationPermissionIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            if (force) Toast.makeText(this, R.string.toast_already_granted, Toast.LENGTH_SHORT).show()
            return
        }
        notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // ------------------------------------------------------------------ 其他

    private fun openAdjustPanel() {
        if (!isReady()) {
            requestRequiredPermission()
            return
        }
        if (!prefs.enabled || !OverlayService.isRunning()) {
            prefs.enabled = true
            swEnable.isChecked = true
            OverlayService.start(this)
        }
        handler.postDelayed({ OverlayService.edit(this) }, 400)
        Toast.makeText(this, R.string.toast_adjust_hint, Toast.LENGTH_LONG).show()
    }

    private fun toggleEnabled() {
        // 以服务真实状态为准，避免"记录为开启但实际已停"时点一下反而变成关闭
        val actuallyOn = prefs.enabled && OverlayService.isRunning()
        if (!actuallyOn && !isReady()) {
            requestRequiredPermission()
            return
        }
        prefs.enabled = !actuallyOn
        if (prefs.enabled) OverlayService.start(this) else OverlayService.stop(this)
    }

    private fun refreshSelectedApps() {
        val count = prefs.selectedApps.size
        tvSelectedApps.text = if (count == 0) {
            getString(R.string.selected_apps_empty)
        } else {
            getString(R.string.selected_apps_count, count)
        }
    }

    private fun updateServiceStatus() {
        val last = prefs.heartbeatAt
        val ago = if (last == 0L) -1 else ((System.currentTimeMillis() - last) / 1000).toInt()
        tvService.text = when {
            !prefs.enabled -> getString(R.string.summary_off)
            waitingPermission -> getString(R.string.service_wait_permission)
            OverlayService.isRunning() -> getString(R.string.service_running, ago.coerceAtLeast(0))
            ago < 0 -> getString(R.string.service_never)
            else -> getString(R.string.service_dead, ago)
        }
    }

    private fun updateSummary() {
        val parts = mutableListOf<String>()
        if (prefs.bottomEnabled) parts += getString(R.string.summary_bottom, prefs.bottomHeight)
        if (prefs.topEnabled) parts += getString(R.string.summary_top, prefs.topHeight)
        tvSummary.text = if (!prefs.enabled || parts.isEmpty()) {
            getString(R.string.summary_off)
        } else {
            getString(R.string.summary_on, parts.joinToString("，"))
        }
    }

    /** 配置变化后通知绘制方刷新 */
    private fun applyChange() {
        if (prefs.enabled) OverlayService.update(this)
        MaskAccessibilityService.refresh()
        updateSummary()
    }

    companion object {
        const val EXTRA_TOGGLE = "extra_toggle"
        private const val START_COOLDOWN_MS = 3000L
    }
}
