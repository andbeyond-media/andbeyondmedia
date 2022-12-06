package com.rtb.andbeyondmedia

import android.os.Bundle
import android.util.DisplayMetrics
import androidx.appcompat.app.AppCompatActivity
import com.rtb.andbeyondmedia.banners.AdTypes
import com.rtb.andbeyondmedia.banners.BannerAdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        loadAd()
        loadAdaptiveAd()
    }

    private fun loadAd() {
        val adRequest = AdRequest().Builder().build()
        binding.bannerAd.loadAd(adRequest)
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
}