package com.kawa.energy.counter.data.monitor

import android.content.Context

object BatteryCapacity {
    private const val DEFAULT_CAPACITY_MAH = 4000

    fun autoDetectMah(context: Context): Int {
        return runCatching {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val instance = powerProfileClass
                .getConstructor(Context::class.java)
                .newInstance(context)
            val capacity = powerProfileClass
                .getMethod("getBatteryCapacity")
                .invoke(instance) as Double
            capacity.toInt().takeIf { it > 0 } ?: DEFAULT_CAPACITY_MAH
        }.getOrDefault(DEFAULT_CAPACITY_MAH)
    }
}
