package com.rtb.andbeyondmedia.banners

import androidx.annotation.Keep
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView

@Keep
internal data class SavedBanner(
        val id: Int = 0,
        val adView: AdManagerAdView,
        val addedTime: Long,
        val supportedSizes: List<AdSize>
)
