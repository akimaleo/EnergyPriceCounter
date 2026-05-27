package com.kawa.energy.counter.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import kotlin.math.abs

actual fun createPowerMonitor(config: MonitorConfig): PowerMonitor {
    val context = AndroidContextHolder.require()
    return AndroidBatteryMonitor(context, config)
}

private class AndroidBatteryMonitor(
    private val context: Context,
    private val config: MonitorConfig,
) : PowerMonitor {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    val capacityMah: Int =
        config.batteryCapacityMahOverride ?: BatteryCapacity.autoDetectMah(context)

    private val _readings = MutableSharedFlow<PowerReading>(
        replay = 1,
        extraBufferCapacity = 64,
    )
    override val readings = _readings.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var lastChargeCounter: Long? = null
    private var lastSampleMs: Long = 0L

    @Volatile
    private var isCharging: Boolean = false

    @Volatile
    private var voltageMv: Int = 3850

    override fun start() {
        if (loop?.isActive == true) return
        registerBatteryReceiver()
        lastChargeCounter = readChargeCounterUah()
        lastSampleMs = System.currentTimeMillis()
        loop = scope.launch {
            while (true) {
                delay(config.samplePeriodMs)
                emitSample()
            }
        }
    }

    override fun stop() {
        loop?.cancel()
        loop = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val v = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                if (v > 0) voltageMv = v
            }
        }
        receiver = r
        context.registerReceiver(r, filter)
    }

    private fun readChargeCounterUah(): Long? {
        // CHARGE_COUNTER returns remaining charge in µAh on most devices.
        val value = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return value.takeIf { it != Long.MIN_VALUE && it != 0L }
    }

    private suspend fun emitSample() {
        val nowMs = System.currentTimeMillis()
        val currentUah = readChargeCounterUah() ?: return
        val prevUah = lastChargeCounter
        val dtMs = nowMs - lastSampleMs

        lastChargeCounter = currentUah
        lastSampleMs = nowMs

        if (prevUah == null || dtMs <= 0) return

        // Positive delta while charging means energy was added to the battery.
        val deltaUah = currentUah - prevUah
        val voltageV = voltageMv / 1000.0
        val label = "BatteryManager.CHARGE_COUNTER · ${capacityMah} mAh capacity"

        if (!isCharging || deltaUah <= 0) {
            _readings.emit(
                PowerReading(
                    timestampMs = nowMs,
                    watts = 0.0,
                    source = PowerReading.Source.BATTERY_CHARGE,
                    sourceLabel = label,
                    sourceDetail = "not charging · V ${voltageV.format2dp()}",
                )
            )
            return
        }

        val deltaMah = deltaUah / 1000.0
        val deltaHours = dtMs / 3_600_000.0
        val watts = if (deltaHours > 0) (deltaMah / 1000.0) * voltageV / deltaHours else 0.0

        _readings.emit(
            PowerReading(
                timestampMs = nowMs,
                watts = abs(watts),
                source = PowerReading.Source.BATTERY_CHARGE,
                sourceLabel = label,
                sourceDetail = "Δ ${deltaMah.format2dp()} mAh in ${dtMs / 1000} s · V ${voltageV.format2dp()} · charging",
            )
        )
    }

    private fun Double.format2dp(): String {
        val r = kotlin.math.round(this * 100.0) / 100.0
        return r.toString()
    }
}
