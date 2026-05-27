package com.kawa.energy.counter.domain

import kotlinx.coroutines.flow.Flow

/** Source-agnostic stream of instantaneous power readings. */
interface PowerMonitor {
    val readings: Flow<PowerReading>
    fun start()
    fun stop()
}

/** Configuration knobs forwarded to platform monitor implementations. */
data class MonitorConfig(
    val samplePeriodMs: Long = 1_000L,
    val batteryCapacityMahOverride: Int? = null,
)
