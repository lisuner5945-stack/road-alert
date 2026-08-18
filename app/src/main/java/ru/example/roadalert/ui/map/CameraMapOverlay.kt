package ru.example.roadalert.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import ru.example.roadalert.detection.BoundingBox
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Рисует камеры поверх карты.
 *
 * Отдельные Marker'ы osmdroid не используются сознательно: камер в базе больше
 * двенадцати тысяч, и создавать под каждую View-подобный объект — верный способ
 * уронить частоту кадров. Здесь всё рисуется на канве за один проход, а список
 * видимых камер берётся из R-дерева, которое и так живёт в памяти.
 */
class CameraMapOverlay(
    private val density: Float,
    private val provider: (BoundingBox) -> List<CameraPoint>,
    private val onTap: (CameraPoint?) -> Unit,
    private val onVisibleChanged: (count: Int, zoom: Double) -> Unit,
) : Overlay() {

    private val signRadius = 15f * density
    private val ringWidth = 4f * density
    private val dotRadius = 3.5f * density
    private val arrowLength = 9f * density
    private val tapRadius = 26f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(90, 0, 0, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.rgb(0x1E, 0x88, 0xE5)
    }

    private val reusablePoint = Point()

    private var cachedBox: BoundingBox? = null
    private var cachedCameras: List<CameraPoint> = emptyList()

    private var lastReportedCount = -1
    private var lastReportedZoom = Double.NaN

    private var attachedMap: MapView? = null

    /** Камера, по которой пользователь тапнул: подсвечивается кольцом. */
    var selected: CameraPoint? = null
        set(value) {
            field = value
            attachedMap?.postInvalidate()
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        attachedMap = mapView

        val visible = mapView.projection.boundingBox
        val box = BoundingBox(
            minLatitude = visible.latSouth,
            minLongitude = visible.lonWest,
            maxLatitude = visible.latNorth,
            maxLongitude = visible.lonEast,
        )
        val zoom = mapView.zoomLevelDouble
        val cameras = camerasFor(box)

        if (cameras.size != lastReportedCount || zoom != lastReportedZoom) {
            lastReportedCount = cameras.size
            lastReportedZoom = zoom
            onVisibleChanged(cameras.size, zoom)
        }

        when (MapDisplay.modeFor(zoom, cameras.size)) {
            MapDisplayMode.HIDDEN -> return
            MapDisplayMode.DOTS -> drawDots(canvas, mapView, cameras)
            MapDisplayMode.SIGNS -> drawSigns(canvas, mapView, cameras)
        }
    }

    private fun camerasFor(box: BoundingBox): List<CameraPoint> {
        val cached = cachedBox
        if (cached != null && cached == box) return cachedCameras
        val found = provider(box)
        cachedBox = box
        cachedCameras = found
        return found
    }

    private fun drawDots(canvas: Canvas, mapView: MapView, cameras: List<CameraPoint>) {
        val projection = mapView.projection
        cameras.asSequence().take(MapDisplay.MAX_DOTS).forEach { camera ->
            projection.toPixels(GeoPoint(camera.latitude, camera.longitude), reusablePoint)
            val x = reusablePoint.x.toFloat()
            val y = reusablePoint.y.toFloat()
            if (!isOnScreen(canvas, x, y, dotRadius)) return@forEach
            fillPaint.color = Color.WHITE
            canvas.drawCircle(x, y, dotRadius + density, fillPaint)
            fillPaint.color = ringColor(camera.type)
            canvas.drawCircle(x, y, dotRadius, fillPaint)
        }
        fillPaint.color = Color.WHITE
    }

    private fun drawSigns(canvas: Canvas, mapView: MapView, cameras: List<CameraPoint>) {
        val projection = mapView.projection
        val selectedId = selected?.id
        cameras.forEach { camera ->
            projection.toPixels(GeoPoint(camera.latitude, camera.longitude), reusablePoint)
            val x = reusablePoint.x.toFloat()
            val y = reusablePoint.y.toFloat()
            if (!isOnScreen(canvas, x, y, signRadius + arrowLength)) return@forEach

            camera.directionDegrees?.let { drawDirectionArrow(canvas, x, y, it, camera.type) }

            fillPaint.color = Color.WHITE
            canvas.drawCircle(x, y, signRadius, fillPaint)
            ringPaint.color = ringColor(camera.type)
            canvas.drawCircle(x, y, signRadius - ringWidth / 2f, ringPaint)
            canvas.drawCircle(x, y, signRadius, outlinePaint)

            val label = MapDisplay.signText(camera)
            if (label != null) {
                textPaint.textSize = if (label.length >= 3) signRadius * 0.9f else signRadius * 1.15f
                val baseline = y - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, x, baseline, textPaint)
            } else {
                fillPaint.color = ringColor(camera.type)
                canvas.drawCircle(x, y, signRadius * 0.32f, fillPaint)
                fillPaint.color = Color.WHITE
            }

            if (camera.id == selectedId) {
                canvas.drawCircle(x, y, signRadius + 5f * density, selectionPaint)
            }
        }
    }

    private fun drawDirectionArrow(
        canvas: Canvas,
        x: Float,
        y: Float,
        directionDegrees: Double,
        type: CameraType,
    ) {
        // Азимут: 0 — север, дальше по часовой стрелке. На экране север смотрит вверх.
        val radians = Math.toRadians(directionDegrees)
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val tipX = x + dx * (signRadius + arrowLength)
        val tipY = y + dy * (signRadius + arrowLength)
        val baseX = x + dx * (signRadius + density)
        val baseY = y + dy * (signRadius + density)
        val halfWidth = 4.5f * density

        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(baseX - dy * halfWidth, baseY + dx * halfWidth)
            lineTo(baseX + dy * halfWidth, baseY - dx * halfWidth)
            close()
        }
        arrowPaint.color = ringColor(type)
        canvas.drawPath(path, arrowPaint)
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        var best: CameraPoint? = null
        var bestDistance = tapRadius * tapRadius

        cachedCameras.forEach { camera ->
            projection.toPixels(GeoPoint(camera.latitude, camera.longitude), reusablePoint)
            val dx = reusablePoint.x - event.x
            val dy = reusablePoint.y - event.y
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = camera
            }
        }

        val tapped = best
        selected = tapped
        onTap(tapped)
        return tapped != null
    }

    /** Сбрасывает кэш: базу перезагрузили, старый список больше не годится. */
    fun invalidateCameras() {
        cachedBox = null
        cachedCameras = emptyList()
        attachedMap?.postInvalidate()
    }

    private fun isOnScreen(canvas: Canvas, x: Float, y: Float, margin: Float): Boolean =
        x >= -margin && y >= -margin && x <= canvas.width + margin && y <= canvas.height + margin

    private fun ringColor(type: CameraType): Int = when (type) {
        CameraType.RED_LIGHT -> Color.rgb(0xF5, 0x7C, 0x00)
        CameraType.SPEED_AND_RED_LIGHT -> Color.rgb(0xC6, 0x28, 0x28)
        CameraType.AVERAGE_SPEED_START, CameraType.AVERAGE_SPEED_END -> Color.rgb(0x6A, 0x1B, 0x9A)
        CameraType.LANE_CONTROL -> Color.rgb(0x00, 0x69, 0x5C)
        else -> Color.rgb(0xD3, 0x2F, 0x2F)
    }
}
