package com.kawa.energy.counter.monitor

import com.kawa.energy.counter.domain.PowerReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual fun createPowerMonitor(config: MonitorConfig): PowerMonitor = IosStubMonitor()

private class IosStubMonitor : PowerMonitor {
    private val flow = MutableSharedFlow<PowerReading>(replay = 1)
    override val readings = flow.asSharedFlow()
    override fun start() = Unit
    override fun stop() = Unit
}
