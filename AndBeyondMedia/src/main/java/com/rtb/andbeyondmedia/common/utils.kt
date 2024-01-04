package com.rtb.andbeyondmedia.common

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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

@Suppress("DEPRECATION")
fun Context.getAddress(location: Location, callback: (Address?) -> Unit) {
    try {
        val geocoder = Geocoder(applicationContext, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1) { callback(it.getOrNull(0)) }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                withContext(Dispatchers.Main) { callback(addresses?.getOrNull(0)) }
            }
        }
    } catch (_: Throwable) {
        callback(null)
    }
}

@Keep
val countryMaps = HashMap<String, String>().apply {
    put("AF", "AFG")
    put("AL", "ALB")
    put("DZ", "DZA")
    put("AS", "ASM")
    put("AD", "AND")
    put("AO", "AGO")
    put("AI", "AIA")
    put("AQ", "ATA")
    put("AG", "ATG")
    put("AR", "ARG")
    put("AM", "ARM")
    put("AW", "ABW")
    put("AU", "AUS")
    put("AT", "AUT")
    put("AZ", "AZE")
    put("BS", "BHS")
    put("BH", "BHR")
    put("BD", "BGD")
    put("BB", "BRB")
    put("BY", "BLR")
    put("BE", "BEL")
    put("BZ", "BLZ")
    put("BJ", "BEN")
    put("BM", "BMU")
    put("BT", "BTN")
    put("BO", "BOL")
    put("BA", "BIH")
    put("BW", "BWA")
    put("BR", "BRA")
    put("IO", "IOT")
    put("VG", "VGB")
    put("BN", "BRN")
    put("BG", "BGR")
    put("BF", "BFA")
    put("BI", "BDI")
    put("KH", "KHM")
    put("CM", "CMR")
    put("CA", "CAN")
    put("CV", "CPV")
    put("KY", "CYM")
    put("CF", "CAF")
    put("TD", "TCD")
    put("CL", "CHL")
    put("CN", "CHN")
    put("CX", "CXR")
    put("CC", "CCK")
    put("CO", "COL")
    put("KM", "COM")
    put("CK", "COK")
    put("CR", "CRI")
    put("HR", "HRV")
    put("CU", "CUB")
    put("CW", "CUW")
    put("CY", "CYP")
    put("CZ", "CZE")
    put("CD", "COD")
    put("DK", "DNK")
    put("DJ", "DJI")
    put("DM", "DMA")
    put("DO", "DOM")
    put("TL", "TLS")
    put("EC", "ECU")
    put("EG", "EGY")
    put("SV", "SLV")
    put("GQ", "GNQ")
    put("ER", "ERI")
    put("EE", "EST")
    put("ET", "ETH")
    put("FK", "FLK")
    put("FO", "FRO")
    put("FJ", "FJI")
    put("FI", "FIN")
    put("FR", "FRA")
    put("PF", "PYF")
    put("GA", "GAB")
    put("GM", "GMB")
    put("GE", "GEO")
    put("DE", "DEU")
    put("GH", "GHA")
    put("GI", "GIB")
    put("GR", "GRC")
    put("GL", "GRL")
    put("GD", "GRD")
    put("GU", "GUM")
    put("GT", "GTM")
    put("GG", "GGY")
    put("GN", "GIN")
    put("GW", "GNB")
    put("GY", "GUY")
    put("HT", "HTI")
    put("HN", "HND")
    put("HK", "HKG")
    put("HU", "HUN")
    put("IS", "ISL")
    put("IN", "IND")
    put("ID", "IDN")
    put("IR", "IRN")
    put("IQ", "IRQ")
    put("IE", "IRL")
    put("IM", "IMN")
    put("IL", "ISR")
    put("IT", "ITA")
    put("CI", "CIV")
    put("JM", "JAM")
    put("JP", "JPN")
    put("JE", "JEY")
    put("JO", "JOR")
    put("KZ", "KAZ")
    put("KE", "KEN")
    put("KI", "KIR")
    put("XK", "XKX")
    put("KW", "KWT")
    put("KG", "KGZ")
    put("LA", "LAO")
    put("LV", "LVA")
    put("LB", "LBN")
    put("LS", "LSO")
    put("LR", "LBR")
    put("LY", "LBY")
    put("LI", "LIE")
    put("LT", "LTU")
    put("LU", "LUX")
    put("MO", "MAC")
    put("MK", "MKD")
    put("MG", "MDG")
    put("MW", "MWI")
    put("MY", "MYS")
    put("MV", "MDV")
    put("ML", "MLI")
    put("MT", "MLT")
    put("MH", "MHL")
    put("MR", "MRT")
    put("MU", "MUS")
    put("YT", "MYT")
    put("MX", "MEX")
    put("FM", "FSM")
    put("MD", "MDA")
    put("MC", "MCO")
    put("MN", "MNG")
    put("ME", "MNE")
    put("MS", "MSR")
    put("MA", "MAR")
    put("MZ", "MOZ")
    put("MM", "MMR")
    put("NA", "NAM")
    put("NR", "NRU")
    put("NP", "NPL")
    put("NL", "NLD")
    put("AN", "ANT")
    put("NC", "NCL")
    put("NZ", "NZL")
    put("NI", "NIC")
    put("NE", "NER")
    put("NG", "NGA")
    put("NU", "NIU")
    put("KP", "PRK")
    put("MP", "MNP")
    put("NO", "NOR")
    put("OM", "OMN")
    put("PK", "PAK")
    put("PW", "PLW")
    put("PS", "PSE")
    put("PA", "PAN")
    put("PG", "PNG")
    put("PY", "PRY")
    put("PE", "PER")
    put("PH", "PHL")
    put("PN", "PCN")
    put("PL", "POL")
    put("PT", "PRT")
    put("PR", "PRI")
    put("QA", "QAT")
    put("CG", "COG")
    put("RE", "REU")
    put("RO", "ROU")
    put("RU", "RUS")
    put("RW", "RWA")
    put("BL", "BLM")
    put("SH", "SHN")
    put("KN", "KNA")
    put("LC", "LCA")
    put("MF", "MAF")
    put("PM", "SPM")
    put("VC", "VCT")
    put("WS", "WSM")
    put("SM", "SMR")
    put("ST", "STP")
    put("SA", "SAU")
    put("SN", "SEN")
    put("RS", "SRB")
    put("SC", "SYC")
    put("SL", "SLE")
    put("SG", "SGP")
    put("SX", "SXM")
    put("SK", "SVK")
    put("SI", "SVN")
    put("SB", "SLB")
    put("SO", "SOM")
    put("ZA", "ZAF")
    put("KR", "KOR")
    put("SS", "SSD")
    put("ES", "ESP")
    put("LK", "LKA")
    put("SD", "SDN")
    put("SR", "SUR")
    put("SJ", "SJM")
    put("SZ", "SWZ")
    put("SE", "SWE")
    put("CH", "CHE")
    put("SY", "SYR")
    put("TW", "TWN")
    put("TJ", "TJK")
    put("TZ", "TZA")
    put("TH", "THA")
    put("TG", "TGO")
    put("TK", "TKL")
    put("TO", "TON")
    put("TT", "TTO")
    put("TN", "TUN")
    put("TR", "TUR")
    put("TM", "TKM")
    put("TC", "TCA")
    put("TV", "TUV")
    put("VI", "VIR")
    put("UG", "UGA")
    put("UA", "UKR")
    put("AE", "ARE")
    put("GB", "GBR")
    put("US", "USA")
    put("UY", "URY")
    put("UZ", "UZB")
    put("VU", "VUT")
    put("VA", "VAT")
    put("VE", "VEN")
    put("VN", "VNM")
    put("WF", "WLF")
    put("EH", "ESH")
    put("YE", "YEM")
    put("ZM", "ZMB")
    put("ZW", "ZWE")
}

fun getCountry(code: String): String {
    return if (code.isBlank()) ""
    else countryMaps[code.uppercase()] ?: ""
}