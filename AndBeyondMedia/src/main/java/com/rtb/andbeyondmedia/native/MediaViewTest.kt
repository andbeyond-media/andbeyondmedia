package com.rtb.andbeyondmedia.native

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.google.android.gms.ads.nativead.MediaView

class MediaViewTest : MediaView {


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    fun setImageScaleType1(scaleType: ImageView.ScaleType) {
        // super.setImageScaleType(scaleType)
    }

    fun test() {

    }
}