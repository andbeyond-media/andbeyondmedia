package com.rtb.andbeyondmedia.banners

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.amazon.device.ads.AdError
import com.amazon.device.ads.DTBAdCallback
import com.amazon.device.ads.DTBAdRequest
import com.amazon.device.ads.DTBAdResponse
import com.amazon.device.ads.DTBAdSize
import com.amazon.device.ads.DTBAdUtil
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdapterResponseInfo
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.gson.Gson
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.common.connectionAvailable
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.BannerManagerListener
import com.rtb.andbeyondmedia.sdk.ConfigSetWorker
import com.rtb.andbeyondmedia.sdk.CountryDetectionWorker
import com.rtb.andbeyondmedia.sdk.CountryModel
import com.rtb.andbeyondmedia.sdk.Fallback
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.Signals
import org.prebid.mobile.VideoParameters
import org.prebid.mobile.api.data.AdUnitFormat
import java.util.Date
import java.util.EnumSet
import kotlin.math.ceil


internal class BannerManager(private val context: Context, private val bannerListener: BannerManagerListener, private val view: View? = null) {

    private var activeTimeCounter: CountDownTimer? = null
    private var passiveTimeCounter: CountDownTimer? = null
    private var unfilledRefreshCounter: CountDownTimer? = null
    private var bannerConfig = BannerConfig()
    private var sdkConfig: SDKConfig? = null
    private var shouldBeActive: Boolean = false
    private var wasFirstLook = true
    private val storeService = AndBeyondMedia.getStoreService(context)
    private var isForegroundRefresh = 1
    private var overridingUnit: String? = null
    private var refreshBlocked = false
    private var adType: String = ""
    private var pubAdSizes: ArrayList<AdSize> = arrayListOf()
    private var countrySetup = Triple<Boolean, Boolean, CountryModel?>(false, false, null) //fetched, applied, config

    init {
        sdkConfig = storeService.config
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
        getCountryConfig()
    }

    private fun shouldBeActive() = shouldBeActive

