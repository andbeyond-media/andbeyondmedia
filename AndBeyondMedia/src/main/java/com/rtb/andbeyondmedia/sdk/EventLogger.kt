package com.rtb.andbeyondmedia.sdk

import android.content.Context
import android.view.View
import com.google.gson.Gson
import com.rtb.andbeyondmedia.common.getHardwareDeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object EventLogger {
    private var config: SafeImpressionConfig? = null
    private var affId: String? = null
    private var client: OkHttpClient? = null

    fun setConfig(config: SafeImpressionConfig?, affId: String) {
        this.config = config
        this.affId = affId
    }

    private fun getClient(): OkHttpClient {
        @Synchronized
        if (client == null) {
            val loggingInterceptor = HttpLoggingInterceptor().setLevel(if (AndBeyondMedia.specialTag.isNullOrEmpty()) HttpLoggingInterceptor.Level.NONE else HttpLoggingInterceptor.Level.BODY)
            client = OkHttpClient.Builder().addInterceptor(loggingInterceptor)
                    .connectTimeout(3000, TimeUnit.MILLISECONDS)
                    .writeTimeout(3000, TimeUnit.MILLISECONDS)
                    .readTimeout(3000, TimeUnit.MILLISECONDS).build()
        }
        return client as OkHttpClient
    }

    private fun sendEvent(body: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            if (config?.eventLogging != 1 || config?.eventLogUrl.isNullOrEmpty()) return@launch
            val urlBuilder = config?.eventLogUrl?.toHttpUrlOrNull() ?: return@launch
            val requestBody = body.toRequestBody()
            val request: Request = Request.Builder().url(urlBuilder).method("POST", requestBody).build()
            getClient().newCall(request).execute()
        } catch (_: Throwable) {
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH).format(Date())
    }

    fun logEvent(view: View?, context: Context, eventName: String, adUnit: String, params: Map<String, Any> = hashMapOf()) {
        try {
            val data = hashMapOf(
                    "aff" to (affId ?: ""),
                    "adunit" to adUnit.substringAfterLast("/"),
                    "eventname" to eventName,
                    "timestamp" to getCurrentTime(),
            )
            params.forEach { data[it.key] = it.value.toString() }
            data["param3"] = view?.id.toString()
            data["param4"] = context.getHardwareDeviceId()
            val body = hashMapOf("body" to Gson().toJson(data))
            sendEvent(Gson().toJson(body))
        } catch (_: Throwable) {
        }
    }

    object Events {
        const val AD_STORED = "AD_STORED"
        const val OWN_AD_NOT_FOUND = "OWN_AD_NOT_FOUND"
        const val OWN_AD_USED = "OWN_AD_USED"
        const val RESUED_OTHER_AD = "RESUED_OTHER_AD"
    }
}