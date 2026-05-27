package com.kawa.energy.counter.domain

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

interface SessionStore {
    suspend fun append(sample: SessionSample)
    suspend fun loadAll(): List<SessionSample>
    suspend fun clear()
}
