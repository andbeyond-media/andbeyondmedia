package com.rtb.andbeyondmedia.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.work.*
import com.amazon.device.ads.AdRegistration
import com.amazon.device.ads.DTBAdNetwork
import com.amazon.device.ads.DTBAdNetworkInfo
import com.appharbr.sdk.configuration.AHSdkConfiguration
import com.appharbr.sdk.engine.AppHarbr
import com.appharbr.sdk.engine.InitializationFailureReason
import com.appharbr.sdk.engine.listeners.OnAppHarbrInitializationCompleteListener
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.common.URLs.BASE_URL
import com.rtb.andbeyondmedia.sdk.EventHelper.attachEventHandler
import com.rtb.andbeyondmedia.sdk.EventHelper.shouldHandle
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.rendering.models.openrtb.bidRequests.Ext
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess


object AndBeyondMedia {
    private var storeService: StoreService? = null
    private var configService: ConfigService? = null
    private var countryService: CountryService? = null
    private var workManager: WorkManager? = null
    internal var logEnabled = false
    internal var specialTag: String? = null

    fun initialize(context: Context, logsEnabled: Boolean = false) {
        attachEventHandler(context)
        this.logEnabled = logsEnabled
        fetchConfig(context)
    }

    internal fun getStoreService(context: Context): StoreService {
        @Synchronized
        if (storeService == null) {
            storeService = StoreService(context.getSharedPreferences(this.toString().substringBefore("@"), Context.MODE_PRIVATE))
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

    internal fun getCountryService(baseUrl: String): CountryService {
        @Synchronized
        if (countryService == null) {
            val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .writeTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS).hostnameVerifier { _, _ -> true }.build()
            countryService = Retrofit.Builder().baseUrl(baseUrl).client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(CountryService::class.java)
        }
        return countryService as CountryService
    }

    internal fun getWorkManager(context: Context): WorkManager {
        @Synchronized
        if (workManager == null) {
            workManager = WorkManager.getInstance(context)
        }
        return workManager as WorkManager
    }

    private fun fetchConfig(context: Context, delay: Long? = null) {
        if (delay != null && delay < 900) return
        try {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val workerRequest: OneTimeWorkRequest = delay?.let {
                OneTimeWorkRequestBuilder<ConfigSetWorker>().setConstraints(constraints).setInitialDelay(it, TimeUnit.SECONDS).build()
            } ?: kotlin.run {
                OneTimeWorkRequestBuilder<ConfigSetWorker>().setConstraints(constraints).build()
            }
            val workName: String = delay?.let {
                String.format("%s_%s", ConfigSetWorker::class.java.simpleName, it.toString())
            } ?: kotlin.run {
                ConfigSetWorker::class.java.simpleName
            }
            val workManager = getWorkManager(context)
            val storeService = getStoreService(context)
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workerRequest)
            workManager.getWorkInfoByIdLiveData(workerRequest.id).observeForever {
                if (it?.state == WorkInfo.State.SUCCEEDED) {
                    val config = storeService.config
                    specialTag = config?.infoConfig?.specialTag
                    logEnabled = (logEnabled || config?.infoConfig?.normalInfo == 1)
                    if (config?.countryStatus?.active == 1 && !config.countryStatus.url.isNullOrEmpty()) {
                        fetchCountry(context, config.countryStatus.url)
                    }
                    SDKManager.initialize(context)
                    if (config?.refetch != null) {
                        fetchConfig(context, storeService.config?.refetch)
                    }
                }
            }
        } catch (e: Throwable) {
            SDKManager.initialize(context)
        }
    }

    private fun fetchCountry(context: Context, baseUrl: String) {
        try {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val data = Data.Builder()
            data.putString("URL", baseUrl)
            val workerRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<CountryDetectionWorker>().setConstraints(constraints).setInputData(data.build()).build()
            val workName: String = CountryDetectionWorker::class.java.simpleName
            val workManager = getWorkManager(context)
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workerRequest)
        } catch (e: Throwable) {
            e.stackTrace
        }
    }
}

internal object EventHelper {

