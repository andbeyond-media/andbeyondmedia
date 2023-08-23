package com.rtb.andbeyondmedia.intersitial

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.appharbr.sdk.engine.AdBlockReason
import com.appharbr.sdk.engine.AdSdk
import com.appharbr.sdk.engine.AppHarbr
import com.appharbr.sdk.engine.adformat.AdFormat
import com.appharbr.sdk.engine.listeners.AHIncident
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.gson.Gson
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.ConfigSetWorker
import com.rtb.andbeyondmedia.sdk.Logger
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log
import org.prebid.mobile.InterstitialAdUnit
import org.prebid.mobile.Signals
import org.prebid.mobile.VideoParameters
import org.prebid.mobile.api.data.AdUnitFormat
import java.util.EnumSet

internal class InterstitialAdManager(private val context: Activity, private val adUnit: String) {

    private var sdkConfig: SDKConfig? = null
    private var interstitialConfig: InterstitialConfig = InterstitialConfig()
    private var shouldBeActive: Boolean = false
    private val storeService = AndBeyondMedia.getStoreService(context)
    private var firstLook: Boolean = true
    private var overridingUnit: String? = null
    private var otherUnit = false

    init {
        sdkConfig = storeService.config
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
    }

    fun load(adRequest: AdRequest, callBack: (interstitialAd: AdManagerInterstitialAd?) -> Unit) {
        var adManagerAdRequest = adRequest.getAdRequest()
        if (adManagerAdRequest == null) {
            callBack(null)
            return
        }
        shouldSetConfig {
            if (it) {
                setConfig()
                if (interstitialConfig.isNewUnit && interstitialConfig.newUnit?.status == 1) {
                    createRequest().getAdRequest()?.let { request ->
                        adManagerAdRequest = request
                        loadAd(getAdUnitName(false, hijacked = false, newUnit = true), request, callBack)
                    }
                } else if (checkHijack(interstitialConfig.hijack)) {
                    createRequest(hijacked = true).getAdRequest()?.let { request ->
                        adManagerAdRequest = request
                        loadAd(getAdUnitName(false, hijacked = true, newUnit = false), request, callBack)
                    }
                } else {
                    loadAd(adUnit, adManagerAdRequest!!, callBack)
                }
            } else {
                loadAd(adUnit, adManagerAdRequest!!, callBack)
            }
        }
    }

    private fun checkHijack(hijackConfig: SDKConfig.LoadConfig?): Boolean {
        return if (hijackConfig?.status == 1) {
            val number = (1..100).random()
            number in 1..(hijackConfig.per ?: 100)
        } else {
            false
        }
    }

