package com.rtb.andbeyondmedia

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rtb.andbeyondmedia.banners.BannerAdListener
import com.rtb.andbeyondmedia.banners.BannerAdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.common.AdTypes
import com.rtb.andbeyondmedia.databinding.ActivityMainBinding
import com.rtb.andbeyondmedia.intersitial.InterstitialAd
import com.rtb.andbeyondmedia.rewardedinterstitial.RewardedInterstitialAd

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var interstitialAd: InterstitialAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        loadAd()
        /*loadInterstitial()
        loadAdaptiveAd()*/
        binding.showInterstitial.setOnClickListener { interstitialAd?.show() }
    }

    private fun loadAd() {
        val adRequest = AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()
        binding.bannerAd.loadAd(adRequest)
        binding.bannerAd.setAdListener(object : BannerAdListener {
            override fun onAdClicked() {

            }

            override fun onAdClosed() {
            }

            override fun onAdFailedToLoad(error: String) {
            }

            override fun onAdImpression() {
            }

            override fun onAdLoaded() {
                Log.d("Sonu", "onAdLoaded: ")
            }

            override fun onAdOpened() {
            }

        })
    }


    private fun loadInterstitial() {
        interstitialAd = InterstitialAd(this, "/6499/example/interstitial")
        interstitialAd?.load(AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()) {
            binding.showInterstitial.isEnabled = it
        }
    }

    private fun loadInterstitialRewarded() {
        rewardedInterstitialAd = RewardedInterstitialAd(this, "/21775744923/example/rewarded_interstitial")
        rewardedInterstitialAd?.load(AdRequest().Builder().build()) {}
    }


    private fun loadAdaptiveAd() {
        val adView = BannerAdView(this)
        binding.root.addView(adView)
        fun loadBanner() {
            adView.setAdUnitID("/6499/example/banner")
            adView.setAdType(AdTypes.ADAPTIVE)
            adView.setAdSize(adSize)

            // Create an ad request. Check your logcat output for the hashed device ID to
            // get test ads on a physical device, e.g.,
            // "Use AdRequest.Builder.addTestDevice("ABCDE0123") to get test ads on this device."
            val adRequest = AdRequest().Builder().build()

            // Start loading the ad in the background.
            adView.loadAd(adRequest)
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
    }

    override fun onPause() {
        super.onPause()
        binding.bannerAd.pauseAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bannerAd.destroyAd()
    }
}