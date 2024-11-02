package com.rtb.andbeyondmedia.sdk

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build

internal class NetworkManager() : ConnectivityManager.NetworkCallback() {

    var isInternetAvailable: Boolean = true

    fun register(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (context.getSystemService(Activity.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.registerDefaultNetworkCallback(this)
            }
        } catch (_: Throwable) {
        }
    }

    fun unRegister(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (context.getSystemService(Activity.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(this)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        isInternetAvailable = true
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        isInternetAvailable = false
    }

    override fun onUnavailable() {
        super.onUnavailable()
        isInternetAvailable = false
    }
}