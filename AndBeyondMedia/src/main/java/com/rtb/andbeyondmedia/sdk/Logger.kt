package com.rtb.andbeyondmedia.sdk

import android.util.Log
import com.rtb.andbeyondmedia.common.LogLevel
import com.rtb.andbeyondmedia.common.TAG


internal fun LogLevel.log(msg: String) {
    if (!AndBeyondMedia.logEnabled()) return
    when (this) {
        LogLevel.INFO -> Log.i(TAG, msg)
        LogLevel.DEBUG -> Log.d(TAG, msg)
        LogLevel.ERROR -> Log.e(TAG, msg)
    }
}