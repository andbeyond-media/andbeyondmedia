package com.rtb.andbeyondtest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.sdk.ABMError
import com.rtb.andbeyondmedia.sdk.BannerAdListener
import com.rtb.andbeyondtest.databinding.ActivityScrollingBinding

class ScrollingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScrollingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScrollingBinding.inflate(layoutInflater).also { setContentView(it.root) }
        loadAd()
        loadScrollingAd()
    }


    private fun loadAd() {
        val adRequest = AdRequest().Builder().addCustomTargeting("hb_format", "amp").build()
        binding.bannerAd.loadAd(adRequest)
        binding.bannerAd.setAdListener(object : BannerAdListener {
            override fun onAdClicked(bannerAdView: BannerAdView) {
            }

            override fun onAdClosed(bannerAdView: BannerAdView) {
            }

            override fun onAdFailedToLoad(bannerAdView: BannerAdView, error: ABMError, retrying: Boolean) {
            }

            override fun onAdImpression(bannerAdView: BannerAdView) {
            }

            override fun onAdLoaded(bannerAdView: BannerAdView) {
                Log.d("Ads", "fixed ad loaded: ")
            }

            override fun onAdOpened(bannerAdView: BannerAdView) {
            }

        })
    }

    private fun loadScrollingAd() {
        binding.boxes.adapter = BoxAdapter(this, listOf())
    }
}