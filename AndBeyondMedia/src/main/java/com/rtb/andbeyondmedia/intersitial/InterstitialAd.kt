package com.rtb.andbeyondmedia.intersitial

import android.app.Activity
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.sdk.log

class InterstitialAd(private val context: Activity, private val adUnit: String) {
    private var interstitialAdManager = InterstitialAdManager(context, adUnit)
    private var mAdManagerInterstitialAd: AdManagerInterstitialAd? = null

    fun load(adRequest: AdRequest, callBack: (loaded: Boolean) -> Unit) {
        interstitialAdManager.load(adRequest) {
            mAdManagerInterstitialAd = it
            callBack(mAdManagerInterstitialAd != null)
        }
    }

    fun show() {
        if (mAdManagerInterstitialAd != null) {
            mAdManagerInterstitialAd?.show(context)
        } else {
            LogLevel.ERROR.log("The interstitial ad wasn't ready yet.")
        }
    }
}