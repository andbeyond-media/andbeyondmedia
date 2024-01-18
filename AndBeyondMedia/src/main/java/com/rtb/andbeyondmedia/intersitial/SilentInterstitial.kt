package com.rtb.andbeyondmedia.intersitial

import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.R
import com.rtb.andbeyondmedia.banners.BannerAdSize
import com.rtb.andbeyondmedia.banners.BannerAdView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.sdk.ABMError
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.BannerAdListener
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log

internal class SilentInterstitial {

    private var activities: ArrayList<Activity> = arrayListOf()
    private var sdkConfig: SDKConfig? = null
    private var interstitialConfig: SilentInterstitialConfig = SilentInterstitialConfig()
    private var activeTimeCounter: CountDownTimer? = null
    private var closeDelayTimer: CountDownTimer? = null
    private var started: Boolean = false
    private var banner: BannerAdView? = null
    private var timerSeconds = 0
    private var dialog: AppCompatDialog? = null
    private val tag: String
        get() = this.javaClass.simpleName

    fun registerActivity(activity: Activity) {
        if (activities.none { it.localClassName == activity.localClassName }) {
            tag.log { activity.localClassName }
            activities.add(activity)
        }
    }

    fun init(context: Context) {
        if (started) return
        tag.log { String.format("%s:%s- Version:%s", "setConfig", "entry", BuildConfig.ADAPTER_VERSION) }
        sdkConfig = AndBeyondMedia.getStoreService(context).config
        val shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
        if (!shouldBeActive) return
        interstitialConfig = sdkConfig?.silentInterstitialConfig ?: SilentInterstitialConfig()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifeCycleHandler())
        started = true
        timerSeconds = interstitialConfig.timer ?: 0
        tag.log { "setConfig :$interstitialConfig" }
        resumeCounter()
    }

    fun destroy() {
        started = false
        activeTimeCounter?.cancel()
        closeDelayTimer?.cancel()
    }

    private fun resumeCounter() {
        if (started) {
            startActiveCounter(timerSeconds.toLong())
        }
    }

    private fun pauseCounter() {
        activeTimeCounter?.cancel()
    }

    private fun startActiveCounter(seconds: Long) {
        if (seconds <= 0) return
        activeTimeCounter?.cancel()
        activeTimeCounter = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerSeconds--
            }

            override fun onFinish() {
                timerSeconds = interstitialConfig.timer ?: 0
                loadAd()
            }
        }
        activeTimeCounter?.start()
    }

    private inner class AppLifeCycleHandler : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCounter()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                pauseCounter()
            }

            if (event == Lifecycle.Event.ON_DESTROY) {
                destroy()
                banner?.destroyAd()
                dialog?.dismiss()
            }
        }
    }

    private fun loadAd() {
        if (interstitialConfig.custom == 1) {
            loadCustomInterstitial()
        } else {
            loadInterstitial()
        }
    }

    private fun loadInterstitial() = findContext()?.let { activity ->
        tag.log { "Loading interstital with unit ${interstitialConfig.adunit}" }
        val interstitialAd = InterstitialAd(activity, interstitialConfig.adunit ?: "")
        interstitialAd.load(AdRequest().Builder().build()) { loaded ->
            if (loaded) {
                tag.log { "Interstitial ad has loaded and it should show now" }
                interstitialAd.show()
                resumeCounter()
            } else {
                tag.log { "Interstitial ad has failed and trying custom now" }
                loadCustomInterstitial()
            }
        }
    } ?: kotlin.run {
        tag.log { "Foreground context is not present for GAM load" }
        resumeCounter()
    }

    private fun findContext(): Activity? {
        if (activities.isEmpty()) return null
        return try {
            activities = activities.filter { !it.isDestroyed && !it.isFinishing } as ArrayList<Activity>
            val current = activities.firstOrNull { (it as? AppCompatActivity)?.lifecycle?.currentState == Lifecycle.State.RESUMED }
            return if (dialog?.isShowing == true) {
                null
            } else current
        } catch (e: Throwable) {
            tag.log { e.localizedMessage ?: "" }
            null
        }
    }

    private fun loadCustomInterstitial() = findContext()?.let { activity ->
        tag.log { "Loading banner with unit ${interstitialConfig.adunit}" }
        banner?.destroyAd()
        banner = BannerAdView(activity).also { it.makeInter() }
        banner?.setAdSizes(*getBannerSizes().toTypedArray())
        banner?.setAdUnitID(interstitialConfig.adunit ?: "")
        banner?.setAdListener(object : BannerAdListener {
            override fun onAdClicked(bannerAdView: BannerAdView) {
            }

            override fun onAdClosed(bannerAdView: BannerAdView) {
            }

            override fun onAdFailedToLoad(bannerAdView: BannerAdView, error: ABMError, retrying: Boolean) {
                tag.log { "Custom ad has load to failed with retry:$retrying" }
            }

            override fun onAdImpression(bannerAdView: BannerAdView) {}

            override fun onAdLoaded(bannerAdView: BannerAdView) {
                resumeCounter()
                showCustomAd(bannerAdView, activity)
            }

            override fun onAdOpened(bannerAdView: BannerAdView) {
            }
        })
        banner?.loadAd(AdRequest().Builder().build())
    } ?: kotlin.run {
        tag.log { "Foreground context not present for custom load" }
        resumeCounter()
    }

    private fun showCustomAd(ad: BannerAdView, activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing) return
        tag.log { "Custom ad has loaded and it should show now" }
        try {
            dialog = AppCompatDialog(activity, android.R.style.Theme_Light)
            dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog?.setContentView(R.layout.custom_inter_layout)
            dialog?.create()
            val rootLayout = dialog?.findViewById<LinearLayout>(R.id.root_layout)
            rootLayout?.removeAllViews()
            rootLayout?.addView(ad)
            dialog?.setOnDismissListener { ad.destroyAd() }
            dialog?.findViewById<ImageButton>(R.id.close_ad)?.setOnClickListener {
                dialog?.dismiss()
            }
            closeDelayTimer?.cancel()
            closeDelayTimer = object : CountDownTimer((interstitialConfig.closeDelay ?: 0).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {}

                override fun onFinish() {
                    dialog?.findViewById<ImageButton>(R.id.close_ad)?.visibility = View.VISIBLE
                }
            }
            closeDelayTimer?.start()
            dialog?.show()
        } catch (e: Throwable) {
            tag.log { "Custom ad could not show because : ${e.localizedMessage}" }
        }
    }

    private fun getBannerSizes(): List<BannerAdSize> {
        val temp = arrayListOf<BannerAdSize>()
        interstitialConfig.bannerSizes?.forEach {
            if (it.height.equals("fluid", true) || it.width.equals("fluid", true)) {
                temp.add(BannerAdSize.FLUID)
            }
            if (it.height?.toIntOrNull() != null && it.width?.toIntOrNull() != null) {
                temp.add(BannerAdSize(width = it.width.toIntOrNull() ?: 300, height = it.height.toIntOrNull() ?: 250))
            }
        }

        if (temp.isEmpty()) {
            temp.add(BannerAdSize.MEDIUM_RECTANGLE)
        }
        return temp
    }

}