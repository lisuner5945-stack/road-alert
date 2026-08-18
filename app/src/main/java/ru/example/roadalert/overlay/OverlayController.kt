package ru.example.roadalert.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import ru.example.roadalert.domain.model.ActiveAlert
import ru.example.roadalert.util.AppLog
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Компактная карточка поверх навигатора (ТЗ §23).
 *
 * Показывается только при активной поездке и только когда есть релевантное
 * предупреждение. Рекламы здесь нет и быть не может; под системный UI
 * карточка не маскируется.
 */
class OverlayController(
    private val context: Context,
    private val onPositionChanged: (x: Int, y: Int) -> Unit,
    private val onCloseRequested: () -> Unit,
) {

    private val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)

    private var rootView: View? = null
    private var typeView: TextView? = null
    private var distanceView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val canDrawOverlays: Boolean get() = Settings.canDrawOverlays(context)

    fun show(alert: ActiveAlert, positionX: Int, positionY: Int) {
        if (!canDrawOverlays) {
            AppLog.event("OVERLAY_NO_PERMISSION")
            return
        }
        if (rootView == null) attach(positionX, positionY)
        update(alert)
    }

    fun update(alert: ActiveAlert) {
        val limit = alert.camera.speedLimitKmh
        typeView?.text = if (limit != null) "📷  $limit" else "📷"
        distanceView?.text = formatDistance(alert.distanceMeters)
    }

    fun hide() {
        val view = rootView ?: return
        runCatching { windowManager?.removeView(view) }
        rootView = null
        typeView = null
        distanceView = null
        layoutParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(positionX: Int, positionY: Int) {
        val manager = windowManager ?: return
        val density = context.resources.displayMetrics.density

        fun dp(value: Int) = (value * density).roundToInt()

        val type = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val distance = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val close = TextView(context).apply {
            text = "✕"
            setTextColor("#B0BEC5".toColorInt())
            textSize = 13f
            gravity = Gravity.END
            setOnClickListener {
                AppLog.event("OVERLAY_CLOSED_BY_USER")
                onCloseRequested()
                hide()
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor("#E6101418".toColorInt())
                setStroke(dp(1), "#4D90A4AE".toColorInt())
            }
            addView(close)
            addView(type)
            addView(distance)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = positionX
            y = positionY
        }

        container.setOnTouchListener(DragListener(params, manager, container))

        runCatching { manager.addView(container, params) }
            .onFailure {
                AppLog.event("OVERLAY_ADD_FAILED", "reason" to it.message)
                return
            }

        rootView = container
        typeView = type
        distanceView = distance
        layoutParams = params
        AppLog.event("OVERLAY_SHOWN")
    }

    /** Перетаскивание карточки; итоговая позиция сохраняется в настройках. */
    private inner class DragListener(
        private val params: WindowManager.LayoutParams,
        private val manager: WindowManager,
        private val view: View,
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) dragging = true
                    if (dragging) {
                        params.x = initialX + dx.roundToInt()
                        params.y = initialY + dy.roundToInt()
                        runCatching { manager.updateViewLayout(view, params) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) onPositionChanged(params.x, params.y) else v?.performClick()
                }
            }
            return dragging
        }
    }

    private companion object {

        const val TOUCH_SLOP = 8f

        fun formatDistance(meters: Double): String = if (meters >= 1000) {
            String.format(java.util.Locale.getDefault(), "%.1f км", meters / 1000.0)
        } else {
            "${(meters / 10).toInt() * 10} м"
        }
    }
}
