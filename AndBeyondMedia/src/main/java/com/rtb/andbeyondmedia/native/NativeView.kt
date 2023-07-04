package com.rtb.andbeyondmedia.native

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.gms.ads.nativead.NativeAdView
import com.rtb.andbeyondmedia.R
import com.rtb.andbeyondmedia.databinding.NativeViewXmlBinding

class NativeView : FrameLayout {
    private lateinit var binding: NativeViewXmlBinding
    private var adView: NativeAdView

    constructor(context: Context) : super(context) {
        init(context, null)
        adView = NativeAdView(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
        adView = NativeAdView(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
        adView = NativeAdView(context, attrs, defStyleAttr)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
        adView = NativeAdView(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val view = inflate(context, R.layout.native_view_xml, this)
        binding = NativeViewXmlBinding.bind(view)
    }

    fun destroy() {
        adView.destroy()
    }
}