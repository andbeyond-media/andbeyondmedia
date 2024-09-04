package com.rtb.andbeyondmedia.banners

import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.rtb.andbeyondmedia.sdk.SafeImpressionConfig
import java.util.Date

internal object SavedBannerLoader {
    private var config: SafeImpressionConfig? = null
    private val banners = arrayListOf<SavedBanner>()
    internal var isActive = false

    fun setConfig(config: SafeImpressionConfig?) {
        this.config = config
        val number = (1..100).random()
        isActive = number in 1..(config?.active ?: 0)
    }

    fun saveBanner(adView: AdManagerAdView, sizes: List<AdSize>): Int {
        if (config?.active != 1) return 0
        val uniqueId = (1..Int.MAX_VALUE).random()
        banners.add(SavedBanner(uniqueId, adView, Date().time, sizes))
        return uniqueId
    }

    fun getOwnAd(id: Int): Pair<AdManagerAdView?, Long> {
        val ownAd = banners.firstOrNull { it.id == id }
        val timeStored = (Date().time - (ownAd?.addedTime ?: 0)) / 1000
        return Pair(ownAd?.adView, timeStored)
    }

    fun removeAd(id: Int) {
        banners.removeAll { it.id == id }
    }

    fun getLoadedBanner(requiredAdSize: List<AdSize>): Pair<AdManagerAdView?, Long> {
        val suitableBanner = banners.firstOrNull {
            (Date().time - it.addedTime) / 1000 > (config?.dnt ?: 0)
                    && (Date().time - it.addedTime) / 1000 < (config?.ttl ?: 0)
                    && it.supportedSizes.any { size -> requiredAdSize.contains(size) }
                    && it.adView.responseInfo != null
        }
        val timeStored = (Date().time - (suitableBanner?.addedTime ?: 0)) / 1000
        suitableBanner?.let { removeAd(it.id) }
        return Pair(suitableBanner?.adView, timeStored)
    }
}