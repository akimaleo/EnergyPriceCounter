package com.kawa.energy.counter.data.location

import com.kawa.energy.counter.domain.LocationProvider
import com.kawa.energy.counter.domain.LocationResult

actual fun createLocationProvider(): LocationProvider = object : LocationProvider {
    override suspend fun detect(): LocationResult = LocationResult.Unavailable
}
