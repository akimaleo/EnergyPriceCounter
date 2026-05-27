package com.kawa.energy.counter.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.kawa.energy.counter.monitor.AndroidContextHolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.coroutines.resume

actual fun createLocationProvider(): LocationProvider =
    AndroidLocationProvider(AndroidContextHolder.require())

private class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {

    private val http: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun detect(): LocationResult {
        // 1. GPS / network if user granted permission.
        if (hasLocationPermission()) {
            val gps = tryGps()
            if (gps != null) return LocationResult.Ok(gps)
        }
        // 2. SIM / network country (no permission required, works offline).
        simCountry()?.let { return LocationResult.Ok(it) }
        // 3. IP geolocation (needs INTERNET only).
        ipLookup()?.let { return LocationResult.Ok(it) }

        return if (!hasLocationPermission()) LocationResult.PermissionDenied
        else LocationResult.Unavailable
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private suspend fun tryGps(): LocationInfo? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val cached = providers.firstNotNullOfOrNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }
        val location = cached ?: providers.firstOrNull()?.let { p ->
            withTimeoutOrNull(10_000L) { requestSingleUpdate(lm, p) }
        } ?: return null

        return reverseGeocode(location)
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }
            }
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun reverseGeocode(location: Location): LocationInfo? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val matches = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val a = matches?.firstOrNull() ?: return@withContext null
            val cc = a.countryCode?.uppercase() ?: return@withContext null
            val label = listOfNotNull(a.locality, a.countryName).joinToString(", ").ifBlank { cc }
            LocationInfo(countryCode = cc, label = label, provider = "GPS + Geocoder")
        }.getOrNull()
    }

    private fun simCountry(): LocationInfo? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val cc = (tm.networkCountryIso ?: tm.simCountryIso)?.takeIf { it.length == 2 }
            ?: return null
        return LocationInfo(
            countryCode = cc.uppercase(),
            label = cc.uppercase(),
            provider = "SIM / network",
        )
    }

    private suspend fun ipLookup(): LocationInfo? {
        runCatching {
            val r: IpInfoResponse = http.get("https://ipinfo.io/json") {
                header(HttpHeaders.UserAgent, "EnergyCounter/1.0")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            val code = r.country?.takeIf { it.length == 2 }
            if (code != null) {
                val label = listOfNotNull(r.city, r.country).joinToString(", ").ifBlank { code }
                return LocationInfo(code.uppercase(), label, provider = "ipinfo.io")
            }
        }
        runCatching {
            val r: CountryIsResponse = http.get("https://api.country.is/") {
                header(HttpHeaders.UserAgent, "EnergyCounter/1.0")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            val code = r.country?.takeIf { it.length == 2 }
            if (code != null) return LocationInfo(code.uppercase(), code.uppercase(), provider = "api.country.is")
        }
        return null
    }
}

@Serializable
private data class IpInfoResponse(
    val country: String? = null,
    val city: String? = null,
)

@Serializable
private data class CountryIsResponse(
    val country: String? = null,
)
