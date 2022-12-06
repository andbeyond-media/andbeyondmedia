package com.rtb.andbeyondmedia.banners

import android.content.Context
import android.os.CountDownTimer
import com.google.android.gms.ads.AdSize
import com.rtb.andbeyondmedia.common.AdRequest
import java.util.*
import kotlin.math.ceil


internal class BannerManager(private val context: Context, private val managerListener: AdManagerListener) {
    private var config: Config? = null
    private var currentConfig: Config? = null
    private lateinit var adSizesToBeTaken: ArrayList<AdSize>
    private var activeTimeCounter: CountDownTimer? = null
    private var passiveTimeCounter: CountDownTimer? = null
    private var refreshCount = 0
    private var pubAdUnitName: String = ""
    private var isVisible = false
    private var isVisibleFor = 0
    private var wasPublisherLoad = true


    internal fun saveVisibility(visible: Boolean) {
        if (visible == isVisible) return
        isVisible = visible
    }

    internal fun fetchConfig(pubAdUnit: String, adSize: ArrayList<AdSize>, adType: String, load: (Config?) -> Unit) {
        this.adSizesToBeTaken = adSize
        if (pubAdUnit.contains('/')) {
            pubAdUnitName = pubAdUnit.substring(pubAdUnit.indexOf('/') + 1, pubAdUnit.length)
        }
        getMaximumAdSize()?.let { size ->
            context.getBannerConfig(size.width, adType) {
                it?.let {
                    load(it)
                    setConfig(it)
                } ?: load(null)
            }
        } ?: load(null)
    }

    private fun setConfig(config: Config) {
        this.config = config
        this.currentConfig = config.copy().apply {
            lastRefreshAt = Date().time
        }
        if (config.followSize == 1 && config.toSizes().isNotEmpty()) {
            adSizesToBeTaken = getSize(config.toSizes())
        }
    }

    internal fun refresh(active: Int = 1, forced: Boolean = false) {
        val currentTimeStamp = Date().time
        val differenceOfLastRefresh = ceil((currentTimeStamp - (currentConfig?.lastRefreshAt ?: 0)).toDouble() / 1000.00).toInt()
        fun refreshAd() {
            currentConfig?.lastRefreshAt = currentTimeStamp
            managerListener.attachAdView(getAdUnit(forced), adSizesToBeTaken)
            loadAd(active)
        }
        if ((forced || isVisible || (differenceOfLastRefresh >= ((if (active == 1) currentConfig?.activeRefreshInterval else currentConfig?.passiveRefreshInterval) ?: 0) * (currentConfig?.factor ?: 0)))
                && differenceOfLastRefresh >= (currentConfig?.difference ?: 0) && (isVisibleFor >= ((if (wasPublisherLoad) currentConfig?.minView else currentConfig?.minViewRtb) ?: 0))
        ) {
            refreshAd()
        } else {
            startRefreshing()
        }
    }

    internal fun startRefreshing(resetVisibleTime: Boolean = false, isPublisherLoad: Boolean = false) {
        if (resetVisibleTime) {
            isVisibleFor = 0
        }
        this.wasPublisherLoad = isPublisherLoad
        currentConfig?.let {
            startPassiveCounter((it.passiveRefreshInterval ?: 0.0).toLong())
            startActiveCounter((it.activeRefreshInterval ?: 0).toLong())
        }
    }

    internal fun getSize(adSizes: String): ArrayList<AdSize> {
        fun getAdSizeObj(adSize: String) = when (adSize) {
            "BANNER" -> AdSize.BANNER
            "LARGE_BANNER" -> AdSize.LARGE_BANNER
            "MEDIUM_RECTANGLE" -> AdSize.MEDIUM_RECTANGLE
            "FULL_BANNER" -> AdSize.FULL_BANNER
            "LEADERBOARD" -> AdSize.LEADERBOARD
            else -> {
                val w = adSize.replace(" ", "").substring(0, adSize.indexOf("x")).toIntOrNull() ?: 0
                val h = adSize.replace(" ", "").substring(adSize.indexOf("x") + 1, adSize.length).toIntOrNull() ?: 0
                AdSize(w, h)
            }
        }

        return ArrayList<AdSize>().apply {
            for (adSize in adSizes.replace(" ", "").split(",")) {
                add(getAdSizeObj(adSize))
            }
        }
    }

    private fun loadAd(active: Int) {
        val adRequest = AdRequest().Builder().apply {
            addCustomTargeting("adunit", pubAdUnitName)
            addCustomTargeting("active", active.toString())
            addCustomTargeting("refresh", refreshCount.toString())
        }.build()
        refreshCount++
        managerListener.loadAd(adRequest)
    }

    internal fun adPaused() {
        activeTimeCounter?.cancel()
    }

    internal fun adResumed() {
        currentConfig?.let { startActiveCounter((it.activeRefreshInterval ?: 0).toLong()) }
    }

    internal fun adDestroyed() {
        activeTimeCounter?.cancel()
        passiveTimeCounter?.cancel()
    }

    private fun startActiveCounter(seconds: Long) {
        activeTimeCounter?.cancel()
        activeTimeCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                currentConfig?.activeRefreshInterval = (currentConfig?.activeRefreshInterval ?: 0) - 1
            }

            override fun onFinish() {
                currentConfig?.activeRefreshInterval = config?.activeRefreshInterval
                refresh(1)
            }
        }
        activeTimeCounter?.start()
    }

    private fun startPassiveCounter(seconds: Long) {
        passiveTimeCounter?.cancel()
        passiveTimeCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                isVisibleFor++
                currentConfig?.passiveRefreshInterval = (currentConfig?.passiveRefreshInterval ?: 0) - 1
            }

            override fun onFinish() {
                currentConfig?.passiveRefreshInterval = config?.passiveRefreshInterval
                refresh(0)
            }
        }
        passiveTimeCounter?.start()
    }

    private fun getAdUnit(forced: Boolean): String {
        val networkName = if (currentConfig?.networkCode.isNullOrEmpty()) currentConfig?.networkId else String.format("%s,%s", currentConfig?.networkId, currentConfig?.networkCode)
        val adUnit = String.format("/%s/%s-%s-1", networkName, currentConfig?.affiliatedId.toString(), currentConfig?.type ?: "", if (forced) currentConfig?.hijack?.number ?: 0 else currentConfig?.position ?: 0)
        return adUnit
    }

    private fun getMaximumAdSize(): AdSize? {
        return if (!this::adSizesToBeTaken.isInitialized || adSizesToBeTaken.isEmpty()) {
            null
        } else {
            var maxAdSize = adSizesToBeTaken[0]
            var maxWidth = 0
            for (adSize in adSizesToBeTaken) {
                if (maxWidth < adSize.width) {
                    maxWidth = adSize.width
                    maxAdSize = adSize
                }
            }
            maxAdSize
        }
    }
}

object AdTypes {
    const val BANNER = "BANNER"
    const val ADAPTIVE = "ADAPTIVE"
    const val INLINE = "INLINE"
    const val STICKY = "STICKY"

    /* const val INREAD = "INREAD"
     const val INTER = "INTER"
     const val REWARD = "REWARD"
     const val NATIVE = "NATIVE"*/
    const val OTHER = "OTHER"
}