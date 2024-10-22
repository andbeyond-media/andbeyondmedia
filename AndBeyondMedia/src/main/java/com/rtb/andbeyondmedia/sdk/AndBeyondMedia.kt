package com.rtb.andbeyondmedia.sdk

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amazon.device.ads.AdRegistration
import com.amazon.device.ads.DTBAdNetwork
import com.amazon.device.ads.DTBAdNetworkInfo
import com.amazon.device.ads.MRAIDPolicy
import com.appharbr.sdk.configuration.AHSdkConfiguration
import com.appharbr.sdk.engine.AppHarbr
import com.appharbr.sdk.engine.InitializationFailureReason
import com.appharbr.sdk.engine.listeners.OnAppHarbrInitializationCompleteListener
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pubmatic.sdk.common.OpenWrapSDK
import com.pubmatic.sdk.common.models.POBApplicationInfo
import com.rtb.andbeyondmedia.BuildConfig
import com.rtb.andbeyondmedia.common.URLs.BASE_URL
import com.rtb.andbeyondmedia.intersitial.SilentInterstitial
import com.rtb.andbeyondmedia.intersitial.SilentInterstitialConfig
import com.rtb.andbeyondmedia.sdk.EventHelper.attachEventHandler
import com.rtb.andbeyondmedia.sdk.EventHelper.attachSentry
import com.rtb.andbeyondmedia.sdk.EventHelper.shouldHandle
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.rendering.models.openrtb.bidRequests.Ext
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess


object AndBeyondMedia {
    private var storeService: StoreService? = null
    private var configService: ConfigService? = null
    private var countryService: CountryService? = null
    private var workManager: WorkManager? = null
    internal var logEnabled = false
    internal var specialTag: String? = null
    internal var cachedConfig: SDKConfig? = null
    internal var cachedCountryConfig: CountryModel? = null
    internal var configFile: File? = null
    internal var configCountryFile: File? = null
    private var silentInterstitial = SilentInterstitial()

    fun initialize(context: Context, logsEnabled: Boolean = false) {
        attachEventHandler(context)
        this.logEnabled = logsEnabled
        fetchConfig(context)
    }

    @Synchronized
    internal fun getStoreService(context: Context): StoreService {
        if (storeService == null) {
            if (configFile == null) {
                configFile = File(context.applicationContext.filesDir, "config_file")
            }
            if (configCountryFile == null) {
                configCountryFile = File(context.applicationContext.filesDir, "country_config_file")
            }
            configFile?.let {
                storeService = StoreService(context.getSharedPreferences(this.toString().substringBefore("@"), Context.MODE_PRIVATE), it, configCountryFile)
            }
        }
        return storeService as StoreService
    }

