package com.rtb.andbeyondmedia.intersitial

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.rtb.andbeyondmedia.sdk.SDKConfig

@Keep
internal data class InterstitialConfig(
        @SerializedName("customUnitName")
        var customUnitName: String = "",
        @SerializedName("isNewUnit")
        var isNewUnit: Boolean = false,
        @SerializedName("position")
        var position: Int = 0,
        @SerializedName("retryConfig")
        var retryConfig: SDKConfig.RetryConfig? = null,
        @SerializedName("newUnit")
        var newUnit: SDKConfig.LoadConfig? = null,
        @SerializedName("hijack")
        var hijack: SDKConfig.LoadConfig? = null,
        @SerializedName("unFilled")
        var unFilled: SDKConfig.LoadConfig? = null,
        @SerializedName("placement")
        var placement: SDKConfig.Placement? = null,
        @SerializedName("format")
        var format: String? = null
)

@Keep
internal data class SilentInterstitialConfig(
        @SerializedName("active")
        val activePercentage: Int? = null,
        @SerializedName("adunit")
        val adunit: String? = null,
        @SerializedName("custom")
        val custom: Int? = null,
        @SerializedName("timer")
        val timer: Int? = null,
        @SerializedName("close_delay")
        val closeDelay: Int? = null,
        @SerializedName("sizes")
        val bannerSizes: List<SDKConfig.Size>? = null
)
