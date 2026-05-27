package com.kawa.energy.counter.domain

data class PowerReading(
    val timestampMs: Long,
    val watts: Double,
    val source: Source,
    val sourceLabel: String? = null,
    val sourceDetail: String? = null,
) {
    enum class Source { CPU, GPU, COMBINED, BATTERY_CHARGE }
}