    fun convertStringSizesToAdSizes(adSizes: String): ArrayList<AdSize> {
        fun getAdSizeObj(adSize: String) = when (adSize) {
            "FLUID" -> AdSize.FLUID
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

    fun convertVaragsToAdSizes(vararg adSizes: BannerAdSize): ArrayList<AdSize> {
        val adSizeList = arrayListOf<AdSize>()
        adSizes.toList().forEach {
            adSizeList.add(it.adSize)
        }
        return adSizeList
    }

    fun clearConfig() {
        storeService.config = null
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun getCountryConfig() {
        val workManager = AndBeyondMedia.getWorkManager(context)
        val workers = workManager.getWorkInfosForUniqueWork(CountryDetectionWorker::class.java.simpleName).get()
        if (workers.isNullOrEmpty()) {
            return
        }
        try {
            val workerData = workManager.getWorkInfoByIdLiveData(workers[0].id)
            workerData?.observeForever(object : Observer<WorkInfo?> {
                override fun onChanged(value: WorkInfo?) {
                    if (value?.state != WorkInfo.State.RUNNING && value?.state != WorkInfo.State.ENQUEUED) {
                        workerData.removeObserver(this)
                        countrySetup = Triple(true, false, storeService.detectedCountry)
                    }
                }
            })
        } catch (e: Throwable) {
            e.message
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun shouldSetConfig(callback: (Boolean) -> Unit) {
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

    fun setConfig(pubAdUnit: String, adSizes: ArrayList<AdSize>, adType: String) {
        view.log { String.format("%s:%s- Version:%s", "setConfig", "entry", BuildConfig.ADAPTER_VERSION) }
        if (!shouldBeActive()) return
        if (sdkConfig?.getBlockList()?.any { pubAdUnit.contains(it) } == true) {
            shouldBeActive = false
            return
        }
        val validConfig = sdkConfig?.refreshConfig?.firstOrNull { config -> config.specific?.equals(pubAdUnit, true) == true || config.type == adType || config.type.equals("all", true) }
        if (validConfig == null) {
            shouldBeActive = false
            return
        }
        this.adType = adType
        this.pubAdSizes = adSizes
        bannerConfig.apply {
            customUnitName = String.format("/%s/%s-%s", getNetworkName(), sdkConfig?.affiliatedId.toString(), getUnitNameType(validConfig.nameType ?: "", sdkConfig?.supportedSizes, adSizes))
            isNewUnit = pubAdUnit.contains(sdkConfig?.networkId ?: "")
            publisherAdUnit = pubAdUnit
            position = validConfig.position ?: 0
            placement = validConfig.placement
            newUnit = sdkConfig?.hijackConfig?.newUnit
            retryConfig = sdkConfig?.retryConfig
            hijack = getValidLoadConfig(adType, true, sdkConfig?.hijackConfig, sdkConfig?.unfilledConfig)
            unFilled = getValidLoadConfig(adType, false, sdkConfig?.hijackConfig, sdkConfig?.unfilledConfig)
            difference = sdkConfig?.difference ?: 0
            activeRefreshInterval = sdkConfig?.activeRefreshInterval ?: 0
            passiveRefreshInterval = sdkConfig?.passiveRefreshInterval ?: 0
            factor = sdkConfig?.factor ?: 1
            visibleFactor = sdkConfig?.visibleFactor ?: 1
            minView = sdkConfig?.minView ?: 0
            minViewRtb = sdkConfig?.minViewRtb ?: 0
            format = validConfig.format
            fallback = sdkConfig?.fallback
            geoEdge = sdkConfig?.geoEdge
            nativeFallback = sdkConfig?.nativeFallback
            this.adSizes = if (validConfig.follow == 1 && !validConfig.sizes.isNullOrEmpty()) {
                getCustomSizes(adSizes, validConfig.sizes)
            } else {
                adSizes
            }
        }
        view.log { String.format("%s:%s", "setConfig", Gson().toJson(bannerConfig)) }
        setCountryConfig()
    }

    private fun getUnitNameType(type: String, supportedSizes: List<SDKConfig.Size>?, pubSizes: List<AdSize>): String {
        if (supportedSizes.isNullOrEmpty()) return type
        else {
            val matchedSizes = arrayListOf<SDKConfig.Size>()
            pubSizes.forEach { pubsize ->
                supportedSizes.firstOrNull { (it.width?.toIntOrNull() ?: 0) == pubsize.width && (it.height?.toIntOrNull() ?: 0) == pubsize.height }?.let { matchedSize ->
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

    private fun getNetworkName(): String? {
        return if (sdkConfig?.networkCode.isNullOrEmpty()) sdkConfig?.networkId else String.format("%s,%s", sdkConfig?.networkId, sdkConfig?.networkCode)
    }

    private fun getValidLoadConfig(adType: String, forHijack: Boolean, hijackConfig: SDKConfig.LoadConfigs?, unfilledConfig: SDKConfig.LoadConfigs?): SDKConfig.LoadConfig? {
        var validConfig = when {
            adType.equals(AdTypes.BANNER, true) -> if (forHijack) hijackConfig?.banner else unfilledConfig?.banner
            adType.equals(AdTypes.INLINE, true) -> if (forHijack) hijackConfig?.inline else unfilledConfig?.inline
            adType.equals(AdTypes.ADAPTIVE, true) -> if (forHijack) hijackConfig?.adaptive else unfilledConfig?.adaptive
            adType.equals(AdTypes.INREAD, true) -> if (forHijack) hijackConfig?.inread else unfilledConfig?.inread
            adType.equals(AdTypes.STICKY, true) -> if (forHijack) hijackConfig?.sticky else unfilledConfig?.sticky
            else -> if (forHijack) hijackConfig?.other else unfilledConfig?.other
        }
        if (validConfig == null) {
            validConfig = if (forHijack) hijackConfig?.other else unfilledConfig?.other
        }
        return validConfig
    }

    private fun getCustomSizes(adSizes: ArrayList<AdSize>, sizeOptions: List<SDKConfig.Size>): ArrayList<AdSize> {
        val sizes = ArrayList<AdSize>()
        adSizes.forEach {
            val lookingWidth = if (it.width != 0) it.width.toString() else "ALL"
            val lookingHeight = if (it.height != 0) it.height.toString() else "ALL"
            sizeOptions.firstOrNull { size -> size.height == lookingHeight && size.width == lookingWidth }?.sizes?.forEach { selectedSize ->
                if (selectedSize.width == "ALL" || selectedSize.height == "ALL") {
                    sizes.add(it)
                } else if (sizes.none { size -> size.width == (selectedSize.width?.toIntOrNull() ?: 0) && size.height == (selectedSize.height?.toIntOrNull() ?: 0) }) {
                    sizes.add(AdSize((selectedSize.width?.toIntOrNull() ?: 0), (selectedSize.height?.toIntOrNull() ?: 0)))
                }
            }
        }
        return sizes
    }

    private fun setCountryConfig() {
        if (sdkConfig?.countryStatus?.active != 1) return
        if (!countrySetup.first || countrySetup.second || countrySetup.third == null || countrySetup.third?.countryCode.isNullOrEmpty() || sdkConfig?.homeCountry?.contains(countrySetup.third?.countryCode ?: "IN", true) == true) return
        bannerConfig = bannerConfig.apply {
            val currentCountry = countrySetup.third?.countryCode ?: "IN"
            val validCountryConfig = sdkConfig?.countryConfigs?.firstOrNull { config -> config.name?.contains(currentCountry, true) == true || config.name?.contains("other") == true }
                    ?: return@apply
            val validRefreshConfig = validCountryConfig.refreshConfig?.firstOrNull { config ->
                config.specific?.equals(this.publisherAdUnit, true) == true
                        || config.type == adType || config.type.equals("all", true)
            }
            validRefreshConfig?.let {
                customUnitName = String.format("/%s/%s-%s", getNetworkName(), sdkConfig?.affiliatedId.toString(), getUnitNameType(it.nameType ?: "", validCountryConfig.supportedSizes, pubAdSizes))
                position = it.position ?: 0
                placement = it.placement
                format = it.format
                this.adSizes = if (it.follow == 1 && !it.sizes.isNullOrEmpty()) {
                    getCustomSizes(pubAdSizes, it.sizes)
                } else {
                    pubAdSizes
                }
            }
            validCountryConfig.hijackConfig?.newUnit?.let { newUnit = it }
            validCountryConfig.hijackConfig?.let { hijack = getValidLoadConfig(adType, true, it, null) }
            validCountryConfig.unfilledConfig?.let { unFilled = getValidLoadConfig(adType, false, null, it) }
            validCountryConfig.diff?.let { difference = it }
            validCountryConfig.activeRefreshInterval?.let { activeRefreshInterval = it }
            validCountryConfig.passiveRefreshInterval?.let { passiveRefreshInterval = it }
            validCountryConfig.factor?.let { factor = it }
            validCountryConfig.visibleFactor?.let { visibleFactor = it }
            validCountryConfig.minView?.let { minView = it }
            validCountryConfig.minViewRtb?.let { minViewRtb = it }
            validCountryConfig.fallback?.let { fallback = it }
            validCountryConfig.geoEdge?.let { geoEdge = it }
            validCountryConfig.nativeFallback?.let { nativeFallback = it }
        }
        countrySetup = Triple(countrySetup.first, true, countrySetup.third)
        view.log { String.format("%s:%s", "set CountryWise Config", Gson().toJson(bannerConfig)) }
    }

    fun saveVisibility(visible: Boolean) {
        if (visible == bannerConfig.isVisible) return
        bannerConfig.isVisible = visible
    }

    fun adReported(creativeId: String?, reportReasons: List<String>) {
        if (bannerConfig.geoEdge?.creativeIds?.replace(" ", "")?.split(",")?.contains(creativeId) == true) {
            refreshBlocked = true
        }
        val configReasons = bannerConfig.geoEdge?.reasons?.replace(" ", "")?.split(",")
        configReasons?.forEach { reason ->
            if (reportReasons.any { reason.contains(it) }) {
                refreshBlocked = true
            }
        }
        if (refreshBlocked) {
            activeTimeCounter?.cancel()
            passiveTimeCounter?.cancel()
            unfilledRefreshCounter?.cancel()
        }
    }

    fun adFailedToLoad(isPublisherLoad: Boolean, recalled: Boolean = false): Boolean {
        if (!recalled) {
            setCountryConfig()
            view.log { "Failed with Unfilled Config: ${Gson().toJson(bannerConfig.unFilled)} && Retry config : ${Gson().toJson(bannerConfig.retryConfig)}" }
        }

        if (shouldBeActive) {
            if (isPublisherLoad) {
                return if (bannerConfig.unFilled?.status == 1) {
                    startUnfilledRefreshCounter()
                    refresh(unfilled = true)
                    true
                } else {
                    false
                }

            } else {
                startUnfilledRefreshCounter()
                if ((bannerConfig.retryConfig?.retries ?: 0) > 0) {
                    bannerConfig.retryConfig?.retries = (bannerConfig.retryConfig?.retries ?: 0) - 1
                    Handler(Looper.getMainLooper()).postDelayed({
                        bannerConfig.retryConfig?.adUnits?.firstOrNull()?.let {
                            bannerConfig.retryConfig?.adUnits?.removeAt(0)
                            overridingUnit = it
                            refresh(unfilled = true)
                        } ?: kotlin.run {
                            overridingUnit = null
                        }
                    }, (bannerConfig.retryConfig?.retryInterval ?: 0).toLong() * 1000)
                    return true
                } else {
                    overridingUnit = null
                    return false
                }
            }
        } else {
            return false
        }
    }

    fun adLoaded(firstLook: Boolean, loadedUnit: String, loadedAdapter: AdapterResponseInfo?) {
        adImpressed()
        setCountryConfig()
        if (sdkConfig?.switch == 1 && !refreshBlocked) {
            overridingUnit = null
            bannerConfig.retryConfig = sdkConfig?.retryConfig
            unfilledRefreshCounter?.cancel()
            val blockedTerms = sdkConfig?.networkBlock?.replace(" ", "")?.split(",") ?: listOf()
            var isNetworkBlocked = false
            blockedTerms.forEach {
                if (it.isNotEmpty() && loadedAdapter?.adapterClassName?.contains(it, true) == true) {
                    isNetworkBlocked = true
                }
            }
            if (!isNetworkBlocked
                    && !(!loadedAdapter?.adSourceId.isNullOrEmpty() && blockedTerms.contains(loadedAdapter?.adSourceId))
                    && !(!loadedAdapter?.adSourceName.isNullOrEmpty() && blockedTerms.contains(loadedAdapter?.adSourceName))
                    && !(!loadedAdapter?.adSourceInstanceId.isNullOrEmpty() && blockedTerms.contains(loadedAdapter?.adSourceInstanceId))
                    && !(!loadedAdapter?.adSourceInstanceName.isNullOrEmpty() && blockedTerms.contains(loadedAdapter?.adSourceInstanceName))
                    && !ifUnitOnHold(loadedUnit)) {
                startRefreshing(resetVisibleTime = true, isPublisherLoad = firstLook)
            } else {
                view.log { "Refresh blocked" }
                passiveTimeCounter?.cancel()
                activeTimeCounter?.cancel()
            }
        }
    }

    fun adImpressed() {
        val currentTimeStamp = Date().time
        bannerConfig.lastRefreshAt = currentTimeStamp
    }

    private fun startRefreshing(resetVisibleTime: Boolean = false, isPublisherLoad: Boolean = false, timers: Int? = null) {
        view.log { "startRefreshing: resetVisibleTime: $resetVisibleTime isPublisherLoad: $isPublisherLoad timers: $timers" }
        if (resetVisibleTime) {
            bannerConfig.isVisibleFor = 0
        }
        this.wasFirstLook = isPublisherLoad
        bannerConfig.let {
            timers?.let { active ->
                if (active == 1) startActiveCounter(it.activeRefreshInterval.toLong())
                else startPassiveCounter(it.passiveRefreshInterval.toLong())
            } ?: kotlin.run {
                startPassiveCounter(it.passiveRefreshInterval.toLong())
                startActiveCounter(it.activeRefreshInterval.toLong())
            }
        }
    }

    private fun startActiveCounter(seconds: Long) {
        activeTimeCounter?.cancel()
        if (seconds <= 0) return
        activeTimeCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (bannerConfig.isVisible) {
                    bannerConfig.isVisibleFor++
                }
                bannerConfig.activeRefreshInterval--
            }

            override fun onFinish() {
                bannerConfig.activeRefreshInterval = sdkConfig?.activeRefreshInterval ?: 0
                refresh(1)
            }
        }
        activeTimeCounter?.start()
    }

    private fun startPassiveCounter(seconds: Long) {
        passiveTimeCounter?.cancel()
        if (seconds <= 0) return
        passiveTimeCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                bannerConfig.passiveRefreshInterval--
            }

            override fun onFinish() {
                bannerConfig.passiveRefreshInterval = sdkConfig?.passiveRefreshInterval ?: 0
                refresh(0)
            }
        }
        passiveTimeCounter?.start()
    }

    private fun startUnfilledRefreshCounter() {
        val passiveTime = sdkConfig?.passiveRefreshInterval?.toLong() ?: 0L
        val difference = sdkConfig?.difference?.toLong() ?: 0L
        val seconds = if (passiveTime <= difference) difference else passiveTime
        unfilledRefreshCounter?.cancel()
        if (seconds <= 0) return
        unfilledRefreshCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {

            }

            override fun onFinish() {
                refresh(0, true)
            }
        }
        unfilledRefreshCounter?.start()
    }

    fun refresh(active: Int = 1, unfilled: Boolean = false) {
        val currentTimeStamp = Date().time
        fun refreshAd() {
            bannerConfig.lastRefreshAt = currentTimeStamp
            bannerListener.attachAdView(getAdUnitName(unfilled, false), bannerConfig.adSizes.apply {
                if (ifNativePossible() && !this.contains(AdSize.FLUID)) {
                    add(AdSize.FLUID)
                }
            })
            loadAd(active, unfilled)
        }

        val differenceOfLastRefresh = ceil((currentTimeStamp - bannerConfig.lastRefreshAt).toDouble() / 1000.00).toInt()
        if (unfilled) {
            view.log { "Retrying" }
        }
        val timers = if (active == 0 && unfilled) {
            null
        } else {
            active
        }
        var takeOpportunity = false
        if (active == 1) {
            var pickOpportunity = false
            if (bannerConfig.isVisible) {
                pickOpportunity = true
            } else {
                if (bannerConfig.visibleFactor < 0) {
                    pickOpportunity = false
                } else {
                    if (ceil((currentTimeStamp - bannerConfig.lastActiveOpportunity).toDouble() / 1000.00).toInt() >= bannerConfig.visibleFactor * bannerConfig.activeRefreshInterval) {
                        pickOpportunity = true
                    }
                }
            }
            if (pickOpportunity) {
                bannerConfig.lastActiveOpportunity = currentTimeStamp
                if (differenceOfLastRefresh >= bannerConfig.difference && (bannerConfig.isVisibleFor >= (if (wasFirstLook || bannerConfig.isNewUnit) bannerConfig.minView else bannerConfig.minViewRtb))) {
                    takeOpportunity = true
                }
            }
        } else if (active == 0) {
            var pickOpportunity = false
            if (isForegroundRefresh == 1) {
                if (bannerConfig.isVisible) {
                    pickOpportunity = true
                } else {
                    if (bannerConfig.factor < 0) {
                        pickOpportunity = false
                    } else {
                        if (ceil((currentTimeStamp - bannerConfig.lastPassiveOpportunity).toDouble() / 1000.00).toInt() >= bannerConfig.factor * bannerConfig.passiveRefreshInterval) {
                            pickOpportunity = true
                        }
                    }
                }
            } else {
                if (bannerConfig.factor < 0) {
                    pickOpportunity = false
                } else {
                    if (ceil((currentTimeStamp - bannerConfig.lastPassiveOpportunity).toDouble() / 1000.00).toInt() >= bannerConfig.factor * bannerConfig.passiveRefreshInterval) {
                        pickOpportunity = true
                    }
                }
            }
            if (pickOpportunity) {
                bannerConfig.lastPassiveOpportunity = currentTimeStamp
                if (differenceOfLastRefresh >= bannerConfig.difference && (bannerConfig.isVisibleFor >= (if (wasFirstLook || bannerConfig.isNewUnit) bannerConfig.minView else bannerConfig.minViewRtb))) {
                    takeOpportunity = true
                }
            }
        }

        if (unfilled || (isForegroundRefresh == 1 && takeOpportunity && context.connectionAvailable() == true)) {
            refreshAd()
        } else {
            startRefreshing(timers = timers)
        }
    }

    private fun createRequest(active: Int, unfilled: Boolean = false, hijacked: Boolean = false) = AdRequest().Builder().apply {
        addCustomTargeting("adunit", bannerConfig.publisherAdUnit)
        addCustomTargeting("active", active.toString())
        addCustomTargeting("refresh", bannerConfig.refreshCount.toString())
        addCustomTargeting("hb_format", sdkConfig?.hbFormat ?: "amp")
        addCustomTargeting("visible", isForegroundRefresh.toString())
        addCustomTargeting("min_view", (if (bannerConfig.isVisibleFor > 10) 10 else bannerConfig.isVisibleFor).toString())
        if (unfilled) addCustomTargeting("retry", "1")
        if (hijacked) addCustomTargeting("hijack", "1")
    }.build()

    private fun loadAd(active: Int, unfilled: Boolean) {
        if (bannerConfig.refreshCount < 10) {
            bannerConfig.refreshCount++
        } else {
            bannerConfig.refreshCount = 10
        }
        bannerListener.loadAd(createRequest(active, unfilled))
    }

    fun checkOverride(): AdManagerAdRequest? {
        if (bannerConfig.isNewUnit && bannerConfig.newUnit?.status == 1) {
            bannerListener.attachAdView(getAdUnitName(unfilled = false, hijacked = false, newUnit = true), bannerConfig.adSizes.apply {
                if (ifNativePossible() && !this.contains(AdSize.FLUID)) {
                    add(AdSize.FLUID)
                }
            })
            view.log { "checkOverride: new Unit" }
            return createRequest(1).getAdRequest()
        } else if (checkHijack(bannerConfig.hijack)) {
            bannerListener.attachAdView(getAdUnitName(unfilled = false, hijacked = true, newUnit = false), bannerConfig.adSizes.apply {
                if (ifNativePossible() && !this.contains(AdSize.FLUID)) {
                    add(AdSize.FLUID)
                }
            })
            view.log { "checkOverride: hijack" }
            return createRequest(1, hijacked = true).getAdRequest()
        }
        return null
    }

    private fun checkHijack(hijackConfig: SDKConfig.LoadConfig?): Boolean {
        return if (hijackConfig?.status == 1) {
            val number = (1..100).random()
            number in 1..(hijackConfig.per ?: 100)
        } else {
            false
        }
    }

    fun checkGeoEdge(firstLook: Boolean, callback: () -> Unit) {
        val number = (1..100).random()
        if ((firstLook && (number in 1..(bannerConfig.geoEdge?.firstLook ?: 0))) ||
                (!firstLook && (number in 1..(bannerConfig.geoEdge?.other ?: 0)))) {
            callback()
        }
    }

    fun fetchDemand(firstLook: Boolean, adRequest: AdManagerAdRequest, callback: (AdManagerAdRequest) -> Unit) {
        val prebidAvailable = if ((firstLook && sdkConfig?.prebid?.firstLook == 1) || ((bannerConfig.isNewUnit || !firstLook) && sdkConfig?.prebid?.other == 1)) {
            bannerConfig.placement != null && bannerConfig.adSizes.isNotEmpty()
        } else {
            false
        }

        val apsAvailable = (firstLook && sdkConfig?.aps?.firstLook == 1) || ((bannerConfig.isNewUnit || !firstLook) && sdkConfig?.aps?.other == 1)


        fun prebid(apsRequestBuilder: AdManagerAdRequest.Builder? = null) = bannerConfig.placement?.let {
            val totalSizes = bannerConfig.adSizes
            val firstAdSize = totalSizes[0]
            val formatNeeded = bannerConfig.format
            val adUnit = if (formatNeeded.isNullOrEmpty() || (formatNeeded.contains("html", true) && !formatNeeded.contains("video", true))) {
                BannerAdUnit(if (firstLook) it.firstLook ?: "" else it.other ?: "", firstAdSize.width, firstAdSize.height)
            } else if (formatNeeded.contains("video", true) && !formatNeeded.contains("html", true)) {
                BannerAdUnit(if (firstLook) it.firstLook ?: "" else it.other ?: "", firstAdSize.width, firstAdSize.height, EnumSet.of(AdUnitFormat.VIDEO)).apply {
                    videoParameters = configureVideoParameters()
                }
            } else {
                BannerAdUnit(if (firstLook) it.firstLook ?: "" else it.other ?: "", firstAdSize.width, firstAdSize.height, EnumSet.of(AdUnitFormat.VIDEO, AdUnitFormat.BANNER)).apply {
                    videoParameters = configureVideoParameters()
                }
            }
            totalSizes.forEach { adSize -> adUnit.addAdditionalSize(adSize.width, adSize.height) }
            val finalRequest = apsRequestBuilder?.let { apsRequestBuilder ->
                adRequest.customTargeting.keySet().forEach { key ->
                    apsRequestBuilder.addCustomTargeting(key, adRequest.customTargeting.getString(key, ""))
                }
                apsRequestBuilder.build()
            } ?: adRequest

            adUnit.fetchDemand(finalRequest) {
                view.log { "Demand fetched aps : ${apsRequestBuilder != null} && prebid : completed" }
                callback(finalRequest)
            }
        }

        fun aps(wait: Boolean) {
            var actionTaken = false
            val matchingSlots = arrayListOf<DTBAdSize>()
            bannerConfig.adSizes.forEach { size ->
                sdkConfig?.aps?.slots?.filter { slot -> slot.height?.toIntOrNull() == size.height && slot.width?.toIntOrNull() == size.width }?.forEach {
                    matchingSlots.add(DTBAdSize(it.width?.toIntOrNull() ?: 0, it.height?.toIntOrNull() ?: 0, it.slotId ?: ""))
                }
            }
            val loader = DTBAdRequest()
            val apsCallback = object : DTBAdCallback {
                override fun onFailure(adError: AdError) {
                    if (actionTaken) return
                    actionTaken = true
                    if (wait) {
                        prebid(null)
                    } else {
                        callback(adRequest)
                    }
                }

                override fun onSuccess(dtbAdResponse: DTBAdResponse) {
                    if (actionTaken) return
                    actionTaken = true
                    if (wait) {
                        prebid(DTBAdUtil.INSTANCE.createAdManagerAdRequestBuilder(dtbAdResponse))
                    } else {
                        val apsRequest = DTBAdUtil.INSTANCE.createAdManagerAdRequestBuilder(dtbAdResponse)
                        adRequest.customTargeting.keySet().forEach { key ->
                            apsRequest.addCustomTargeting(key, adRequest.customTargeting.getString(key, ""))
                        }
                        callback(apsRequest.build())
                    }
                }
            }

            if (matchingSlots.isEmpty()) {
                apsCallback.onFailure(AdError(AdError.ErrorCode.NO_FILL, "error"))
                return
            }
            loader.setSizes(*matchingSlots.toTypedArray())
            loader.loadAd(apsCallback)
            sdkConfig?.aps?.timeout?.let {
                Handler(Looper.getMainLooper()).postDelayed({
                    apsCallback.onFailure(AdError(AdError.ErrorCode.NO_FILL, "error"))
                }, it.toLongOrNull() ?: 1000)
            }
        }

        view.log { "Fetch Demand with aps : $apsAvailable and with prebid : $prebidAvailable" }
        if (apsAvailable && prebidAvailable) {
            aps(true)
        } else if (apsAvailable) {
            aps(false)
        } else if (prebidAvailable) {
            prebid()
        } else {
            callback(adRequest)
        }
    }

    private fun configureVideoParameters(): VideoParameters {
        return VideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            api = listOf(Signals.Api.VPAID_1, Signals.Api.VPAID_2)
            maxBitrate = 1500
            minBitrate = 300
            maxDuration = 30
            minDuration = 5
            playbackMethod = listOf(Signals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(Signals.Protocols.VAST_2_0)
        }
    }


    private fun getAdUnitName(unfilled: Boolean, hijacked: Boolean, newUnit: Boolean = false): String {
        return overridingUnit ?: String.format("%s-%d", bannerConfig.customUnitName,
                if (unfilled) bannerConfig.unFilled?.number else if (newUnit) bannerConfig.newUnit?.number else if (hijacked) bannerConfig.hijack?.number else bannerConfig.position)
    }

    fun adPaused() {
        isForegroundRefresh = 0
        activeTimeCounter?.cancel()
    }

    fun adResumed() {
        isForegroundRefresh = 1
        if (bannerConfig.adSizes.isNotEmpty()) {
            startActiveCounter(bannerConfig.activeRefreshInterval.toLong())
        }
    }

    fun adDestroyed() {
        activeTimeCounter?.cancel()
        passiveTimeCounter?.cancel()
        unfilledRefreshCounter?.cancel()
    }

    fun allowCallback(refreshLoad: Boolean): Boolean {
        return !refreshLoad || sdkConfig?.infoConfig?.refreshCallbacks == 1
    }

    fun checkFallback(refreshLoad: Boolean): Boolean {
        if ((!refreshLoad && bannerConfig.fallback?.firstlook == 1) || (refreshLoad && bannerConfig.fallback?.other == 1)) {
            val matchedBanners = arrayListOf<Fallback.Banner>()
            pubAdSizes.forEach { pubSize ->
                bannerConfig.fallback?.banners?.firstOrNull { (it.width?.toIntOrNull() ?: 0) == pubSize.width && (it.height?.toIntOrNull() ?: 0) == pubSize.height }?.let { matchedSize ->
                    matchedBanners.add(matchedSize)
                }
            }
            var biggestBanner: Fallback.Banner? = null
            var maxArea = 0
            matchedBanners.forEach {
                if (maxArea < ((it.width?.toIntOrNull() ?: 0) * (it.height?.toIntOrNull() ?: 0))) {
                    biggestBanner = it
                    maxArea = (it.width?.toIntOrNull() ?: 0) * (it.height?.toIntOrNull() ?: 0)
                }
            }

            if (biggestBanner == null) {
                var biggestPubSize: AdSize? = null
                maxArea = 0
                pubAdSizes.forEach {
                    if (maxArea < (it.width * it.height)) {
                        biggestPubSize = it
                        maxArea = (it.width * it.height)
                    }
                }
                biggestBanner = bannerConfig.fallback?.banners?.firstOrNull { it.height == "all" && it.width == "all" }?.apply {
                    height = biggestPubSize?.height.toString()
                    width = biggestPubSize?.width.toString()
                }
            }

            biggestBanner?.let { bannerListener.attachFallback(it) }
            return biggestBanner != null && ((biggestBanner?.height?.toIntOrNull() ?: 0) != 0 && (biggestBanner?.width?.toIntOrNull() ?: 0) != 0)
        } else {
            return false
        }
    }

    private fun ifNativePossible(): Boolean {
        return if (bannerConfig.nativeFallback != 1) {
            false
        } else {
            var maxArea: Int
            var biggestPubSize: AdSize? = null
            maxArea = 0
            pubAdSizes.forEach {
                if (maxArea < (it.width * it.height)) {
                    biggestPubSize = it
                    maxArea = (it.width * it.height)
                }
            }
            (biggestPubSize != null && biggestPubSize!!.height > 120 && biggestPubSize!!.width > 120)
        }
    }

    private fun ifUnitOnHold(adUnit: String): Boolean {
        val hold = sdkConfig?.heldUnits?.any { adUnit.contains(it, false) } == true || sdkConfig?.heldUnits?.any { it.contains("all", true) } == true
        if (hold) {
            view.log { "Blocking refresh on : $adUnit" }
        }
        return hold
    }
}