    fun attachEventHandler(context: Context) {
        val storeService = AndBeyondMedia.getStoreService(context)
        Thread.setDefaultUncaughtExceptionHandler(EventHandler(storeService, Thread.getDefaultUncaughtExceptionHandler()))
        SentryAndroid.init(context) { options ->
            options.environment = context.packageName
            options.dsn = "https://9bf82b481805d3068675828513d59d68@o4505753409421312.ingest.sentry.io/4505753410732032"
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> getProcessedEvent(storeService, event) }
        }
    }

    private fun getProcessedEvent(storeService: StoreService, event: SentryEvent): SentryEvent? {
        val sentEvent = if ((event.throwable?.stackTraceToString()?.contains(BuildConfig.LIBRARY_PACKAGE_NAME, true) == true
                        && shouldHandle(storeService.config?.events?.self ?: 100)) || (event.throwable?.stackTraceToString()?.contains("OutOfMemoryError", true) == true
                        && shouldHandle(storeService.config?.events?.oom ?: 100))) {
            event
        } else {
            if (shouldHandle(storeService.config?.events?.other ?: 0)) {
                event
            } else {
                null
            }
        }
        sentEvent?.setExtra("RAW_TRACE", event.throwable?.stackTraceToString() ?: "")
        sentEvent?.dist = BuildConfig.ADAPTER_VERSION
        sentEvent?.tags = hashMapOf<String?, String?>().apply {
            if (event.throwable?.stackTraceToString()?.contains(BuildConfig.LIBRARY_PACKAGE_NAME, true) == true) {
                put("SDK_ISSUE", "yes")
            } else {
                put("PUBLISHER_ISSUE", "Yes")
            }
        }

        return sentEvent
    }

    fun shouldHandle(max: Int): Boolean {
        return try {
            val number = (1..100).random()
            number in 1..max
        } catch (e: Throwable) {
            false
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
        } catch (e: Throwable) {
            Logger.ERROR.log(msg = e.message ?: "")
            storeService.config?.let {
                Result.success()
            } ?: Result.failure()
        }
    }
}

internal class CountryDetectionWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val storeService = AndBeyondMedia.getStoreService(context)
        return try {
            var baseUrl = inputData.getString("URL")
            baseUrl = if (baseUrl?.contains("apiip") == true) {
                baseUrl.substring(0, baseUrl.indexOf("check"))
            } else if (baseUrl?.contains("andbeyond") == true) {
                baseUrl.substring(0, baseUrl.indexOf("maxmind"))
            } else {
                ""
            }
            if (baseUrl.isEmpty()) {
                Result.failure()
            } else {
                val countryService = AndBeyondMedia.getCountryService(baseUrl)
                val response = if (baseUrl.contains("apiip")) {
                    countryService.getConfig(hashMapOf("accessKey" to "7ef45bac-167a-4aa8-8c99-bc8a28f80bc5", "fields" to "countryCode,latitude,longitude")).execute()
                } else {
                    countryService.getConfig().execute()
                }
                if (response.isSuccessful && response.body() != null) {
                    storeService.detectedCountry = response.body()
                    Result.success()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Throwable) {
            Logger.ERROR.log(msg = e.message ?: "")
            Result.failure()
        }
    }
}

internal object SDKManager {

    fun initialize(context: Context) {
        initializeGAM(context)
        val storeService = AndBeyondMedia.getStoreService(context)
        val config = storeService.config ?: return
        if (config.switch != 1) return
        initializePrebid(context, config.prebid)
        initializeGeoEdge(context, config.geoEdge?.apiKey)
        initializeAPS(context, config.aps)
    }

