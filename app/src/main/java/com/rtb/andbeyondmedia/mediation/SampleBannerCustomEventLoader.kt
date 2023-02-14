package com.rtb.andbeyondmedia.mediation

import android.view.View
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.mediation.*


class SampleBannerCustomEventLoader(private var mediationBannerAdConfiguration: MediationBannerAdConfiguration,
                                    private var mediationAdLoadCallback: MediationAdLoadCallback<MediationBannerAd?, MediationBannerAdCallback?>) : MediationBannerAd {

    private val sampleAdView: AdManagerAdView? = null

    /** Callback for banner ad events.  */
    private val bannerAdCallback: MediationBannerAdCallback? = null


    override fun getView(): View {
        TODO("Not yet implemented")
    }

    fun createSampleRequest(
            mediationAdConfiguration: MediationAdConfiguration): SampleAdRequest? {
        val request = AdRequest.Builder()
        request.setTestMode(mediationAdConfiguration.isTestRequest)
        request.setKeywords(mediationAdConfiguration.mediationExtras.keySet())
        return request
    }
}