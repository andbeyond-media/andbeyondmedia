package com.rtb.andbeyondmedia.sdk

import android.view.ViewGroup
import com.google.android.gms.ads.admanager.AdManagerAdView

internal object SavedViews {
    private val views = arrayListOf<AdManagerAdView>()

    fun getLoadedAd(): AdManagerAdView? {
        val view = views.firstOrNull { it.responseInfo != null }
        if (view?.parent != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        return view
    }

    fun saveView(view: AdManagerAdView) {
        views.add(view)
    }

    fun clearViews(view: AdManagerAdView) {
        views.removeAll { it.id == view.id }
    }
}