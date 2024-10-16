package com.rtb.andbeyondmedia.unified

import com.google.android.gms.ads.nativead.NativeAd
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

    open fun onAdOpened() {
    }

    open fun onAdSwipeGestureClicked() {
    }
}