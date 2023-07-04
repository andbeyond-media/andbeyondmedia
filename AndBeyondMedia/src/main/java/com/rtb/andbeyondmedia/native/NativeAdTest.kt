package com.rtb.andbeyondmedia.native

import com.google.android.gms.ads.nativead.NativeAd

class NativeAdTest(private val ad: NativeAd) {
    fun getExtras() = ad.extras

    fun getHeadline() = ad.headline
}