    private fun loadAd(adUnit: String, adRequest: AdManagerAdRequest, callBack: (interstitialAd: AdManagerInterstitialAd?) -> Unit) {
        otherUnit = adUnit != this.adUnit
        fetchDemand(adRequest) {
            AdManagerInterstitialAd.load(context, adUnit, adRequest, object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    interstitialConfig.retryConfig = sdkConfig?.retryConfig
                    addGeoEdge(interstitialAd, otherUnit)
                    callBack(interstitialAd)
                    firstLook = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Logger.ERROR.log(msg = adError.message)
                    val tempStatus = firstLook
                    if (firstLook) {
                        firstLook = false
                    }
                    try {
                        adFailedToLoad(tempStatus, callBack)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        callBack(null)
                    }
                }
            })
        }
    }

    private fun addGeoEdge(interstitialAd: AdManagerInterstitialAd, otherUnit: Boolean) {
        try {
            val number = (1..100).random()
            if ((!otherUnit && (number in 1..(sdkConfig?.geoEdge?.firstLook ?: 0))) ||
                    (otherUnit && (number in 1..(sdkConfig?.geoEdge?.other ?: 0)))) {
                AppHarbr.addInterstitial(AdSdk.GAM, interstitialAd, object : AHIncident {
                    override fun onAdBlocked(p0: Any?, p1: String?, p2: AdFormat, reasons: Array<out AdBlockReason>) {
                        log { "Interstitial : onAdBlocked : ${Gson().toJson(reasons.asList().map { it.reason })}" }
                    }

                    override fun onAdIncident(p0: Any?, p1: String?, p2: AdSdk?, p3: String?, p4: AdFormat, p5: Array<out AdBlockReason>, reportReasons: Array<out AdBlockReason>) {
                        log { "Interstitial: onAdIncident : ${Gson().toJson(reportReasons.asList().map { it.reason })}" }
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun adFailedToLoad(firstLook: Boolean, callBack: (interstitialAd: AdManagerInterstitialAd?) -> Unit) {
        fun requestAd() {
            createRequest(unfilled = true).getAdRequest()?.let {
                loadAd(getAdUnitName(unfilled = true, hijacked = false, newUnit = false), it, callBack)
            }
        }
        if (interstitialConfig.unFilled?.status == 1) {
            if (firstLook) {
                requestAd()
            } else {
                if ((interstitialConfig.retryConfig?.retries ?: 0) > 0) {
                    interstitialConfig.retryConfig?.retries = (interstitialConfig.retryConfig?.retries ?: 0) - 1
                    Handler(Looper.getMainLooper()).postDelayed({
                        interstitialConfig.retryConfig?.adUnits?.firstOrNull()?.let {
                            interstitialConfig.retryConfig?.adUnits?.removeAt(0)
                            overridingUnit = it
                            requestAd()
                        } ?: kotlin.run {
                            overridingUnit = null
                            callBack(null)
                        }
                    }, (interstitialConfig.retryConfig?.retryInterval ?: 0).toLong() * 1000)
                } else {
                    overridingUnit = null
                    callBack(null)
                }
            }
        } else {
            callBack(null)
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun shouldSetConfig(callback: (Boolean) -> Unit) {
        val workManager = AndBeyondMedia.getWorkManager(context)
        val workers = workManager.getWorkInfosForUniqueWork(ConfigSetWorker::class.java.simpleName).get()
        if (workers.isNullOrEmpty()) {
            callback(false)
        } else {
            try {
                val workerData = workManager.getWorkInfoByIdLiveData(workers[0].id)
                workerData?.observeForever(object : Observer<WorkInfo?> {
                    override fun onChanged(value: WorkInfo?) {
                        if (value?.state != WorkInfo.State.RUNNING && value?.state != WorkInfo.State.ENQUEUED) {
                            workerData.removeObserver(this)
                            sdkConfig = storeService.config
                            shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
                            callback(shouldBeActive)
                        }
                    }
                })
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    private fun setConfig() {
        if (!shouldBeActive) return
        if (sdkConfig?.getBlockList()?.contains(adUnit) == true) {
            shouldBeActive = false
            return
        }
        val validConfig = sdkConfig?.refreshConfig?.firstOrNull { config -> config.specific?.equals(adUnit, true) == true || config.type == AdTypes.INTERSTITIAL || config.type.equals("all", true) }
        if (validConfig == null) {
            shouldBeActive = false
            return
        }
        val networkName = if (sdkConfig?.networkCode.isNullOrEmpty()) sdkConfig?.networkId else String.format("%s,%s", sdkConfig?.networkId, sdkConfig?.networkCode)
        interstitialConfig.apply {
            customUnitName = String.format("/%s/%s-%s", networkName, sdkConfig?.affiliatedId.toString(), validConfig.nameType ?: "")
            position = validConfig.position ?: 0
            isNewUnit = adUnit.contains(sdkConfig?.networkId ?: "")
            placement = validConfig.placement
            newUnit = sdkConfig?.hijackConfig?.newUnit
            retryConfig = sdkConfig?.retryConfig
            hijack = sdkConfig?.hijackConfig?.inter ?: sdkConfig?.hijackConfig?.other
            unFilled = sdkConfig?.unfilledConfig?.inter ?: sdkConfig?.unfilledConfig?.other
            format = validConfig.format
        }
    }

    private fun getAdUnitName(unfilled: Boolean, hijacked: Boolean, newUnit: Boolean): String {
        return overridingUnit ?: String.format("%s-%d", interstitialConfig.customUnitName, if (unfilled) interstitialConfig.unFilled?.number else if (newUnit) interstitialConfig.newUnit?.number else if (hijacked) interstitialConfig.hijack?.number else interstitialConfig.position)
    }

    private fun createRequest(unfilled: Boolean = false, hijacked: Boolean = false) = AdRequest().Builder().apply {
        addCustomTargeting("adunit", adUnit)
        addCustomTargeting("hb_format", sdkConfig?.hbFormat ?: "amp")
        if (unfilled) addCustomTargeting("retry", "1")
        if (hijacked) addCustomTargeting("hijack", "1")
    }.build()

    private fun fetchDemand(adRequest: AdManagerAdRequest, callback: () -> Unit) {
        if ((!otherUnit && sdkConfig?.prebid?.firstLook == 1) || (otherUnit && sdkConfig?.prebid?.other == 1)) {
            val formatNeeded = interstitialConfig.format
            val adUnit = if (formatNeeded.isNullOrEmpty() || (formatNeeded.contains("html", true) && !formatNeeded.contains("video", true))) {
                InterstitialAdUnit((if (otherUnit) interstitialConfig.placement?.other ?: 0 else interstitialConfig.placement?.firstLook ?: 0).toString(), 80, 60)
            } else if (formatNeeded.contains("video", true) && !formatNeeded.contains("html", true)) {
                InterstitialAdUnit((if (otherUnit) interstitialConfig.placement?.other ?: 0 else interstitialConfig.placement?.firstLook ?: 0).toString(), EnumSet.of(AdUnitFormat.VIDEO)).apply {
                    videoParameters = configureVideoParameters()
                }
            } else {
                InterstitialAdUnit((if (otherUnit) interstitialConfig.placement?.other ?: 0 else interstitialConfig.placement?.firstLook ?: 0).toString(), EnumSet.of(AdUnitFormat.VIDEO, AdUnitFormat.VIDEO)).apply {
                    setMinSizePercentage(80, 60)
                    videoParameters = configureVideoParameters()
                }
            }
            adUnit.fetchDemand(adRequest) { callback() }
        } else {
            callback()
        }
    }

    private fun configureVideoParameters(): VideoParameters {
        return VideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = Signals.Placement.Interstitial
            api = listOf(Signals.Api.VPAID_1, Signals.Api.VPAID_2)
            maxBitrate = 1500
            minBitrate = 300
            maxDuration = 30
            minDuration = 5
            playbackMethod = listOf(Signals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(Signals.Protocols.VAST_2_0)
        }
    }
}