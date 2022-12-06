package com.rtb.andbeyondmedia.banners

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.rtb.andbeyondmedia.R
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.databinding.BannerAdViewBinding
import java.util.*

class BannerAdView : LinearLayout, AdManagerListener {

    private lateinit var mContext: Context
    private lateinit var binding: BannerAdViewBinding
    private lateinit var adView: AdManagerAdView
    private lateinit var adManager: BannerManager
    private var adType: String = AdTypes.OTHER
    private lateinit var currentAdUnit: String
    private lateinit var currentAdSizes: List<AdSize>
    private var isFirstLoad = true
    private var config: Config? = null
    private var bannerAdListener: BannerAdListener? = null

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        this.mContext = context
        adManager = BannerManager(mContext, this)
        val view = inflate(context, R.layout.banner_ad_view, this)
        binding = BannerAdViewBinding.bind(view)
        attrs?.let {
            context.obtainStyledAttributes(it, R.styleable.BannerAdView).apply {
                val adUnitId = getString(R.styleable.BannerAdView_adUnitId) ?: ""
                val adSize = getString(R.styleable.BannerAdView_adSize) ?: ""
                var adSizes = getString(R.styleable.BannerAdView_adSizes) ?: ""
                adType = getString(R.styleable.BannerAdView_adType) ?: AdTypes.OTHER
                if (!adSizes.contains(adSize)) {
                    adSizes = if (adSizes.isNotEmpty()) {
                        String.format(Locale.ENGLISH, "%s,%s", adSizes, adSize)
                    } else {
                        adSize
                    }
                }
                if (adUnitId.isNotEmpty() && adSizes.isNotEmpty()) {
                    attachAdView(adUnitId, adManager.getSize(adSizes))
                }
            }
        }
    }


    override fun attachAdView(adUnitId: String, adSizes: List<AdSize>) {
        adView = AdManagerAdView(mContext)
        currentAdSizes = adSizes
        currentAdUnit = adUnitId
        adView.setAdSizes(*adSizes.toTypedArray())
        adView.adUnitId = adUnitId
        adView.adListener = adListener
        binding.root.removeAllViews()
        binding.root.addView(adView)
    }

    fun setAdSize(adSize: BannerAdSize) = setAdSizes(adSize)

    fun setAdSizes(vararg adSizes: BannerAdSize) {
        this.currentAdSizes = getProperAdSizes(*adSizes)
        if (this::currentAdSizes.isInitialized && this::currentAdUnit.isInitialized) {
            attachAdView(adUnitId = currentAdUnit, adSizes = currentAdSizes)
        }
    }

    private fun getProperAdSizes(vararg adSizes: BannerAdSize): ArrayList<AdSize> {
        val adSizeList = arrayListOf<AdSize>()
        adSizes.toList().forEach {
            adSizeList.add(it.adSize)
        }
        return adSizeList
    }


    fun setAdUnitID(adUnitId: String) {
        this.currentAdUnit = adUnitId
        if (this::currentAdSizes.isInitialized && this::currentAdUnit.isInitialized) {
            attachAdView(adUnitId = currentAdUnit, adSizes = currentAdSizes)
        }
    }

    fun setAdType(adType: String) {
        this.adType = adType
        if (this::currentAdSizes.isInitialized && this::currentAdUnit.isInitialized) {
            attachAdView(adUnitId = currentAdUnit, adSizes = currentAdSizes)
        }
    }

    fun setAdListener(listener: BannerAdListener) {
        this.bannerAdListener = listener
    }

    override fun loadAd(request: AdRequest): Boolean {
        val adRequest = request.getAdRequest() ?: return false
        fun load() {
            if (this::adView.isInitialized) {
                adView.loadAd(adRequest)
            }
        }
        if (isFirstLoad && this::currentAdSizes.isInitialized) {
            adManager.fetchConfig(currentAdUnit, currentAdSizes as ArrayList<AdSize>, adType) {
                this.config = it
                load()
            }
        } else {
            load()
        }
        return true
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        adManager.saveVisibility(isVisible)
    }

    private val adListener = object : AdListener() {
        override fun onAdClicked() {
            bannerAdListener?.onAdClicked()
            super.onAdClicked()
        }

        override fun onAdClosed() {
            bannerAdListener?.onAdClosed()
            super.onAdClosed()
        }

        override fun onAdFailedToLoad(p0: LoadAdError) {
            bannerAdListener?.onAdFailedToLoad(p0.toString())
            super.onAdFailedToLoad(p0)
            if (isFirstLoad) {
                isFirstLoad = false
                if (config?.hijack?.status == 1) {
                    adManager.refresh(forced = true)
                }
            }
        }

        override fun onAdImpression() {
            bannerAdListener?.onAdImpression()
            super.onAdImpression()
        }

        override fun onAdLoaded() {
            bannerAdListener?.onAdLoaded()
            super.onAdLoaded()
            if (config?.refresh == 1) {
                adManager.startRefreshing(resetVisibleTime = true, isPublisherLoad = isFirstLoad)
            }
            if (isFirstLoad) {
                isFirstLoad = false
            }
        }

        override fun onAdOpened() {
            bannerAdListener?.onAdOpened()
            super.onAdOpened()
        }
    }

    fun pauseAd() {
        adView.pause()
        adManager.adPaused()
    }

    fun resumeAd() {
        adView.resume()
        adManager.adResumed()
    }

    fun destroyAd() {
        adView.destroy()
        adManager.adDestroyed()
    }
}