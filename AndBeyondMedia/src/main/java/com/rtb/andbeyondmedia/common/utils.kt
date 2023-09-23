package com.rtb.andbeyondmedia.common

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import java.util.Random

fun Context.connectionAvailable(): Boolean? {
    return try {
        val internetAvailable: Boolean
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi: NetworkInfo? = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val network: NetworkInfo? = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
        internetAvailable = wifi != null && wifi.isConnected || network != null && network.isConnected
        internetAvailable
    } catch (e: Throwable) {
        null
    }
}

fun Context.dpToPx(value: Int): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}

fun getUniqueId(): String {
    val ALLOWED_CHARACTERS = "0123456789qwertyuiopasdfghjklzxcvbnm-"
    val random = Random()
    val sb = StringBuilder(36)
    for (i in 0 until 36) {
        sb.append(ALLOWED_CHARACTERS[random.nextInt(ALLOWED_CHARACTERS.length)])
    }
    return sb.toString()
}

@SuppressLint("HardwareIds")
fun Context.getDeviceId() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""

@SuppressLint("MissingPermission")
fun Context.getLocation() = try {
    val locationManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getSystemService(LocationManager::class.java)
    } else {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
} catch (e: Throwable) {
    null
}