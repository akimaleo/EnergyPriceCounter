package com.kawa.energy.counter.monitor

import com.kawa.energy.counter.domain.PowerReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import oshi.SystemInfo
import oshi.hardware.CentralProcessor

actual fun createPowerMonitor(config: MonitorConfig): PowerMonitor =
    if (LhmPowerMonitor.isAvailable()) LhmPowerMonitor(config) else JvmPowerMonitor(config)

private class JvmPowerMonitor(
    private val config: MonitorConfig,
) : PowerMonitor {

    private val systemInfo = SystemInfo()
    private val processor: CentralProcessor = systemInfo.hardware.processor
    private val sensors = systemInfo.hardware.sensors

    private val _readings = MutableSharedFlow<PowerReading>(
        replay = 1,
        extraBufferCapacity = 64,
    )
    override val readings = _readings.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private var prevTicks: LongArray = processor.systemCpuLoadTicks
    private val cpuTdpWatts: Double = estimateTdpWatts(processor)
    private val cpuName: String = runCatching {
        processor.processorIdentifier.name.trim()
    }.getOrDefault("CPU")
    private val logicalCores: Int = processor.logicalProcessorCount

    override fun start() {
        if (loop?.isActive == true) return
        prevTicks = processor.systemCpuLoadTicks
        loop = scope.launch {
            while (true) {
                delay(config.samplePeriodMs)
                val sample = sample()
                _readings.emit(
                    PowerReading(
                        timestampMs = System.currentTimeMillis(),
                        watts = sample.watts,
                        source = PowerReading.Source.CPU,
                        sourceLabel = "$cpuName · $logicalCores threads (OSHI)",
                        sourceDetail = sample.detail,
                    )
                )
            }
        }
    }

    override fun stop() {
        loop?.cancel()
        loop = null
    }

    private data class Sample(val watts: Double, val detail: String)

    private fun sample(): Sample {
        val ticks = processor.systemCpuLoadTicks
        val load = processor.getSystemCpuLoadBetweenTicks(prevTicks).coerceIn(0.0, 1.0)
        prevTicks = ticks
        val watts = load * cpuTdpWatts
        val voltage = runCatching { sensors.cpuVoltage }.getOrDefault(0.0)
        val temp = runCatching { sensors.cpuTemperature }.getOrDefault(0.0)
        val parts = buildList {
            add("load ${(load * 100.0).round1()}%")
            add("TDP ${cpuTdpWatts.toInt()} W")
            if (voltage > 0.0) add("V ${voltage.round2()}")
            if (temp > 0.0) add("T ${temp.round1()}°C")
        }
        return Sample(watts, parts.joinToString(" · "))
    }

    private fun Double.round1(): String {
        val r = kotlin.math.round(this * 10.0) / 10.0
        return r.toString()
    }
    private fun Double.round2(): String {
        val r = kotlin.math.round(this * 100.0) / 100.0
        return r.toString()
    }

    private fun estimateTdpWatts(cpu: CentralProcessor): Double {
        val logical = cpu.logicalProcessorCount
        // Coarse heuristic: 5 W per logical core, clamped to 15..250 W.
        return (logical * 5.0).coerceIn(15.0, 250.0)
    }
}
