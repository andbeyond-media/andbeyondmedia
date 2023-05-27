package com.rtb.andbeyondmedia.sdk

import android.util.Log
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.common.TAG


internal fun LogLevel.log(tag: String = TAG, msg: String) {
    if (!AndBeyondMedia.logEnabled()) return
    when (this) {
        LogLevel.INFO -> Log.i(tag, msg)
        LogLevel.DEBUG -> Log.d(tag, msg)
        LogLevel.ERROR -> Log.e(tag, msg)
    }
}