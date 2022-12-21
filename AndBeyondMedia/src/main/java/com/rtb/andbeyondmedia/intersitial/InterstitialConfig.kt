package com.rtb.andbeyondmedia.intersitial

import com.rtb.andbeyondmedia.sdk.SDKConfig

internal class InterstitialConfig(
        var customUnitName: String = "",
        var isNewUnit: Boolean = false,
        var position: Int = 0,
        var newUnit: SDKConfig.LoadConfig? = null,
        var hijack: SDKConfig.LoadConfig? = null,
        var unFilled: SDKConfig.LoadConfig? = null,
        var placement: SDKConfig.Placement? = null,
)