    @Synchronized
    internal fun getConfigService(): ConfigService {
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

    @Synchronized
    internal fun getCountryService(baseUrl: String): CountryService {
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

    @Synchronized
    internal fun getWorkManager(context: Context): WorkManager {
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
                    storeService.getConfig { config ->
                        specialTag = config?.infoConfig?.specialTag
                        logEnabled = (logEnabled || config?.infoConfig?.normalInfo == 1)
                        if (config?.countryStatus?.active == 1 && !config.countryStatus.url.isNullOrEmpty()) {
                            fetchCountry(context, config.countryStatus.url)
                        }
                        attachSentry(context, config?.events)
                        SDKManager.initialize(context, config)
                        if (config?.refetch != null) {
                            fetchConfig(context, config.refetch)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            SDKManager.initialize(context, null)
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
            val storeService = getStoreService(context)
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workerRequest)
            workManager.getWorkInfoByIdLiveData(workerRequest.id).observeForever {
                if (it?.state == WorkInfo.State.SUCCEEDED) {
                    storeService.getConfig { config ->
                        storeService.getDetectedCountry { countryConfig ->
                            checkForSilentInterstitial(context, config?.silentInterstitialConfig, countryConfig)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.stackTrace
        }
    }

    fun registerActivity(activity: Activity) {
        silentInterstitial.registerActivity(activity)
    }

    private fun checkForSilentInterstitial(context: Context, silentInterstitialConfig: SilentInterstitialConfig?, countryConfig: CountryModel?) {
        if (silentInterstitialConfig == null) {
            silentInterstitial.destroy()
            return
        }
        val shouldStart: Boolean
        val regionConfig = silentInterstitialConfig.regionConfig
        shouldStart = if (regionConfig == null || (regionConfig.getCities().isEmpty() && regionConfig.getStates().isEmpty() && regionConfig.getCountries().isEmpty())) {
            true
        } else {
            if ((regionConfig.mode ?: "allow").contains("allow", true)) {
                regionConfig.getCities().any { it.equals(countryConfig?.city, true) }
                        || regionConfig.getStates().any { it.equals(countryConfig?.state, true) }
                        || regionConfig.getCountries().any { it.equals(countryConfig?.countryCode, true) }
            } else {
                regionConfig.getCities().none { it.equals(countryConfig?.city, true) }
                        && regionConfig.getStates().none { it.equals(countryConfig?.state, true) }
                        && regionConfig.getCountries().none { it.equals(countryConfig?.countryCode, true) }
            }
        }
        val number = (1..100).random()
        if (shouldStart && number in 1..(silentInterstitialConfig.activePercentage ?: 0)) {
            silentInterstitial.init(context)
        }
    }
}

internal object EventHelper {

    fun attachEventHandler(context: Context) {
        val storeService = AndBeyondMedia.getStoreService(context)
        Thread.setDefaultUncaughtExceptionHandler(EventHandler(storeService, Thread.getDefaultUncaughtExceptionHandler()))
    }

    fun attachSentry(context: Context, events: SDKConfig.Events?) {
        val sentryInitPercentage = events?.sentry ?: 100
        if (shouldHandle(sentryInitPercentage) && !Sentry.isEnabled()) {
            SentryAndroid.init(context) { options ->
                options.environment = context.packageName
                options.dsn = "https://9bf82b481805d3068675828513d59d68@o4505753409421312.ingest.sentry.io/4505753410732032"
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> getProcessedEvent(events, event) }
            }
        }
    }

    private fun getProcessedEvent(events: SDKConfig.Events?, event: SentryEvent): SentryEvent? {
        val sentEvent = if ((event.throwable?.stackTraceToString()?.contains(BuildConfig.LIBRARY_PACKAGE_NAME, true) == true
                        && shouldHandle(events?.self ?: 100)) || (event.throwable?.stackTraceToString()?.contains("OutOfMemoryError", true) == true
                        && shouldHandle(events?.oom ?: 100))) {
            event
        } else {
            if (shouldHandle(events?.other ?: 0)) {
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

internal class ConfigSetWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val storeService = AndBeyondMedia.getStoreService(context)
        return try {
            val configService = AndBeyondMedia.getConfigService()
            val response = configService.getConfig(context.packageName).execute()
            if (response.isSuccessful && response.body() != null) {
                AndBeyondMedia.cachedConfig = response.body()
                storeService.setConfig(AndBeyondMedia.cachedConfig)
                Result.success()
            } else {
                storeService.getConfig {
                    AndBeyondMedia.cachedConfig = it
                }
                delay(50)
                if (AndBeyondMedia.cachedConfig == null) Result.failure() else Result.success()

            }
        } catch (e: Throwable) {
            Logger.ERROR.log(msg = e.message ?: "")
            storeService.getConfig {
                AndBeyondMedia.cachedConfig = it
            }
            delay(50)
            if (AndBeyondMedia.cachedConfig == null) Result.failure() else Result.success()
        }
    }
}

internal class CountryDetectionWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
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
                    countryService.getConfig(hashMapOf("accessKey" to "7ef45bac-167a-4aa8-8c99-bc8a28f80bc5", "fields" to "countryCode,latitude,longitude,city,regionCode,ip,postalCode")).execute()
                } else {
                    countryService.getConfig().execute()
                }
                if (response.isSuccessful && response.body() != null) {
                    AndBeyondMedia.cachedCountryConfig = response.body()
                    storeService.setDetectedCountry(AndBeyondMedia.cachedCountryConfig)
                    Result.success()
                } else {
                    storeService.getDetectedCountry {
                        AndBeyondMedia.cachedCountryConfig = it
                    }
                    delay(50)
                    if (AndBeyondMedia.cachedCountryConfig == null) Result.failure() else Result.success()
                }
            }
        } catch (e: Throwable) {
            Logger.ERROR.log(msg = e.message ?: "")
            storeService.getDetectedCountry {
                AndBeyondMedia.cachedCountryConfig = it
            }
            delay(50)
            if (AndBeyondMedia.cachedCountryConfig == null) Result.failure() else Result.success()
        }
    }
}

internal object SDKManager {

    fun initialize(context: Context, config: SDKConfig?) {
        initializeGAM(context)
        if (config == null || config.switch != 1) return
        initializePrebid(context, config.prebid)
        initializeGeoEdge(context, config.geoEdge?.apiKey)
        initializeAPS(context, config.aps)
        initializeOpenWrap(config.openWrapConfig)
    }

    private fun initializePrebid(context: Context, prebid: SDKConfig.Prebid?) = CoroutineScope(Dispatchers.Main).launch {
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
            AdRegistration.setMRAIDPolicy(MRAIDPolicy.CUSTOM)
            aps?.mRaidSupportedVersions?.let {
                AdRegistration.setMRAIDSupportedVersions(it.toTypedArray())
            }
            aps?.omidPartnerName?.let {
                AdRegistration.addCustomAttribute("omidPartnerName", it)
            }
            aps?.omidPartnerVersion?.let {
                AdRegistration.addCustomAttribute("omidPartnerVersion", it)
            }
        }
        if (aps?.delay == null || aps.delay.toIntOrNull() == 0) {
            init()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ init() }, aps.delay.toLongOrNull() ?: 1L)
        }
    }

    private fun initializeOpenWrap(owConfig: SDKConfig.OpenWrapConfig?) {
        if (owConfig?.playStoreUrl.isNullOrEmpty()) return
        val appInfo = POBApplicationInfo()
        try {
            appInfo.storeURL = URL(owConfig?.playStoreUrl ?: "")
        } catch (_: MalformedURLException) {
        }
        OpenWrapSDK.setApplicationInfo(appInfo)
    }
}

