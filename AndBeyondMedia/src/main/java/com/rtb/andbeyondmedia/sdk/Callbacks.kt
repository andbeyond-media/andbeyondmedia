package com.rtb.andbeyondmedia.sdk

import com.google.android.gms.ads.AdSize
import com.rtb.andbeyondmedia.common.AdRequest

internal interface BannerManagerListener {
    fun attachAdView(adUnitId: String, adSizes: List<AdSize>)

    fun attachFallback(fallbackBanner: Fallback.Banner)

    fun loadAd(request: AdRequest): Boolean
}

interface FullScreenContentCallback {
    fun onAdClicked()
    fun onAdDismissedFullScreenContent()
    fun onAdFailedToShowFullScreenContent(error: String)
    fun onAdImpression()
    fun onAdShowedFullScreenContent()
}

interface BannerAdListener {
    fun onAdClicked()
    fun onAdClosed()
    fun onAdFailedToLoad(error: String, retrying: Boolean)
    fun onAdImpression()
    fun onAdLoaded()
    fun onAdOpened()
}

fun interface OnShowAdCompleteListener {
    fun onShowAdComplete()
}

interface AdLoadCallback {
    fun onAdLoaded()
    fun onAdFailedToLoad(error: String)
}