    private fun initializePrebid(context: Context, prebid: SDKConfig.Prebid?) {
        PrebidMobile.setPbsDebug(prebid?.debug == 1)
        PrebidMobile.setPrebidServerHost(Host.createCustomHost(prebid?.host ?: ""))
        PrebidMobile.setPrebidServerAccountId(prebid?.accountId ?: "")
        PrebidMobile.setTimeoutMillis(prebid?.timeout?.toIntOrNull() ?: 1000)
        PrebidMobile.initializeSdk(context) { Logger.INFO.log(msg = "Prebid Initialization Completed") }
        PrebidMobile.setShareGeoLocation(prebid?.location == null || prebid.location == 1)
        prebid?.gdpr?.let { TargetingParams.setSubjectToGDPR(it == 1) }
        if (TargetingParams.isSubjectToGDPR() == true) {
            TargetingParams.setGDPRConsentString(TargetingParams.getGDPRConsentString())
        }
        if (!prebid?.bundleName.isNullOrEmpty()) {
            TargetingParams.setBundleName(prebid?.bundleName)
        }
        if (!prebid?.domain.isNullOrEmpty()) {
            TargetingParams.setDomain(prebid?.domain)
        }
        if (!prebid?.storeURL.isNullOrEmpty()) {
            TargetingParams.setStoreUrl(prebid?.storeURL)
        }
        if (!prebid?.omidPartnerName.isNullOrEmpty()) {
            TargetingParams.setOmidPartnerName(prebid?.omidPartnerName)
        }
        if (!prebid?.omidPartnerVersion.isNullOrEmpty()) {
            TargetingParams.setOmidPartnerVersion(prebid?.omidPartnerVersion)
        }
        if (!prebid?.extParams.isNullOrEmpty()) {
            TargetingParams.setUserExt(Ext().apply {
                prebid?.extParams?.forEach { put(it.key ?: "", it.value ?: "") }
            })
        }
    }

    private fun initializeGAM(context: Context) {
        MobileAds.initialize(context) {
            Logger.INFO.log(msg = "GAM Initialization complete.")
        }
    }

    private fun initializeGeoEdge(context: Context, apiKey: String?) {
        if (apiKey.isNullOrEmpty()) return
        val configuration = AHSdkConfiguration.Builder(apiKey).build()
        AppHarbr.initialize(context, configuration, object : OnAppHarbrInitializationCompleteListener {
            override fun onSuccess() {
                Logger.INFO.log(msg = "AppHarbr SDK Initialized Successfully")
            }

            override fun onFailure(reason: InitializationFailureReason) {
                Logger.ERROR.log(msg = "AppHarbr SDK Initialization Failed: ${reason.readableHumanReason}")
            }

        })
    }

    private fun initializeAPS(context: Context, aps: SDKConfig.Aps?) {
        if (aps?.appKey.isNullOrEmpty()) return
        fun init() {
            AdRegistration.getInstance(aps?.appKey ?: "", context)
            AdRegistration.setAdNetworkInfo(DTBAdNetworkInfo(DTBAdNetwork.GOOGLE_AD_MANAGER))
            AdRegistration.useGeoLocation(aps?.location == null || aps.location == 1)
        }
        if (aps?.delay == null || aps.delay.toIntOrNull() == 0) {
            init()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ init() }, aps.delay.toLongOrNull() ?: 1L)
        }
    }
}

internal interface ConfigService {
    @GET("appconfig1.php")
    fun getConfig(@QueryMap params: HashMap<String, Any>): Call<SDKConfig>
}

internal interface CountryService {
    @GET("check")
    fun getConfig(@QueryMap params: HashMap<String, Any>): Call<CountryModel>

    @GET("maxmind.php")
    fun getConfig(): Call<CountryModel>
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

    var detectedCountry: CountryModel?
        get() {
            val string = prefs.getString("COUNTRY", "") ?: ""
            if (string.isEmpty()) return null
            return Gson().fromJson(string, CountryModel::class.java)
        }
        set(value) = prefs.edit().apply {
            value?.let { putString("COUNTRY", Gson().toJson(value)) } ?: kotlin.run { remove("COUNTRY") }
        }.apply()
}

internal class EventHandler(private val storeService: StoreService, private val defaultHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, exception: Throwable) {

        if (exception.stackTraceToString().contains(BuildConfig.LIBRARY_PACKAGE_NAME, true) && shouldHandle(storeService.config?.events?.self ?: 100)) {
            Sentry.captureException(exception)
            exitProcess(0)
        } else if (exception.stackTraceToString().contains("OutOfMemoryError", true) && shouldHandle(storeService.config?.events?.oom ?: 100)) {
            Sentry.captureException(exception)
            exitProcess(0)
        } else {
            if (shouldHandle(storeService.config?.events?.other ?: 0)) {
                Sentry.captureException(exception)
                exitProcess(0)
            } else {
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
    }

}