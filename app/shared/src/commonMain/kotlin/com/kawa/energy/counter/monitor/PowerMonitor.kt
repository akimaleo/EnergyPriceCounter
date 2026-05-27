package com.kawa.energy.counter.monitor

import com.kawa.energy.counter.domain.PowerReading
import kotlinx.coroutines.flow.Flow

interface PowerMonitor {
    val readings: Flow<PowerReading>
    fun start()
    fun stop()
}

expect fun createPowerMonitor(config: MonitorConfig = MonitorConfig()): PowerMonitor

data class MonitorConfig(
    val samplePeriodMs: Long = 1_000L,
    val batteryCapacityMahOverride: Int? = null,
)
