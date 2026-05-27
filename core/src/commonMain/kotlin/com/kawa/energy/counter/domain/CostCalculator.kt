package com.kawa.energy.counter.domain

object CostCalculator {
    fun cost(kwh: Double, rate: ElectricityRate): Double =
        kwh * rate.pricePerKwh

    fun batteryChargeKwh(mahCharged: Int, batteryVoltageV: Double = 3.85): Double =
        (mahCharged.toDouble() / 1000.0) * batteryVoltageV / 1000.0
}
