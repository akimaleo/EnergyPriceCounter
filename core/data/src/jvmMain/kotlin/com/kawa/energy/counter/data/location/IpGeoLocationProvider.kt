package com.kawa.energy.counter.data.location

import com.kawa.energy.counter.domain.LocationProvider
import com.kawa.energy.counter.domain.LocationInfo
import com.kawa.energy.counter.domain.LocationResult

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

actual fun createLocationProvider(): LocationProvider = IpGeoLocationProvider()

private class IpGeoLocationProvider : LocationProvider {

    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun detect(): LocationResult {
        val errors = mutableListOf<String>()

        runCatching { ipinfo() }.fold(
            onSuccess = { it?.let { return LocationResult.Ok(it) } },
            onFailure = { errors += "ipinfo.io: ${it.message}" },
        )
        runCatching { countryIs() }.fold(
            onSuccess = { it?.let { return LocationResult.Ok(it) } },
            onFailure = { errors += "api.country.is: ${it.message}" },
        )

        return LocationResult.Failure(
            if (errors.isEmpty()) "Geolocation returned no usable data"
            else errors.joinToString("; ")
        )
    }

    private suspend fun ipinfo(): LocationInfo? {
        val r: IpInfoResponse = client.get("https://ipinfo.io/json") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }.body()
        val code = r.country?.takeIf { it.length == 2 } ?: return null
        val label = listOfNotNull(r.city, r.country).joinToString(", ").ifBlank { code }
        return LocationInfo(code.uppercase(), label, provider = "ipinfo.io")
    }

    private suspend fun countryIs(): LocationInfo? {
        val r: CountryIsResponse = client.get("https://api.country.is/") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }.body()
        val code = r.country?.takeIf { it.length == 2 } ?: return null
        return LocationInfo(code.uppercase(), code.uppercase(), provider = "api.country.is")
    }

    companion object {
        private const val USER_AGENT = "EnergyCounter/1.0"
    }
}

@Serializable
private data class IpInfoResponse(
    val country: String? = null,
    val city: String? = null,
    val region: String? = null,
)

@Serializable
private data class CountryIsResponse(
    val ip: String? = null,
    val country: String? = null,
)
