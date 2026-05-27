package com.kawa.energy.counter.monitor

import com.kawa.energy.counter.domain.PowerReading
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Polls a local LibreHardwareMonitor instance for live power sensors.
 *
 * Enable the LHM web server: Options → Remote Web Server → Run (default :8085).
 *
 * Preference order when emitting a sample:
 *  1. A PSU-level sensor if reported (Corsair / NZXT / Seasonic / EVGA telemetry).
 *  2. Sum of GPU + CPU package power sensors.
 *  3. Any other "Power" sensors as a last-resort sum.
 */
class LhmPowerMonitor(
    private val config: MonitorConfig,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : PowerMonitor {

    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 3_000
            connectTimeoutMillis = 1_500
        }
    }

    private val _readings = MutableSharedFlow<PowerReading>(replay = 1, extraBufferCapacity = 64)
    override val readings = _readings.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                runCatching {
                    val root: LhmNode = client.get(endpoint).body()
                    val sensors = mutableListOf<PowerSensor>()
                    walk(root, sensors)
                    if (sensors.isNotEmpty()) {
                        val agg = aggregate(sensors)
                        _readings.emit(
                            PowerReading(
                                timestampMs = System.currentTimeMillis(),
                                watts = agg.watts,
                                source = PowerReading.Source.COMBINED,
                                sourceLabel = agg.label,
                                sourceDetail = agg.detail,
                            )
                        )
                    }
                }
                delay(config.samplePeriodMs)
            }
        }
    }

    override fun stop() {
        loop?.cancel()
        loop = null
    }

    companion object {
        const val DEFAULT_ENDPOINT = "http://localhost:8085/data.json"

        /** Blocking probe — returns true when LHM responds within the timeout. */
        fun isAvailable(endpoint: String = DEFAULT_ENDPOINT, timeoutMs: Long = 1_500): Boolean {
            val probe = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = timeoutMs
                    connectTimeoutMillis = timeoutMs
                }
            }
            return try {
                runBlocking {
                    withTimeoutOrNull(timeoutMs) {
                        runCatching { probe.get(endpoint).status.value in 200..299 }.getOrDefault(false)
                    } ?: false
                }
            } finally {
                probe.close()
            }
        }
    }
}

private data class PowerSensor(
    val name: String,
    val parentPath: String,
    val watts: Double,
) {
    private val combined = "$parentPath / $name".lowercase()
    val isPsuVendor: Boolean = PSU_VENDOR_KEYWORDS.any { it in combined }
    val isExplicitPsuTotal: Boolean =
        name.equals("Power Total", ignoreCase = true) ||
            name.equals("Total Power", ignoreCase = true) ||
            name.equals("PSU Power", ignoreCase = true) ||
            name.equals("Power", ignoreCase = true) && isPsuVendor
    val isCpuPackage: Boolean = name.contains("Package", ignoreCase = true) ||
        name.contains("CPU Package", ignoreCase = true)
    val isDram: Boolean = name.contains("DRAM", ignoreCase = true) ||
        name.contains("Memory", ignoreCase = true)
    val isGpu: Boolean = name.contains("GPU Power", ignoreCase = true) ||
        (parentPath.contains("GeForce", ignoreCase = true) ||
            parentPath.contains("Radeon", ignoreCase = true) ||
            parentPath.contains("NVIDIA", ignoreCase = true)) &&
            !name.contains("Memory", ignoreCase = true)
}

private val PSU_VENDOR_KEYWORDS = listOf(
    "corsair", "nzxt", "evga", "seasonic", "msi psu", "asus psu", "asrock psu",
    "power supply", "psu",
)

private data class Aggregate(val watts: Double, val label: String, val detail: String)

private fun aggregate(sensors: List<PowerSensor>): Aggregate {
    // 1) Prefer real PSU telemetry. Multiple rails are exposed; pick the one
    //    that represents total (explicit "Total" / "Power Total"), else the
    //    largest power sensor under the PSU vendor node.
    val psuSensors = sensors.filter { it.isPsuVendor }
    if (psuSensors.isNotEmpty()) {
        val total = psuSensors.firstOrNull { it.isExplicitPsuTotal }
            ?: psuSensors.maxByOrNull { it.watts }
        if (total != null) {
            val vendor = total.parentPath.substringAfterLast("/").trim()
                .ifBlank { total.parentPath.ifBlank { total.name } }
            return Aggregate(
                watts = total.watts,
                label = "PSU telemetry · $vendor (LHM)",
                detail = "${total.watts.format1()} W reported by ${total.name}",
            )
        }
    }

    // 2) Sum CPU package + GPU + DRAM — the most accurate component-level read.
    val cpu = sensors.firstOrNull { it.isCpuPackage }
    val gpu = sensors.firstOrNull { it.isGpu }
    val dram = sensors.firstOrNull { it.isDram }
    if (cpu != null || gpu != null) {
        val total = (cpu?.watts ?: 0.0) + (gpu?.watts ?: 0.0) + (dram?.watts ?: 0.0)
        val parts = buildList {
            cpu?.let { add("CPU ${it.watts.format1()} W") }
            gpu?.let { add("GPU ${it.watts.format1()} W") }
            dram?.let { add("DRAM ${it.watts.format1()} W") }
        }
        return Aggregate(
            watts = total,
            label = "LHM components (CPU + GPU${dram?.let { " + DRAM" } ?: ""})",
            detail = parts.joinToString(" + "),
        )
    }

    // 3) Last resort: sum everything LHM reports as Power.
    val total = sensors.sumOf { it.watts }
    return Aggregate(
        watts = total,
        label = "LHM (sum of ${sensors.size} power sensors)",
        detail = sensors.joinToString(" + ") { "${it.name} ${it.watts.format1()} W" }
            .take(180),
    )
}

private fun walk(node: LhmNode, out: MutableList<PowerSensor>, ancestors: String = "") {
    val pathHere = if (ancestors.isBlank()) node.text else "$ancestors / ${node.text}"
    if (node.type.equals("Power", ignoreCase = true) && node.value.isNotBlank()) {
        parseWatts(node.value)?.let { w ->
            out += PowerSensor(
                name = node.text,
                parentPath = ancestors,
                watts = w,
            )
        }
    }
    node.children.forEach { walk(it, out, pathHere) }
}

private fun parseWatts(value: String): Double? {
    val cleaned = value.trim()
        .removeSuffix("W")
        .removeSuffix("w")
        .trim()
        .replace(',', '.')
    return cleaned.toDoubleOrNull()
}

private fun Double.format1(): String {
    val r = kotlin.math.round(this * 10.0) / 10.0
    return r.toString()
}

@Serializable
private data class LhmNode(
    @SerialName("id") val id: Int = 0,
    @SerialName("Text") val text: String = "",
    @SerialName("Children") val children: List<LhmNode> = emptyList(),
    @SerialName("Value") val value: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("SensorId") val sensorId: String = "",
)
