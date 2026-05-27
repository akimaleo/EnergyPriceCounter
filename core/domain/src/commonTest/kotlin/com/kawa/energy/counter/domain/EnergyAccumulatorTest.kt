package com.kawa.energy.counter.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class EnergyAccumulatorTest {

    @Test
    fun integratesPowerOverTime() {
        val acc = EnergyAccumulator()
        // Constant 100 W for one hour -> 0.1 kWh
        acc.add(PowerReading(0L, 100.0, PowerReading.Source.COMBINED))
        acc.add(PowerReading(3_600_000L, 100.0, PowerReading.Source.COMBINED))
        assertTrue(abs(acc.totalKwh - 0.1) < 1e-9, "expected 0.1 kWh, got ${acc.totalKwh}")
    }

    @Test
    fun batteryChargeKwh() {
        // 3000 mAh at 3.85 V ~ 0.01155 kWh
        val kwh = CostCalculator.batteryChargeKwh(3000)
        assertTrue(abs(kwh - 0.01155) < 1e-6, "got $kwh")
    }
}
