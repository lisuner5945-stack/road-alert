package ru.example.roadalert.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import ru.example.roadalert.util.AppLog

/**
 * Небольшой баннер на экранах вне поездки (ТЗ §25).
 *
 * Если баннер не загрузился — место просто схлопывается: реклама никогда
 * не блокирует основную функцию приложения.
 */
@Composable
fun HomeBanner(modifier: Modifier = Modifier) {
    if (!AdUnits.enabled) return

    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val widthDp = with(density) { containerWidth.toDp().value.toInt() }.coerceAtLeast(MIN_BANNER_WIDTH_DP)
    var failed by remember { mutableStateOf(false) }
    if (failed) return

    val holder = remember { mutableStateOf<BannerAdView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { holder.value?.destroy() }
            holder.value = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            val container = FrameLayout(context)
            val banner = BannerAdView(context).apply {
                setAdSize(BannerAdSize.sticky(context, widthDp))
                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() = AppLog.event("AD_BANNER_LOADED")

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        AppLog.event("AD_LOAD_FAILED", "code" to error.code)
                        failed = true
                    }

                    override fun onAdClicked() = Unit

                    override fun onImpression(impressionData: ImpressionData?) = Unit
                })
            }
            holder.value = banner
            container.addView(banner)
            runCatching { banner.loadAd(AdRequest.Builder(AdUnits.banner).build()) }
                .onFailure {
                    AppLog.event("AD_BANNER_EXCEPTION", "reason" to it.message)
                    failed = true
                }
            container
        },
    )
}

/** Ниже этой ширины Yandex SDK не отдаёт баннер. */
private const val MIN_BANNER_WIDTH_DP = 320
