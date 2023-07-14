package com.rtb.andbeyondmedia

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.rtb.andbeyondmedia.databinding.ActivityGamaactivityBinding

class GAMActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamaactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamaactivityBinding.inflate(layoutInflater).also { setContentView(it.root) }
        init()
        loadAd()
    }

    private fun init() {
        MobileAds.initialize(this) {}
    }

    private fun loadAd() {
        val adRequest = AdManagerAdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
    }
}