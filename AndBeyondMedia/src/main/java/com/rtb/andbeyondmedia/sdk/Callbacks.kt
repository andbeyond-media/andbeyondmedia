package com.rtb.andbeyondmedia.sdk

import com.google.android.gms.ads.AdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.intersitial.InterstitialAd

internal interface BannerManagerListener {
    fun attachAdView(adUnitId: String, adSizes: List<AdSize>)

    fun attachFallback(fallbackBanner: Fallback.Banner)

    fun loadAd(request: AdRequest): Boolean
}

interface FullScreenContentCallback {
    fun onAdClicked()
    fun onAdDismissedFullScreenContent()
    fun onAdFailedToShowFullScreenContent(error: ABMError)
    fun onAdImpression()
    fun onAdShowedFullScreenContent()
}

interface BannerAdListener {
    fun onAdClicked(bannerAdView: BannerAdView)
    fun onAdClosed(bannerAdView: BannerAdView)
    fun onAdFailedToLoad(bannerAdView: BannerAdView, error: ABMError, retrying: Boolean)
    fun onAdImpression(bannerAdView: BannerAdView)
    fun onAdLoaded(bannerAdView: BannerAdView)
    fun onAdOpened(bannerAdView: BannerAdView)
}

fun interface OnShowAdCompleteListener {
    fun onShowAdComplete()
}

interface AdLoadCallback {
    fun onAdLoaded()
    fun onAdFailedToLoad(error: ABMError)
}

interface InterstitialAdListener {

    fun onAdReceived(var1: InterstitialAd)

    fun onAdFailedToLoad(var1: InterstitialAd, var2: ABMError)

    fun onAdFailedToShow(var1: InterstitialAd, var2: ABMError)

    fun onAppLeaving(var1: InterstitialAd)

    fun onAdOpened(var1: InterstitialAd)

    fun onAdClosed(var1: InterstitialAd)

    fun onAdClicked(var1: InterstitialAd)

    fun onAdExpired(var1: InterstitialAd)
}

