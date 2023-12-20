package com.rtb.andbeyondmedia.sdk

import android.util.Log
import android.view.View
import com.rtb.andbeyondmedia.common.TAG

internal enum class Logger {
    DEBUG, INFO, ERROR
}

internal fun Logger.log(tag: String = TAG, msg: String) {
    if (!AndBeyondMedia.logEnabled) return
    when (this) {
        Logger.INFO -> Log.i(tag, msg)
        Logger.DEBUG -> Log.d(tag, msg)
        Logger.ERROR -> Log.e(tag, msg)
    }
}

internal fun log(getMessage: () -> String) {
    if (!AndBeyondMedia.specialTag.isNullOrEmpty()) {
        try {
            Log.i(AndBeyondMedia.specialTag ?: "", getMessage())
        } catch (_: Throwable) {

        }
    }
}

internal fun String?.log(getMessage: () -> String) {
    if (!AndBeyondMedia.specialTag.isNullOrEmpty()) {
        try {
            Log.i(AndBeyondMedia.specialTag, String.format("%s~ %s", this, getMessage()))
        } catch (_: Throwable) {
        }
    }
}

internal fun View?.log(getMessage: () -> String) {
    if (!AndBeyondMedia.specialTag.isNullOrEmpty()) {
        try {
            Log.i(AndBeyondMedia.specialTag, String.format("%d-%s", this?.id ?: -1, getMessage()))
        } catch (_: Throwable) {
        }
    }
}