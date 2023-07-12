package com.rtb.andbeyondmedia

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.databinding.ActivityGamaactivityBinding
import com.rtb.andbeyondmedia.native.NativeAdManager

class GAMActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamaactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamaactivityBinding.inflate(layoutInflater).also { setContentView(it.root) }
        init()
        loadAd()
    }

    private fun init() {
        //  MobileAds.initialize(this) {}
        NativeAdManager(this, "/21952429235/985111-NATIVE-1").testLoad(AdRequest().Builder().build()) {
            Log.d("Sonu", "init: ${it.getHeadline()}")
        }
        //binding.mediaView.setMediaContent(NativeMediaContent())
    }

    private fun loadAd() {
        /*val adRequest = AdManagerAdRequest.Builder().build()
        binding.adView.loadAd(adRequest)*/
    }
}