package com.rtb.andbeyondmedia.rewarded

import android.app.Activity
import com.google.android.gms.ads.rewarded.RewardedAd
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.rewardedinterstitial.Reward
import com.rtb.andbeyondmedia.sdk.log

class RewardedAd(private val context: Activity, private val adUnit: String) {
    private var rewardedAdManager = RewardedAdManager(context, adUnit)
    private var mAdManagerInterstitialAd: RewardedAd? = null

    fun load(adRequest: AdRequest, callBack: (loaded: Boolean) -> Unit) {
        rewardedAdManager.load(adRequest) {
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