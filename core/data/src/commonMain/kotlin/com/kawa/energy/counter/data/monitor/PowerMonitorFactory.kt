package com.kawa.energy.counter.data.monitor

import com.kawa.energy.counter.domain.MonitorConfig
import com.kawa.energy.counter.domain.PowerMonitor

/** Platform factory that picks the best available power-telemetry source. */
expect fun createPowerMonitor(config: MonitorConfig = MonitorConfig()): PowerMonitor
