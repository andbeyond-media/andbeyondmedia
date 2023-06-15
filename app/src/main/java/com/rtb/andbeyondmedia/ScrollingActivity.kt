package com.rtb.andbeyondmedia

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.databinding.ActivityScrollingBinding
import com.rtb.andbeyondmedia.sdk.BannerAdListener

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
            override fun onAdClicked() {

            }

            override fun onAdClosed() {
            }

            override fun onAdFailedToLoad(error: String, retrying: Boolean) {
            }

              override fun onAdImpression() {
              }

            override fun onAdLoaded() {
                Log.d("Ads", "fixed ad loaded: ")
            }

            override fun onAdOpened() {

            }

        })
    }

    private fun loadScrollingAd() {
        binding.boxes.adapter = BoxAdapter(this, listOf())
    }
}