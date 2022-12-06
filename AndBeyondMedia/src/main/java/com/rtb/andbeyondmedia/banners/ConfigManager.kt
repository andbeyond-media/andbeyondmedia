package com.rtb.andbeyondmedia.banners

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException


private object URLs {
    const val BANNER = "https://prebid.andbeyond.media/appconfig.php"
}

internal fun Context.getBannerConfig(width: Int, type: String, loaded: (Config?) -> Unit) {
    val activity = (this@getBannerConfig as? Activity) ?: kotlin.run {
        loaded(null)
        return
    }
    val client = OkHttpClient.Builder().build()
    val url = URLs.BANNER.toHttpUrlOrNull()?.newBuilder()?.apply {
        addQueryParameter("width", width.toString())
        addQueryParameter("type", type)
        addQueryParameter("page", activity.javaClass.simpleName)
        addQueryParameter("name", activity.javaClass.name.removeSuffix(activity.javaClass.simpleName))
    }?.build()?.toString() ?: kotlin.run {
        loaded(null)
        return
    }
    val request = Request.Builder().url(url).build()
    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            activity.runOnUiThread { loaded(null) }
        }

        override fun onResponse(call: Call, response: Response) {
            val responseString = response.body.string()
            activity.runOnUiThread { loaded(Gson().fromJson(responseString, Config::class.java)) }
        }
    })
}