package com.kawa.energy.counter.location

data class LocationInfo(
    val countryCode: String,
    val label: String,
    val provider: String = "",
)

sealed class LocationResult {
    data class Ok(val info: LocationInfo) : LocationResult()
    data object PermissionDenied : LocationResult()
    data object Unavailable : LocationResult()
    data class Failure(val message: String) : LocationResult()
}

interface LocationProvider {
    suspend fun detect(): LocationResult
}

expect fun createLocationProvider(): LocationProvider
