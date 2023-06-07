package com.rtb.andbeyondmedia.adapter

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.mediation.*
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.sdk.log


class AndBeyondAdapter : Adapter() {

    private lateinit var bannerLoader: AndBeyondBannerLoader
    private lateinit var interstitialLoader: AndBeyondInterstitialLoader
    private lateinit var rewardedLoaded: RewardedLoader
    private lateinit var rewardedInterstitialLoader: RewardedInterstitialLoader
    private lateinit var appOpenAdLoader: AppOpenAdLoader
    private val TAG: String = this::class.java.simpleName

    companion object {

        @SuppressLint("VisibleForTests")
        fun createAdRequest(mediationAdConfiguration: MediationAdConfiguration): AdManagerAdRequest {
            return AdManagerAdRequest.Builder().addCustomTargeting("hb_format", "amp").build()
        }
    }

    override fun initialize(context: Context, initializationCompleteCallback: InitializationCompleteCallback, list: List<MediationConfiguration>) {
        LogLevel.INFO.log(TAG, "initialize: AndBeyondAdapter")
        return
    }

    override fun getVersionInfo(): VersionInfo {
        val versionString = BuildConfig.ADAPTER_VERSION
        val splits = versionString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (splits.size >= 3) {
            val major = splits[0].toInt()
            val minor = splits[1].toInt()
            val micro = splits[2].toInt()
            return VersionInfo(major, minor, micro)
        }
        return VersionInfo(0, 0, 0)
    }

    override fun getSDKVersionInfo(): VersionInfo {
        val versionString = MobileAds.getVersion()
        return VersionInfo(versionString.majorVersion, versionString.minorVersion, versionString.microVersion)
    }

    override fun loadBannerAd(mediationBannerAdConfiguration: MediationBannerAdConfiguration, mediationAdLoadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>) {
        LogLevel.INFO.log(TAG, "loadBannerAd")
        bannerLoader = AndBeyondBannerLoader(mediationBannerAdConfiguration, mediationAdLoadCallback)
        bannerLoader.loadAd()
    }

    override fun loadInterstitialAd(mediationInterstitialAdConfiguration: MediationInterstitialAdConfiguration, callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>) {
        LogLevel.INFO.log(TAG, "loadInterstitialAd:")
        interstitialLoader = AndBeyondInterstitialLoader(mediationInterstitialAdConfiguration, callback)
        interstitialLoader.loadAd()
    }

    override fun loadAppOpenAd(mediationAppOpenAdConfiguration: MediationAppOpenAdConfiguration, callback: MediationAdLoadCallback<MediationAppOpenAd, MediationAppOpenAdCallback>) {
        LogLevel.INFO.log(TAG, "loadAppOpenAd:")
        appOpenAdLoader = AppOpenAdLoader(mediationAppOpenAdConfiguration, callback)
        appOpenAdLoader.loadAd()
    }

    override fun loadRewardedAd(mediationRewardedAdConfiguration: MediationRewardedAdConfiguration, callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>) {
        LogLevel.INFO.log(TAG, "loadRewardedAd:")
        rewardedLoaded = RewardedLoader(mediationRewardedAdConfiguration, callback)
        rewardedLoaded.loadAd()
    }

    override fun loadRewardedInterstitialAd(mediationRewardedAdConfiguration: MediationRewardedAdConfiguration, callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>) {
        LogLevel.INFO.log(TAG, "loadRewardedInterstitialAd:")
        rewardedInterstitialLoader = RewardedInterstitialLoader(mediationRewardedAdConfiguration, callback)
        rewardedInterstitialLoader.loadAd()
    }
}