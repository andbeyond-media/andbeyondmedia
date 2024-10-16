package com.rtb.andbeyondmedia.unified

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.banners.BannerAdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.intersitial.InterstitialConfig
import com.rtb.andbeyondmedia.sdk.ABMError
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.BannerAdListener
import com.rtb.andbeyondmedia.sdk.ConfigSetWorker
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.prebid.mobile.NativeAdUnit
import org.prebid.mobile.NativeDataAsset
import org.prebid.mobile.NativeEventTracker
import org.prebid.mobile.NativeImageAsset
import org.prebid.mobile.NativeTitleAsset
import java.util.Locale

class UnifiedAdManager(private val context: Context, private val adUnit: String) {
    private var sdkConfig: SDKConfig? = null
    private var nativeConfig: InterstitialConfig = InterstitialConfig()
    private var shouldBeActive: Boolean = false
    private val storeService = AndBeyondMedia.getStoreService(context)
    private var adOptions = NativeAdOptions.Builder().build()
    private var loadCount: Int = 0
    private var adListener: UnifiedAdListener? = null
    private var bannerAdListener: AdListener? = null
    private var otherUnit = false
    private lateinit var adLoader: AdLoader
    private var currentAdSizes: List<BannerAdSize> = arrayListOf()

    init {
        sdkConfig = storeService.config
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
    }

    fun setAdListener(adListener: UnifiedAdListener) {
        this.adListener = adListener
    }

    fun setNativeAdOptions(adOptions: NativeAdOptions) {
        this.adOptions = adOptions
    }

    fun setLoadCount(count: Int) {
        this.loadCount = count
    }

    fun isLoading(): Boolean {
        return this::adLoader.isInitialized && adLoader.isLoading
    }

    fun setAdSizes(vararg adSizes: BannerAdSize) {
        this.currentAdSizes = adSizes.toList()
    }

    fun load(adRequest: AdRequest) {
        var adManagerAdRequest = adRequest.getAdRequest() ?: return
        shouldSetConfig {
            if (it) {
                setConfig()
                if (nativeConfig.isNewUnitApplied()) {
                    adUnit.log { "new unit override on $adUnit" }
                    createRequest(newUnit = true).getAdRequest()?.let { request ->
                        adManagerAdRequest = request
                        loadAd(getAdUnitName(hijacked = false, newUnit = true), request)
                    }
                } else if (checkHijack(nativeConfig.hijack)) {
                    adUnit.log { "hijack override on $adUnit" }
                    createRequest(hijacked = true).getAdRequest()?.let { request ->
                        adManagerAdRequest = request
                        loadAd(getAdUnitName(hijacked = true, newUnit = false), request)
                    }
                } else {
                    loadAd(adUnit, adManagerAdRequest)
                }
            } else {
                loadAd(adUnit, adManagerAdRequest)
            }
        }
    }

