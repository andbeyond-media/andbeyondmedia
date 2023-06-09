package com.rtb.andbeyondmedia.rewardedinterstitial

import com.google.gson.annotations.SerializedName

data class Reward(
        @SerializedName("amount")
        val amount: Int,
        @SerializedName("type")
        val type: String
)
