package com.rtb.andbeyondmedia.unified

import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.sdk.ABMError

abstract class UnifiedAdListener {

    open fun onAdClicked() {
    }

    open fun onAdClosed() {
    }

    open fun onAdFailedToLoad(adError: ABMError) {
    }

    open fun onAdImpression() {
    }

    open fun onNativeLoaded(nativeAd: NativeAd) {
    }

    open fun onBannerLoaded(bannerAd: BannerAdView) {
    }

    open fun onCustomAdLoaded(id: String, customAd: NativeCustomFormatAd) {
    }

    open fun onCustomAdClicked(id: String, customAd: NativeCustomFormatAd, assetName: String) {
    }

    open fun onAdOpened() {
    }

    open fun onAdSwipeGestureClicked() {
    }
}