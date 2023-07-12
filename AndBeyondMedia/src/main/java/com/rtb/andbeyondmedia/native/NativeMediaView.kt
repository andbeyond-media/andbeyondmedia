package com.rtb.andbeyondmedia.native

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.gms.ads.MediaContent
import com.rtb.andbeyondmedia.R
import com.rtb.andbeyondmedia.databinding.NativeMediaViewBinding

class NativeMediaView : LinearLayout {

    private lateinit var binding: NativeMediaViewBinding

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
        val view = inflate(context, R.layout.native_media_view, this)
        binding = NativeMediaViewBinding.bind(view)
    }

    fun setMediaContent(mediaContent: MediaContent) {
        binding.mediaView.mediaContent = mediaContent
    }

    fun setImageScaleType(scaleType: ImageView.ScaleType) {
        binding.mediaView.setImageScaleType(scaleType)
    }
}