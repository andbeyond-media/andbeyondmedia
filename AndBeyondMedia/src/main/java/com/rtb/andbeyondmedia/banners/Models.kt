package com.rtb.andbeyondmedia.banners

import com.google.gson.annotations.SerializedName

internal data class Config(
        @SerializedName("aff")
        val affiliatedId: Long? = null,
        val prebid: Int? = null,
        @SerializedName("diff")
        val difference: Int? = null,
        @SerializedName("network")
        val networkId: String? = null,
        @SerializedName("networkcode")
        val networkCode: String? = null,
        @SerializedName("refresh")
        val refresh: Int? = null,
        @SerializedName("active")
        var activeRefreshInterval: Int? = null,
        @SerializedName("passive")
        var passiveRefreshInterval: Int? = null,
        val factor: Int? = null,
        @SerializedName("min_view")
        val minView: Int? = null,
        @SerializedName("min_view_rtb")
        val minViewRtb: Int? = null,
        val hijack: Hijack? = null,
        val type: String? = null,
        @SerializedName("pos")
        val position: Int? = null,
        val size: ArrayList<Size>? = null,
        @SerializedName("follow")
        val followSize: Int? = null,
        var lastRefreshAt: Long = 0
) {

    fun toSizes(): String {
        return size?.joinToString(",") ?: ""
    }

    data class Hijack(
            val status: Int? = null,
            val number: Int? = null
    )

    data class Size(
            val width: Int? = null,
            val height: Int? = null,
    ) {
        override fun toString(): String {
            return String.format("%d x %d", width, height)
        }
    }
}