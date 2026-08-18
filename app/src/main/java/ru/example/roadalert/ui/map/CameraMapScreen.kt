package ru.example.roadalert.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.example.roadalert.BuildConfig
import ru.example.roadalert.detection.BoundingBox
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.ui.components.SectionCard
import ru.example.roadalert.ui.components.SpeedLimitSign
import ru.example.roadalert.util.AppLog
import java.io.File

/**
 * Карта камер.
 *
 * Подложка — OpenStreetMap через osmdroid: без Google Play Services, без ключей
 * API и без единого рубля за обслуживание (ТЗ §12 и бюджет 0 ₽). Данные о
 * камерах те же самые, что использует детектор во время поездки, поэтому карта
 * показывает ровно то, о чём приложение будет предупреждать.
 */
@Composable
fun CameraMapScreen(
    camerasProvider: (BoundingBox) -> List<CameraPoint>,
    databaseReady: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var visibleCount by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableDoubleStateOf(DEFAULT_ZOOM) }
    var selected by remember { mutableStateOf<CameraPoint?>(null) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }

    val mapView = remember {
        configureOsmdroid(context)
        createMapView(context)
    }

    val myLocation = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            isDrawAccuracyEnabled = true
        }
    }

    val density = LocalDensity.current.density
    val cameraOverlay = remember {
        CameraMapOverlay(
            density = density,
            provider = camerasProvider,
            onTap = { selected = it },
            onVisibleChanged = { count, currentZoom ->
                visibleCount = count
                zoom = currentZoom
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) mapView.followMyLocation(myLocation)
    }

    // База могла обновиться, пока карта была закрыта.
    DisposableEffect(databaseReady) {
        cameraOverlay.invalidateCameras()
        onDispose { }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        mapView.overlays.add(myLocation)
        mapView.overlays.add(cameraOverlay)
        mapView.restoreLastPosition(context)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (hasLocationPermission) myLocation.enableMyLocation()
        AppLog.event("MAP_OPENED")

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.saveLastPosition(context)
            myLocation.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(onClick = onBack) { Text("Назад") }
                    Column(Modifier.weight(1f)) {
                        Text("Карта камер", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (databaseReady) "В кадре: $visibleCount" else "База камер не загружена",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            MapDisplay.hintFor(zoom, visibleCount)?.let { hint ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                ) {
                    Text(
                        hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapRoundButton("+") { mapView.controller.zoomIn() }
            MapRoundButton("−") { mapView.controller.zoomOut() }
            MapRoundButton("◎") {
                if (hasLocationPermission) {
                    mapView.followMyLocation(myLocation)
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected?.let { camera ->
                CameraDetailsCard(camera = camera, onClose = {
                    selected = null
                    cameraOverlay.selected = null
                })
            }
            Text(
                "Карта: © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF37474F),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MapRoundButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CameraDetailsCard(camera: CameraPoint, onClose: () -> Unit) {
    SectionCard(title = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            camera.speedLimitKmh?.let { SpeedLimitSign(limitKmh = it, diameter = 64) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    MapDisplay.typeLabel(camera.type),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    MapDisplay.speedLabel(camera),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    MapDisplay.directionLabel(camera.directionDegrees),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    formatCoordinates(camera.latitude, camera.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
}

private fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)

private const val DEFAULT_ZOOM = 14.0
private const val MAP_PREFS = "road_alert_map"
private const val KEY_LAT = "last_lat"
private const val KEY_LON = "last_lon"
private const val KEY_ZOOM = "last_zoom"

/** Москва: нейтральная точка старта, если ни позиции, ни разрешения ещё нет. */
private const val FALLBACK_LAT = 55.7558
private const val FALLBACK_LON = 37.6173

/**
 * Готовит osmdroid к работе: свой User-Agent (этого требуют правила серверов
 * OSM) и кэш плиток внутри приложения, чтобы не просить разрешение на storage.
 */
private fun configureOsmdroid(context: Context) {
    val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    Configuration.getInstance().apply {
        load(context, prefs)
        userAgentValue = BuildConfig.APPLICATION_ID
        osmdroidBasePath = File(context.cacheDir, "osmdroid").also { it.mkdirs() }
        osmdroidTileCache = File(osmdroidBasePath, "tiles").also { it.mkdirs() }
        // Кэш ограничен: карта — вспомогательная функция, а не файловая свалка.
        tileFileSystemCacheMaxBytes = 60L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
    }
}

private fun createMapView(context: Context): MapView = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    setUseDataConnection(true)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    minZoomLevel = 4.0
    maxZoomLevel = 19.0
    isTilesScaledToDpi = true
}

private fun MapView.restoreLastPosition(context: Context) {
    val prefs = context.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE)
    val savedLat = prefs.getFloat(KEY_LAT, Float.NaN).toDouble()
    val savedLon = prefs.getFloat(KEY_LON, Float.NaN).toDouble()
    val savedZoom = prefs.getFloat(KEY_ZOOM, DEFAULT_ZOOM.toFloat()).toDouble()

    val start = when {
        !savedLat.isNaN() && !savedLon.isNaN() -> GeoPoint(savedLat, savedLon)
        else -> context.lastKnownPoint() ?: GeoPoint(FALLBACK_LAT, FALLBACK_LON)
    }
    controller.setZoom(savedZoom)
    controller.setCenter(start)
}

private fun MapView.saveLastPosition(context: Context) {
    val center = mapCenter
    context.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).edit {
        putFloat(KEY_LAT, center.latitude.toFloat())
        putFloat(KEY_LON, center.longitude.toFloat())
        putFloat(KEY_ZOOM, zoomLevelDouble.toFloat())
    }
}

private fun MapView.followMyLocation(overlay: MyLocationNewOverlay) {
    overlay.enableMyLocation()
    overlay.enableFollowLocation()
    overlay.myLocation?.let { controller.animateTo(it) }
    if (zoomLevelDouble < MapDisplay.MIN_ZOOM_SIGNS) controller.setZoom(DEFAULT_ZOOM)
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Последняя известная позиция — только чтобы открыть карту не посреди океана. */
@SuppressLint("MissingPermission")
private fun Context.lastKnownPoint(): GeoPoint? {
    if (!hasLocationPermission()) return null
    val manager = ContextCompat.getSystemService(this, LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    val location = providers.asSequence()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
    return location?.let { GeoPoint(it.latitude, it.longitude) }
}
