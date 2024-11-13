package com.rtb.andbeyondtest

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import com.rtb.andbeyondmedia.banners.BannerAdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.intersitial.InterstitialAd
import com.rtb.andbeyondmedia.nativeformat.NativeAdManager
import com.rtb.andbeyondmedia.rewarded.RewardedAd
import com.rtb.andbeyondmedia.rewardedinterstitial.RewardedInterstitialAd
import com.rtb.andbeyondmedia.sdk.ABMError
import com.rtb.andbeyondmedia.sdk.BannerAdListener
import com.rtb.andbeyondmedia.unified.UnifiedAdListener
import com.rtb.andbeyondmedia.unified.UnifiedAdManager
import com.rtb.andbeyondtest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), BannerAdListener {
    private lateinit var binding: ActivityMainBinding
    private var interstitialAd: InterstitialAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var adaptiveAd: BannerAdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        init()
        loadAd()
        //loadInterstitial()
        //loadInterstitialRewarded()
        //loadRewarded()
        //loadAdaptiveAd()
        //loadNative()
        //loadUnifiedAd()
    }

    private fun init() {
        binding.showInterstitial.setOnClickListener { interstitialAd?.show() }
        binding.showInterstitialRewarded.setOnClickListener {
            rewardedInterstitialAd?.show {

            }
        }

        binding.showRewarded.setOnClickListener {
            rewardedAd?.show {

            }
        }
    }

    private fun loadUnifiedAd() {
        val adLoader = UnifiedAdManager(this, "/6499/example/banner")
        adLoader.setAdSizes(BannerAdSize.BANNER, BannerAdSize.MEDIUM_RECTANGLE)
        adLoader.setCustomFormatIds(arrayListOf("10063170"))
        adLoader.setLoadCount(1)
        adLoader.setAdListener(object : UnifiedAdListener() {

            override fun onBannerLoaded(bannerAd: BannerAdView) {
                binding.root.addView(bannerAd)
            }

            override fun onNativeLoaded(nativeAd: NativeAd) {
                super.onNativeLoaded(nativeAd)
                showNative(nativeAd)
            }

            override fun onCustomAdLoaded(id: String, customAd: NativeCustomFormatAd) {
                //show custom ad
            }

            override fun onAdFailedToLoad(adError: ABMError) {
            }

        })
        adLoader.load(AdRequest().Builder().build())
    }

    private fun loadAd() {
        val adRequest = AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()
        binding.bannerAd.loadAd(adRequest)
        binding.bannerAd.setAdListener(this)
    }

    private fun loadNative() {
        val nativeAdManger = NativeAdManager(this, "/6499/example/native")
        nativeAdManger.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                // Native Ad loaded
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {
                // Native Ad failed to load
            }
        })
        nativeAdManger.load(AdRequest().Builder().build()) {
            it?.let {
                showNative(it)
            }
        }
    }

    private fun showNative(nativeAd: NativeAd) = nativeAd.let {
        binding.title.text = it.headline
        binding.nativeAd.headlineView = binding.title
        binding.description.text = it.body
        binding.nativeAd.bodyView = binding.description
        binding.icon.setImageURI(it.icon?.uri)
        binding.icon.setImageDrawable(it.icon?.drawable)
        binding.mediaView.mediaContent = it.mediaContent
        binding.mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        binding.nativeAd.iconView = binding.icon
        binding.nativeAd.mediaView = binding.mediaView
        binding.nativeAd.setNativeAd(it)
    }


    private fun loadInterstitial() {
        interstitialAd = InterstitialAd(this, "/6499/example/interstitial")
        interstitialAd?.load(AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()) {
            binding.showInterstitial.isEnabled = it
        }
    }

    private fun loadInterstitialRewarded() {
        rewardedInterstitialAd = RewardedInterstitialAd(this, "/21775744923/example/rewarded_interstitial")
        rewardedInterstitialAd?.load(AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()) {
            binding.showInterstitialRewarded.isEnabled = it
        }
    }

    private fun loadRewarded() {
        rewardedAd = RewardedAd(this, "/6499/example/rewarded")
        rewardedAd?.load(AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()) {
            binding.showRewarded.isEnabled = it
        }
    }


    private fun loadAdaptiveAd() {
        adaptiveAd = BannerAdView(this)
        binding.root.addView(adaptiveAd!!)
        fun loadBanner() {
            adaptiveAd?.setAdUnitID("/6499/example/banner")
            adaptiveAd?.setAdType(AdTypes.ADAPTIVE)
            adaptiveAd?.setAdSize(adSize)
            adaptiveAd?.setAdListener(this)

            // Create an ad request. Check your logcat output for the hashed device ID to
            // get test ads on a physical device, e.g.,
            // "Use AdRequest.Builder.addTestDevice("ABCDE0123") to get test ads on this device."
            val adRequest = AdRequest().Builder().build()

            // Start loading the ad in the background.
            adaptiveAd?.loadAd(adRequest)
        }

        var initialLayoutComplete = false
        // Since we're loading the banner based on the adContainerView size, we need
        // to wait until this view is laid out before we can get the width.
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            if (!initialLayoutComplete) {
                initialLayoutComplete = true
                loadBanner()
            }
        }
    }

    private val adSize: BannerAdSize
        get() {
            val display = windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)

            val density = outMetrics.density

            var adWidthPixels = binding.root.width.toFloat()
            if (adWidthPixels == 0f) {
                adWidthPixels = outMetrics.widthPixels.toFloat()
            }

            val adWidth = (adWidthPixels / density).toInt()
            return BannerAdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }


    override fun onResume() {
        super.onResume()
        binding.bannerAd.resumeAd()
        adaptiveAd?.resumeAd()
    }

    override fun onPause() {
        super.onPause()
        binding.bannerAd.pauseAd()
        adaptiveAd?.pauseAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bannerAd.destroyAd()
        adaptiveAd?.destroyAd()
    }

    override fun onAdClicked(bannerAdView: BannerAdView) {
    }

    override fun onAdClosed(bannerAdView: BannerAdView) {
    }

    override fun onAdFailedToLoad(bannerAdView: BannerAdView, error: ABMError, retrying: Boolean) {
        Log.d("Ads", "onAdFailedToLoad & Retrying $retrying")
    }

    override fun onAdImpression(bannerAdView: BannerAdView) {
        Log.d("Ads", "onAdImpression: ")
    }

    override fun onAdLoaded(bannerAdView: BannerAdView) {
        Log.d("Ads", "onAdLoaded: ")
    }

    override fun onAdOpened(bannerAdView: BannerAdView) {
    }
}