internal interface ConfigService {
    @GET("appconfig_{package}.js")
    fun getConfig(@Path("package") packageName: String): Call<SDKConfig>
}

internal interface CountryService {
    @GET("check")
    fun getConfig(@QueryMap params: HashMap<String, Any>): Call<CountryModel>

    @GET("maxmind.php")
    fun getConfig(): Call<CountryModel>
}

internal class StoreService(private val prefs: SharedPreferences, private val configFile: File, private val configCountryFile: File?) {

    fun getConfig(callback: (SDKConfig?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (AndBeyondMedia.cachedConfig == null) {
                AndBeyondMedia.cachedConfig = try {
                    if (configFile.exists()) {
                        val ois = ObjectInputStream(FileInputStream(configFile))
                        ois.readObject() as? SDKConfig
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            if (AndBeyondMedia.cachedConfig == null) {
                AndBeyondMedia.cachedConfig = try {
                    val string = prefs.getString("CONFIG", "") ?: ""
                    if (string.isEmpty()) {
                        null
                    } else {
                        GsonBuilder().create().fromJson(string, SDKConfig::class.java)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            withContext(Dispatchers.Main) {
                callback(AndBeyondMedia.cachedConfig)
            }
        }
    }

    fun setConfig(sdkConfig: SDKConfig?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oos = ObjectOutputStream(FileOutputStream(configFile))
                oos.writeObject(sdkConfig)
                oos.flush()
                oos.close()
            } catch (_: Throwable) {
                prefs.edit().apply {
                    sdkConfig?.let { putString("CONFIG", Gson().toJson(it)) } ?: kotlin.run { remove("CONFIG") }
                }.apply()
            }
        }
    }

    fun getDetectedCountry(callback: (CountryModel?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (AndBeyondMedia.cachedCountryConfig == null) {
                AndBeyondMedia.cachedCountryConfig = try {
                    if (configCountryFile?.exists() == true) {
                        val ois = ObjectInputStream(FileInputStream(configCountryFile))
                        ois.readObject() as? CountryModel
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            if (AndBeyondMedia.cachedCountryConfig == null) {
                AndBeyondMedia.cachedCountryConfig = try {
                    val string = prefs.getString("COUNTRY", "") ?: ""
                    if (string.isEmpty()) {
                        null
                    } else {
                        GsonBuilder().create().fromJson(string, CountryModel::class.java)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            withContext(Dispatchers.Main) {
                callback(AndBeyondMedia.cachedCountryConfig)
            }
        }
    }

    fun setDetectedCountry(detectedCountry: CountryModel?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oos = ObjectOutputStream(FileOutputStream(configCountryFile))
                oos.writeObject(detectedCountry)
                oos.flush()
                oos.close()
            } catch (_: Throwable) {
                prefs.edit().apply {
                    detectedCountry?.let { putString("COUNTRY", Gson().toJson(it)) } ?: kotlin.run { remove("COUNTRY") }
                }.apply()
            }
        }
    }

    var lastInterstitial: Long
        get() = prefs.getLong("INTER_TIME", 0L)
        set(value) = prefs.edit().putLong("INTER_TIME", value).apply()
}

internal class EventHandler(private val storeService: StoreService, private val defaultHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        storeService.getConfig { config ->
            if (exception.stackTraceToString().contains(BuildConfig.LIBRARY_PACKAGE_NAME, true) && shouldHandle(config?.events?.self ?: 100)) {
                Sentry.captureException(exception)
                exitProcess(0)
            } else if (exception.stackTraceToString().contains("OutOfMemoryError", true) && shouldHandle(config?.events?.oom ?: 100)) {
                Sentry.captureException(exception)
                exitProcess(0)
            } else {
                if (shouldHandle(config?.events?.other ?: 0)) {
                    Sentry.captureException(exception)
                    exitProcess(0)
                } else {
                    defaultHandler?.uncaughtException(thread, exception)
                }
            }
        }
    }

}