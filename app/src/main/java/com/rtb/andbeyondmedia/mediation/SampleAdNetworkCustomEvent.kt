package com.rtb.andbeyondmedia.mediation

import android.content.Context
import com.google.android.gms.ads.mediation.*

class SampleAdNetworkCustomEvent : Adapter() {

    private var bannerLoader: SampleBannerCustomEventLoader? = null

    override fun getSDKVersionInfo(): VersionInfo {
        /*   val versionString: String = SampleAdRequest.getSDKVersion()
           val splits = versionString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

           if (splits.size >= 3) {
               val major = splits[0].toInt()
               val minor = splits[1].toInt()
               val micro = splits[2].toInt()
               return VersionInfo(major, minor, micro)
           }*/

        return VersionInfo(0, 0, 0)
    }

    override fun getVersionInfo(): VersionInfo {
        /*  val versionString: String = VersionInfo(1, 2, 3)
          val splits = versionString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

          if (splits.size >= 4) {
              val major = splits[0].toInt()
              val minor = splits[1].toInt()
              val micro = splits[2].toInt() * 100 + splits[3].toInt()
              return VersionInfo(major, minor, micro)
          }*/

        return VersionInfo(0, 0, 0)
    }

    override fun initialize(p0: Context, initializationCompleteCallback: InitializationCompleteCallback, p2: MutableList<MediationConfiguration>) {
        // This is where you will initialize the SDK that this custom
        // event is built for. Upon finishing the SDK initialization,
        // call the completion handler with success.
        initializationCompleteCallback.onInitializationSucceeded()
    }

    override fun loadBannerAd(adConfiguration: MediationBannerAdConfiguration, callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>) {
        bannerLoader = SampleBannerCustomEventLoader(adConfiguration, callback)
        bannerLoader.loadAd()
    }
}