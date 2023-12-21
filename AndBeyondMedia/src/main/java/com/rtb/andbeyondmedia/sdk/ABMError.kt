package com.rtb.andbeyondmedia.sdk

import androidx.annotation.Keep

@Keep
data class ABMError(
        val code: Int = 0,
        val message: String = "",
        val extras: Any? = null
)
