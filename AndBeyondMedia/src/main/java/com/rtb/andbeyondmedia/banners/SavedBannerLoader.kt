package com.rtb.andbeyondmedia.banners

import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.rtb.andbeyondmedia.sdk.SafeImpressionConfig
import java.util.Date

internal object SavedBannerLoader {
    private var config: SafeImpressionConfig? = null
    private val banners = arrayListOf<SavedBanner>()

    fun setConfig(config: SafeImpressionConfig?) {
        this.config = config
    }

    fun saveBanner(adView: AdManagerAdView, sizes: List<AdSize>): Int {
        if (config?.active != 1) return 0
        val uniqueId = (1..Int.MAX_VALUE).random()
        banners.add(SavedBanner(uniqueId, adView, Date().time, sizes))
        return uniqueId
    }

    fun getOwnAd(id: Int): AdManagerAdView? {
        return banners.firstOrNull { it.id == id }?.adView
    }

    fun removeAd(id: Int) {
        banners.removeAll { it.id == id }
    }

    fun getLoadedBanner(requiredAdSize: List<AdSize>): AdManagerAdView? {
        val suitableBanner = banners.firstOrNull {
            (Date().time - it.addedTime) / 1000 > (config?.dnt ?: 0)
                    && (Date().time - it.addedTime) / 1000 < (config?.ttl ?: 0)
                    && it.supportedSizes.any { size -> requiredAdSize.contains(size) }
                    && it.adView.responseInfo != null
        }
        suitableBanner?.let { removeAd(it.id) }
        return suitableBanner?.adView
    }
}