package com.admask.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 轻量 HSV 调色盘：左侧饱和度 / 明度面板 + 右侧色相条。
 *
 * 纯 Canvas 实现，不引入任何三方依赖；拖动时不会与外层 ScrollView 抢事件。
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 颜色变化回调，参数为不透明的 0xFFRRGGBB */
    var onColorChanged: ((Int) -> Unit)? = null

    private val hsv = FloatArray(3)

    private val panelRect = RectF()
    private val hueRect = RectF()

    private var panelBitmap: Bitmap? = null
    private var hueBitmap: Bitmap? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val indicatorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val indicatorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x99000000.toInt()
    }

    private val barWidth = (BAR_WIDTH_DP * resources.displayMetrics.density).roundToInt()
    private val gap = (GAP_DP * resources.displayMetrics.density).roundToInt()

    init {
        setColor(Color.BLACK)
    }

    /** 当前选中的颜色（始终不透明） */
    fun getColor(): Int = Color.HSVToColor(hsv)

    /** 设置当前颜色，仅取 RGB 分量 */
    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        rebuildPanel()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (w - paddingRight).toFloat()
        val bottom = (h - paddingBottom).toFloat()

        panelRect.set(left, top, (right - barWidth - gap).coerceAtLeast(left + 1), bottom)
        hueRect.set((right - barWidth).coerceAtLeast(left + 1), top, right, bottom)

        val pw = panelRect.width().roundToInt().coerceAtLeast(1)
        val ph = panelRect.height().roundToInt().coerceAtLeast(1)
        val hw = hueRect.width().roundToInt().coerceAtLeast(1)
        val hh = hueRect.height().roundToInt().coerceAtLeast(1)

        panelBitmap?.recycle()
        hueBitmap?.recycle()
        panelBitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        hueBitmap = makeHueBitmap(hw, hh)
        rebuildPanel()
    }

    /** 色相条只在尺寸变化时生成一次 */
    private fun makeHueBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
                    0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(),
                    0xFFFF0000.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    /** 色相变化时重绘饱和度 / 明度面板 */
    private fun rebuildPanel() {
        val bmp = panelBitmap ?: return
        val canvas = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()

        canvas.drawColor(Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w, 0f, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, white)

        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, black)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        panelBitmap?.let { canvas.drawBitmap(it, panelRect.left, panelRect.top, bitmapPaint) }
        hueBitmap?.let { canvas.drawBitmap(it, hueRect.left, hueRect.top, bitmapPaint) }

        // 面板指示器
        val px = panelRect.left + hsv[1] * panelRect.width()
        val py = panelRect.top + (1f - hsv[2]) * panelRect.height()
        val r = 8f * resources.displayMetrics.density
        canvas.drawCircle(px, py, r, indicatorShadowPaint)
        canvas.drawCircle(px, py, r - 2f, indicatorFillPaint)
        canvas.drawCircle(px, py, r, indicatorPaint)

        // 色相条指示器
        val hy = hueRect.top + (hsv[0] / 360f) * hueRect.height()
        val hx = hueRect.centerX()
        canvas.drawLine(hueRect.left, hy, hueRect.right, hy, indicatorShadowPaint)
        canvas.drawLine(hueRect.left, hy, hueRect.right, hy, indicatorPaint)
        canvas.drawCircle(hx, hy, r - 2f, indicatorFillPaint)
        canvas.drawCircle(hx, hy, r - 2f, indicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x, event.y, true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(x: Float, y: Float, notify: Boolean) {
        when {
            x <= panelRect.right -> {
                hsv[1] = ((x - panelRect.left) / panelRect.width()).coerceIn(0f, 1f)
                hsv[2] = (1f - (y - panelRect.top) / panelRect.height()).coerceIn(0f, 1f)
            }

            x >= hueRect.left -> {
                hsv[0] = (((y - hueRect.top) / hueRect.height()).coerceIn(0f, 1f)) * 360f
                rebuildPanel()
            }

            else -> return
        }
        invalidate()
        if (notify) onColorChanged?.invoke(getColor())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        panelBitmap?.recycle()
        hueBitmap?.recycle()
        panelBitmap = null
        hueBitmap = null
    }

    companion object {
        private const val BAR_WIDTH_DP = 26
        private const val GAP_DP = 10
    }
}