    private fun loadAd(adUnit: String, adRequest: AdManagerAdRequest) {
        otherUnit = adUnit != this.adUnit
        this.adUnit.log { "loading $adUnit by Unified Native" }
        fetchDemand(adRequest) {
            adLoader = AdLoader.Builder(context, adUnit)
                    .forNativeAd { nativeAd: NativeAd ->
                        nativeConfig.retryConfig = sdkConfig?.retryConfig
                        adUnit.log { "loaded $adUnit by Unified native" }
                        adListener?.onNativeLoaded(nativeAd)
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdClicked() {
                            adListener?.onAdClicked()
                        }

                        override fun onAdClosed() {
                            adListener?.onAdClosed()
                        }

                        override fun onAdImpression() {
                            bannerAdListener?.onAdImpression()
                            adListener?.onAdImpression()
                        }

                        override fun onAdOpened() {
                            adListener?.onAdOpened()
                        }

                        override fun onAdSwipeGestureClicked() {
                            adListener?.onAdSwipeGestureClicked()
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            this@UnifiedAdManager.adUnit.log { "loading $adUnit failed by unified native with error : ${p0.message}" }
                            if (currentAdSizes.isEmpty()) {
                                adListener?.onAdFailedToLoad(ABMError(p0.code, p0.message, p0.domain))
                            } else {
                                createFreshBanner()
                            }
                        }
                    }).apply {
                        if (currentAdSizes.isNotEmpty()) {
                            this.forAdManagerAdView({
                                sendLoadedBanner(it, adUnit)
                            }, *convertToAdSizes(currentAdSizes).toTypedArray())
                        }
                    }
                    .withNativeAdOptions(adOptions)
                    .build()
            if (loadCount == 0) {
                adLoader.loadAd(adRequest)
            } else {
                adLoader.loadAds(adRequest, loadCount)
            }
        }
    }

    private fun sendLoadedBanner(adView: AdManagerAdView, adUnit: String) {
        adUnit.log { "loaded $adUnit by Unified native banner" }
        val banner = BannerAdView(context)
        bannerAdListener = banner.setAdView(sdkConfig, adView, convertToAdSizes(currentAdSizes), adUnit)
        bannerAdListener?.onAdLoaded()
        adListener?.onBannerLoaded(banner)
    }

    private fun createFreshBanner() {
        val adUnitForBanner = getAdUnitForBanner()
        val banner = BannerAdView(context)
        banner.setAdSizes(*currentAdSizes.toTypedArray())
        banner.setAdUnitID(adUnitForBanner)
        banner.setAdListener(object : BannerAdListener {
            override fun onAdClicked(bannerAdView: BannerAdView) {
                adListener?.onAdClicked()
            }

            override fun onAdClosed(bannerAdView: BannerAdView) {
                adListener?.onAdClosed()
            }

            override fun onAdFailedToLoad(bannerAdView: BannerAdView, error: ABMError, retrying: Boolean) {
                if (!retrying) {
                    this@UnifiedAdManager.adUnit.log { "loading $adUnitForBanner failed by unified banner with error : ${error.message}" }
                    adListener?.onAdFailedToLoad(error)
                }
            }

            override fun onAdImpression(bannerAdView: BannerAdView) {
                adListener?.onAdImpression()
            }

            override fun onAdLoaded(bannerAdView: BannerAdView) {
                adListener?.onBannerLoaded(bannerAdView)
            }

            override fun onAdOpened(bannerAdView: BannerAdView) {
                adListener?.onAdOpened()
            }

        })
        banner.loadAd(createRequest(unfilled = true))
        adUnit.log { "Loading $adUnitForBanner using unified banner" }
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun shouldSetConfig(callback: (Boolean) -> Unit) = CoroutineScope(Dispatchers.Main).launch {
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
            } catch (e: Throwable) {
                callback(false)
            }
        }
    }

    private fun setConfig() {
        adUnit.log { String.format("%s:%s- Version:%s", "setConfig", "entry", BuildConfig.ADAPTER_VERSION) }
        if (!shouldBeActive) return
        if (sdkConfig?.getBlockList()?.contains(adUnit) == true) {
            shouldBeActive = false
            return
        }
        val validConfig = sdkConfig?.refreshConfig?.firstOrNull { config -> config.specific?.equals(adUnit, true) == true || config.type == AdTypes.NATIVE || config.type.equals("all", true) }
        if (validConfig == null) {
            shouldBeActive = false
            return
        }
        nativeConfig.apply {
            customUnitName = String.format("/%s/%s-%s", getNetworkName(), sdkConfig?.affiliatedId.toString(), validConfig.nameType ?: "")
            position = validConfig.position ?: 0
            isNewUnit = adUnit.contains(sdkConfig?.networkId ?: "")
            placement = validConfig.placement
            newUnit = sdkConfig?.hijackConfig?.newUnit
            retryConfig = sdkConfig?.retryConfig
            hijack = sdkConfig?.hijackConfig?.native ?: sdkConfig?.hijackConfig?.other
            unFilled = sdkConfig?.unfilledConfig?.native ?: sdkConfig?.unfilledConfig?.other
        }
        adUnit.log { "setConfig :$nativeConfig" }
    }

    private fun getNetworkName(): String? {
        return if (sdkConfig?.networkCode.isNullOrEmpty()) sdkConfig?.networkId else String.format("%s,%s", sdkConfig?.networkId, sdkConfig?.networkCode)
    }

    private fun getAdUnitName(hijacked: Boolean, newUnit: Boolean): String {
        return String.format(Locale.ENGLISH, "%s-%d", nativeConfig.customUnitName, if (newUnit) nativeConfig.newUnit?.number else if (hijacked) nativeConfig.hijack?.number else nativeConfig.position)
    }

    private fun getAdUnitForBanner(): String {
        val validConfig = sdkConfig?.refreshConfig?.firstOrNull { config -> config.specific?.equals(adUnit, true) == true || config.type == AdTypes.BANNER || config.type.equals("all", true) }
        val customUnitName = String.format("/%s/%s-%s", getNetworkName(), sdkConfig?.affiliatedId.toString(), getUnitNameType(validConfig?.nameType ?: "", sdkConfig?.supportedSizes, currentAdSizes))
        val unfilledConfig = sdkConfig?.unfilledConfig?.banner ?: sdkConfig?.unfilledConfig?.other
        return String.format(Locale.ENGLISH, "%s-%d", customUnitName, unfilledConfig?.number ?: 2)
    }

    private fun getUnitNameType(type: String, supportedSizes: List<SDKConfig.Size>?, pubSizes: List<BannerAdSize>): String {
        if (supportedSizes.isNullOrEmpty()) return type
        else {
            val matchedSizes = arrayListOf<SDKConfig.Size>()
            pubSizes.forEach { pubsize ->
                supportedSizes.firstOrNull { (it.width?.toIntOrNull() ?: 0) == pubsize.adSize.width && (it.height?.toIntOrNull() ?: 0) == pubsize.adSize.height }?.let { matchedSize ->
                    matchedSizes.add(matchedSize)
                }
            }
            var biggestSize: SDKConfig.Size? = null
            var maxArea = 0
            matchedSizes.forEach {
                if (maxArea < ((it.width?.toIntOrNull() ?: 0) * (it.height?.toIntOrNull() ?: 0))) {
                    biggestSize = it
                    maxArea = (it.width?.toIntOrNull() ?: 0) * (it.height?.toIntOrNull() ?: 0)
                }
            }
            return biggestSize?.let { String.format("%s-%s", it.width, it.height) } ?: type
        }
    }

    private fun createRequest(unfilled: Boolean = false, hijacked: Boolean = false, newUnit: Boolean = false) = AdRequest().Builder().apply {
        addCustomTargeting("adunit", adUnit)
        addCustomTargeting("hb_format", sdkConfig?.hbFormat ?: "amp")
        addCustomTargeting("sdk_version", BuildConfig.ADAPTER_VERSION)
        if (unfilled) addCustomTargeting("retry", "1")
        if (hijacked) addCustomTargeting("hijack", "1")
        if (newUnit) addCustomTargeting("new_unit", "1")
    }.build()

    private fun checkHijack(hijackConfig: SDKConfig.LoadConfig?): Boolean {
        return if (hijackConfig?.status == 1) {
            val number = (1..100).random()
            number in 1..(hijackConfig.per ?: 100)
        } else {
            false
        }
    }

    fun convertToAdSizes(adSizes: List<BannerAdSize>): ArrayList<AdSize> {
        val adSizeList = arrayListOf<AdSize>()
        adSizes.forEach {
            adSizeList.add(it.adSize)
        }
        return adSizeList
    }

    private fun fetchDemand(adRequest: AdManagerAdRequest, callback: () -> Unit) {
        if (sdkConfig?.prebid?.whitelistedFormats != null && sdkConfig?.prebid?.whitelistedFormats?.contains(AdTypes.NATIVE) == false) {
            callback()
            return
        }

        if ((nativeConfig.isNewUnitApplied() && sdkConfig?.prebid?.other == 1) ||
                (otherUnit && !nativeConfig.isNewUnitApplied() && sdkConfig?.prebid?.retry == 1) ||
                (!otherUnit && sdkConfig?.prebid?.firstLook == 1)
        ) {
            adUnit.log { "Fetch Demand with prebid" }
            val adUnit = NativeAdUnit((if (otherUnit) nativeConfig.placement?.other ?: 0 else nativeConfig.placement?.firstLook ?: 0).toString())
            adUnit.setContextType(NativeAdUnit.CONTEXT_TYPE.SOCIAL_CENTRIC)
            adUnit.setPlacementType(NativeAdUnit.PLACEMENTTYPE.CONTENT_FEED)
            adUnit.setContextSubType(NativeAdUnit.CONTEXTSUBTYPE.GENERAL_SOCIAL)
            addNativeAssets(adUnit)
            adUnit.fetchDemand(adRequest) { callback() }
        } else {
            callback()
        }
    }

    private fun addNativeAssets(adUnit: NativeAdUnit?) {
        // ADD NATIVE ASSETS

        val title = NativeTitleAsset()
        title.setLength(90)
        title.isRequired = true
        adUnit?.addAsset(title)

        val icon = NativeImageAsset(20, 20, 20, 20)
        icon.imageType = NativeImageAsset.IMAGE_TYPE.ICON
        icon.isRequired = true
        adUnit?.addAsset(icon)

        val image = NativeImageAsset(200, 200, 200, 200)
        image.imageType = NativeImageAsset.IMAGE_TYPE.MAIN
        image.isRequired = true
        adUnit?.addAsset(image)

        val data = NativeDataAsset()
        data.len = 90
        data.dataType = NativeDataAsset.DATA_TYPE.SPONSORED
        data.isRequired = true
        adUnit?.addAsset(data)

        val body = NativeDataAsset()
        body.isRequired = true
        body.dataType = NativeDataAsset.DATA_TYPE.DESC
        adUnit?.addAsset(body)

        val cta = NativeDataAsset()
        cta.isRequired = true
        cta.dataType = NativeDataAsset.DATA_TYPE.CTATEXT
        adUnit?.addAsset(cta)

        // ADD NATIVE EVENT TRACKERS
        val methods = ArrayList<NativeEventTracker.EVENT_TRACKING_METHOD>()
        methods.add(NativeEventTracker.EVENT_TRACKING_METHOD.IMAGE)
        methods.add(NativeEventTracker.EVENT_TRACKING_METHOD.JS)
        try {
            val tracker = NativeEventTracker(NativeEventTracker.EVENT_TYPE.IMPRESSION, methods)
            adUnit?.addEventTracker(tracker)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}