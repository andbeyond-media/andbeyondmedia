package com.rtb.andbeyondmedia.rewardedinterstitial

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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.gson.Gson
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.intersitial.InterstitialConfig
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.ConfigSetWorker
import com.rtb.andbeyondmedia.sdk.Logger
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log
import org.prebid.mobile.InterstitialAdUnit
import org.prebid.mobile.api.data.AdUnitFormat
import java.util.EnumSet

internal class RewardedInterstitialAdManager(private val context: Activity, private val adUnit: String) {

    private var sdkConfig: SDKConfig? = null
    private var config: InterstitialConfig = InterstitialConfig()
    private var shouldBeActive: Boolean = false
    private val storeService = AndBeyondMedia.getStoreService(context)
    private var firstLook: Boolean = true
    private var overridingUnit: String? = null
    private var otherUnit = false

    init {
        sdkConfig = storeService.config
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
    }

    fun load(adRequest: AdRequest, callBack: (rewardedInterstitialAd: RewardedInterstitialAd?) -> Unit) {
        var adManagerAdRequest = adRequest.getAdRequest()
        if (adManagerAdRequest == null) {
            callBack(null)
            return
        }
        shouldSetConfig {
            if (it) {
                setConfig()
                if (config.isNewUnit) {
                    createRequest().getAdRequest()?.let { request ->
                        adManagerAdRequest = request
                        loadAd(getAdUnitName(false, hijacked = false, newUnit = true), request, callBack)
                    }
                } else if (config.hijack?.status == 1) {
                    createRequest().getAdRequest()?.let { request ->
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

    private fun loadAd(adUnit: String, adRequest: AdManagerAdRequest, callBack: (rewardedInterstitialAd: RewardedInterstitialAd?) -> Unit) {
        otherUnit = adUnit != this.adUnit
        fetchDemand(adRequest) {
            RewardedInterstitialAd.load(context, adUnit, adRequest, object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    config.retryConfig = sdkConfig?.retryConfig.also { it?.fillAdUnits() }
                    addGeoEdge(ad, firstLook)
                    callBack(ad)
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

    private fun addGeoEdge(rewarded: RewardedInterstitialAd, otherUnit: Boolean) {
        try {
            val number = (1..100).random()
            if ((!otherUnit && (number in 1..(sdkConfig?.geoEdge?.firstLook ?: 0))) ||
                    (otherUnit && (number in 1..(sdkConfig?.geoEdge?.other ?: 0)))) {
                AppHarbr.addRewardedInterstitialAd(AdSdk.GAM, rewarded, object : AHIncident {
                    override fun onAdBlocked(p0: Any?, p1: String?, p2: AdFormat, reasons: Array<out AdBlockReason>) {
                        log { "RewardedInterstitialAd : onAdBlocked : ${Gson().toJson(reasons.asList().map { it.reason })}" }
                    }

                    override fun onAdIncident(p0: Any?, p1: String?, p2: AdSdk?, p3: String?, p4: AdFormat, p5: Array<out AdBlockReason>, reportReasons: Array<out AdBlockReason>) {
                        log { "RewardedInterstitialAd: onAdIncident : ${Gson().toJson(reportReasons.asList().map { it.reason })}" }
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun adFailedToLoad(firstLook: Boolean, callBack: (rewardedInterstitialAd: RewardedInterstitialAd?) -> Unit) {
        fun requestAd() {
            createRequest().getAdRequest()?.let {
                loadAd(getAdUnitName(unfilled = true, hijacked = false, newUnit = false), it, callBack)
            }
        }
        if (config.unFilled?.status == 1) {
            if (firstLook) {
                requestAd()
            } else {
                if ((config.retryConfig?.retries ?: 0) > 0) {
                    config.retryConfig?.retries = (config.retryConfig?.retries ?: 0) - 1
                    Handler(Looper.getMainLooper()).postDelayed({
                        config.retryConfig?.adUnits?.firstOrNull()?.let {
                            config.retryConfig?.adUnits?.removeAt(0)
                            overridingUnit = it
                            requestAd()
                        } ?: kotlin.run {
                            overridingUnit = null
                            callBack(null)
                        }
                    }, (config.retryConfig?.retryInterval ?: 0).toLong() * 1000)
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
                workerData?.observeForever(object : Observer<WorkInfo> {
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    override fun onChanged(value: WorkInfo) {
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
        val validConfig = sdkConfig?.refreshConfig?.firstOrNull { config -> config.specific?.equals(adUnit, true) == true || config.type == AdTypes.REWARDEDINTERSTITIAL || config.type.equals("all", true) }
        if (validConfig == null) {
            shouldBeActive = false
            return
        }
        val networkName = if (sdkConfig?.networkCode.isNullOrEmpty()) sdkConfig?.networkId else String.format("%s,%s", sdkConfig?.networkId, sdkConfig?.networkCode)
        config.apply {
            customUnitName = String.format("/%s/%s-%s", networkName, sdkConfig?.affiliatedId.toString(), validConfig.nameType ?: "")
            position = validConfig.position ?: 0
            isNewUnit = adUnit.contains(sdkConfig?.networkId ?: "")
            placement = validConfig.placement
            newUnit = sdkConfig?.hijackConfig?.newUnit
            retryConfig = sdkConfig?.retryConfig.also { it?.fillAdUnits() }
            hijack = sdkConfig?.hijackConfig?.reward ?: sdkConfig?.hijackConfig?.other
            unFilled = sdkConfig?.unfilledConfig?.reward ?: sdkConfig?.unfilledConfig?.other
        }
    }

    private fun getAdUnitName(unfilled: Boolean, hijacked: Boolean, newUnit: Boolean): String {
        return overridingUnit ?: String.format("%s-%d", config.customUnitName, if (unfilled) config.unFilled?.number else if (newUnit) config.newUnit?.number else if (hijacked) config.hijack?.number else config.position)
    }

    private fun createRequest() = AdRequest().Builder().apply {
        addCustomTargeting("adunit", adUnit)
        addCustomTargeting("hb_format", "video")
    }.build()

    private fun fetchDemand(adRequest: AdManagerAdRequest, callback: () -> Unit) {
        if ((!otherUnit && sdkConfig?.prebid?.firstLook == 1) || (otherUnit && sdkConfig?.prebid?.other == 1)) {
            val adUnit = InterstitialAdUnit((if (otherUnit) config.placement?.other ?: 0 else config.placement?.firstLook ?: 0).toString(), EnumSet.of(AdUnitFormat.VIDEO))
            adUnit.fetchDemand(adRequest) { callback() }
        } else {
            callback()
        }
    }
}