package com.rtb.andbeyondmedia.banners

import com.google.android.gms.ads.AdSize
import com.rtb.andbeyondmedia.common.AdRequest

internal interface AdManagerListener {
    fun attachAdView(adUnitId: String, adSizes: List<AdSize>)

    fun loadAd(request: AdRequest): Boolean
}

interface BannerAdListener {
    fun onAdClicked()

    fun onAdClosed()

    fun onAdFailedToLoad(error: String)

    fun onAdImpression()

    fun onAdLoaded()

    fun onAdOpened()
}