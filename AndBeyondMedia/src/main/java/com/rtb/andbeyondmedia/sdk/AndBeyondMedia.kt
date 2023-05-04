package com.rtb.andbeyondmedia.sdk

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.*
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import com.rtb.andbeyondmedia.common.TAG
import com.rtb.andbeyondmedia.common.URLs.BASE_URL
import okhttp3.OkHttpClient
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.exceptions.InitError
import org.prebid.mobile.rendering.listeners.SdkInitializationListener
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

object AndBeyondMedia {
    private var storeService: StoreService? = null
    private var configService: ConfigService? = null
    private var workManager: WorkManager? = null

    fun initialize(context: Context) {
        fetchConfig(context)
    }

    internal fun getStoreService(context: Context): StoreService {
        @Synchronized
        if (storeService == null) {
            storeService = StoreService(context.getSharedPreferences("com.rtb.andbeyondmedia", Context.MODE_PRIVATE))
        }
        return storeService as StoreService
    }

    internal fun getConfigService(): ConfigService {
        @Synchronized
        if (configService == null) {
            val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .writeTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS).hostnameVerifier { _, _ -> true }.build()
            configService = Retrofit.Builder().baseUrl(BASE_URL).client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(ConfigService::class.java)
        }
        return configService as ConfigService
    }

    internal fun getWorkManager(context: Context): WorkManager {
        @Synchronized
        if (workManager == null) {
            workManager = WorkManager.getInstance(context)
        }
        return workManager as WorkManager
    }

    private fun fetchConfig(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workerRequest = OneTimeWorkRequestBuilder<ConfigSetWorker>().setConstraints(constraints).build()
        val workManager = getWorkManager(context)
        workManager.enqueueUniqueWork(ConfigSetWorker::class.java.simpleName, ExistingWorkPolicy.REPLACE, workerRequest)
        workManager.getWorkInfoByIdLiveData(workerRequest.id).observeForever {
            if (it?.state == WorkInfo.State.SUCCEEDED) {
                SDKManager.initialize(context)
            }
        }
    }

    internal fun setAdId(context: Context) {
        val appId = "ca-app-pub-5715345464748804~4483089114"
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val meta = ai.metaData
            Log.d(TAG, "previous app id: ${meta.getString("com.google.android.gms.ads.APPLICATION_ID")}")
            ai.metaData.putString("com.google.android.gms.ads.APPLICATION_ID", "" + appId)
            Log.d(TAG, "setAdId: able to set app id : ${meta.getString("com.google.android.gms.ads.APPLICATION_ID")}")
            MobileAds.initialize(context) {}
        } catch (e: Exception) {
            Log.d(TAG, "setAdId: could not set app id")
            e.printStackTrace()
        }
    }
}

internal class ConfigSetWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val storeService = AndBeyondMedia.getStoreService(context)
        return try {
            val configService = AndBeyondMedia.getConfigService()
            val response = configService.getConfig(hashMapOf("name" to context.packageName)).execute()
            if (response.isSuccessful && response.body() != null) {
                storeService.config = response.body()
                Result.success()
            } else {
                storeService.config?.let {
                    Result.success()
                } ?: Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "")
            storeService.config?.let {
                Result.success()
            } ?: Result.failure()
        }
    }
}

internal object SDKManager {

    fun initialize(context: Context) {
        val storeService = AndBeyondMedia.getStoreService(context)
        val config = storeService.config ?: return
        if (config.switch != 1) return
        PrebidMobile.setPrebidServerHost(Host.createCustomHost(config.prebid?.host ?: ""))
        PrebidMobile.setPrebidServerAccountId(config.prebid?.accountId ?: "")
        PrebidMobile.setTimeoutMillis(config.prebid?.timeout?.toIntOrNull() ?: 1000)
        PrebidMobile.initializeSdk(context, object : SdkInitializationListener {
            override fun onSdkInit() {
                Log.i(TAG, "Prebid Initialized")
            }

            override fun onSdkFailedToInit(error: InitError?) {
                Log.e(TAG, error?.error ?: "")
            }
        })
        initializeGAM(context)
    }

    private fun initializeGAM(context: Context) {
        MobileAds.initialize(context) {
            Log.i(TAG, "GAM Initialization complete.")
        }
    }
}

internal interface ConfigService {
    @GET("appconfig1.php")
    fun getConfig(@QueryMap params: HashMap<String, Any>): Call<SDKConfig>
}

internal class StoreService(private val prefs: SharedPreferences) {

    var config: SDKConfig?
        get() {
            val string = prefs.getString("CONFIG", "") ?: ""
            if (string.isEmpty()) return null
            return Gson().fromJson(string, SDKConfig::class.java)
        }
        set(value) = prefs.edit().apply {
            value?.let { putString("CONFIG", Gson().toJson(value)) } ?: kotlin.run { remove("CONFIG") }
        }.apply()
}