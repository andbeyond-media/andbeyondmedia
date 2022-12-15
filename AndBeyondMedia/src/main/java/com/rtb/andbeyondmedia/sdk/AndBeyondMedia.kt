package com.rtb.andbeyondmedia.sdk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import com.rtb.andbeyondmedia.common.TAG
import com.rtb.andbeyondmedia.common.URLs.BASE_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.module
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
    fun initialize(context: Context) {
        startKoin {
            androidContext(context)
            modules(sdkModule)
        }
        fetchConfig(context)
    }

    private fun fetchConfig(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workerRequest = OneTimeWorkRequestBuilder<ConfigSetWorker>().setConstraints(constraints).build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(ConfigSetWorker::class.java.simpleName, ExistingWorkPolicy.REPLACE, workerRequest)
        workManager.getWorkInfoByIdLiveData(workerRequest.id).observeForever {
            if (it.state == WorkInfo.State.SUCCEEDED) {
                SDKManager.initialize(context)
            }
        }
    }
}

internal val sdkModule = module {

    single { WorkManager.getInstance(androidContext()) }

    single {
        StoreService(androidContext().getSharedPreferences(androidContext().packageName, Context.MODE_PRIVATE))
    }

    single {
        val bodyInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder().addInterceptor(bodyInterceptor)
                .connectTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS).build()
    }

    single {
        val client = Retrofit.Builder().baseUrl(BASE_URL).client(get())
                .addConverterFactory(GsonConverterFactory.create()).build()
        client.create(ConfigService::class.java)
    }
}

internal class ConfigSetWorker(context: Context, params: WorkerParameters) : Worker(context, params), KoinComponent {
    override fun doWork(): Result {
        return try {
            val storeService: StoreService by inject()
            val configService: ConfigService by inject()
            val response = configService.getConfig(hashMapOf()).execute()
            if (response.isSuccessful && response.body() != null) {
                storeService.config = response.body()
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "")
            Result.failure()
        }
    }
}

internal object SDKManager : KoinComponent {

    fun initialize(context: Context) {
        val storeService: StoreService by inject()
        val config = storeService.config ?: return
        if (config.switch != 1) return
        PrebidMobile.setPrebidServerHost(Host.createCustomHost(config.prebid?.host ?: ""))
        PrebidMobile.setPrebidServerAccountId(config.prebid?.accountId ?: "")
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