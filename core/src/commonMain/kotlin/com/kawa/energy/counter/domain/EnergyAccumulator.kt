package com.kawa.energy.counter.domain

class EnergyAccumulator {
    private var lastTimestampMs: Long? = null
    private var lastWatts: Double = 0.0

    var totalKwh: Double = 0.0
        private set

    fun add(reading: PowerReading) {
        val prev = lastTimestampMs
        if (prev != null) {
            val deltaHours = (reading.timestampMs - prev) / 3_600_000.0
            if (deltaHours > 0) {
                val avgWatts = (lastWatts + reading.watts) / 2.0
                totalKwh += (avgWatts * deltaHours) / 1000.0
            }
        }
        lastTimestampMs = reading.timestampMs
        lastWatts = reading.watts
    }

    fun reset() {
        lastTimestampMs = null
        lastWatts = 0.0
        totalKwh = 0.0
    }
}
