package com.kawa.energy.counter.history

import kotlinx.serialization.Serializable

@Serializable
data class SessionSample(
    val timestampMs: Long,
    val kwh: Double,
    val pricePerKwh: Double,
    val currency: String,
    val costInCurrency: Double,
    val countryCode: String? = null,
)
