package com.rtb.andbeyondmedia.rewardedinterstitial

import android.app.Activity
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.sdk.log

class RewardedInterstitialAd(private val context: Activity, private val adUnit: String) {
    private var rewardedInterstitialAdManager = RewardedInterstitialAdManager(context, adUnit)
    private var mAdManagerInterstitialAd: RewardedInterstitialAd? = null

    fun load(adRequest: AdRequest, callBack: (loaded: Boolean) -> Unit) {
        rewardedInterstitialAdManager.load(adRequest) {
            mAdManagerInterstitialAd = it
            callBack(mAdManagerInterstitialAd != null)
        }
    }

    fun show(callBack: (reward: Reward?) -> Unit) {
        if (mAdManagerInterstitialAd != null) {
            mAdManagerInterstitialAd?.show(context) { callBack(Reward(it.amount, it.type)) }
        } else {
            LogLevel.ERROR.log("The rewarded interstitial ad wasn't ready yet.")
            callBack(null)
        }
    }
}