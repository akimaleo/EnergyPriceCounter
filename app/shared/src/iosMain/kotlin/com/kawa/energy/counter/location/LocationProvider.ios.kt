package com.kawa.energy.counter.location

actual fun createLocationProvider(): LocationProvider = object : LocationProvider {
    override suspend fun detect(): LocationResult = LocationResult.Unavailable
}
