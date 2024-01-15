package com.rtb.andbeyondmedia.intersitial

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia
import com.rtb.andbeyondmedia.sdk.ConfigSetWorker
import com.rtb.andbeyondmedia.sdk.SDKConfig
import com.rtb.andbeyondmedia.sdk.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SilentInterstitial(private val context: Context) {

    val activities: ArrayList<Activity> = arrayListOf()
    private var sdkConfig: SDKConfig? = null
    private val storeService = AndBeyondMedia.getStoreService(context)
    private var interstitialConfig: SilentInterstitialConfig = SilentInterstitialConfig()
    private var shouldBeActive: Boolean = false

    init {
        sdkConfig = storeService.config
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun shouldSetConfig(callback: (Boolean) -> Unit) = CoroutineScope(Dispatchers.Main).launch {
        val workManager = AndBeyondMedia.getWorkManager(context)
        val workers = workManager.getWorkInfosForUniqueWork(ConfigSetWorker::class.java.simpleName).get()
        if (workers.isNullOrEmpty()) {
            callback(false)
        } else {
            try {
                val workerData = workManager.getWorkInfoByIdLiveData(workers[0].id)
                workerData?.observeForever(object : Observer<WorkInfo?> {
                    override fun onChanged(value: WorkInfo?) {
                        if (value?.state != WorkInfo.State.RUNNING && value?.state != WorkInfo.State.ENQUEUED) {
                            workerData.removeObserver(this)
                            sdkConfig = storeService.config
                            shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
                            callback(shouldBeActive)
                        }
                    }
                })
            } catch (e: Throwable) {
                callback(false)
            }
        }
    }

    private fun setConfig() {
        if (!shouldBeActive) return
        interstitialConfig = sdkConfig?.silentInterstitialConfig ?: SilentInterstitialConfig()
        if ((interstitialConfig.activePercentage ?: 0) == 0) return
    }

    fun start() {
        this.javaClass.simpleName.log { String.format("%s:%s- Version:%s", "setConfig", "entry", BuildConfig.ADAPTER_VERSION) }
        shouldBeActive = !(sdkConfig == null || sdkConfig?.switch != 1)
        shouldSetConfig { if (it) setConfig() }
    }

    fun stop() {


    }

}