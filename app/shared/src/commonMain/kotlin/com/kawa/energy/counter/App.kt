package com.kawa.energy.counter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawa.energy.counter.ui.AnimatedNumber
import com.kawa.energy.counter.ui.EnergyHistoryChart
import com.kawa.energy.counter.ui.EnergyUiState
import com.kawa.energy.counter.ui.EnergyViewModel
import com.kawa.energy.counter.ui.LocationStatus
import com.kawa.energy.counter.ui.PriceSource
import com.kawa.energy.counter.ui.Sparkline
import com.kawa.energy.counter.ui.SpeedometerGauge
import com.kawa.energy.counter.ui.WorldMap

private val DashboardScheme = darkColorScheme(
    primary = Color(0xFF7BD389),
    onPrimary = Color(0xFF003913),
    tertiary = Color(0xFFFFB454),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1F25),
    surfaceVariant = Color(0xFF252B33),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFB4BAC4),
    outline = Color(0xFF445164),
    outlineVariant = Color(0xFF2A323D),
)

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = DashboardScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val vm: EnergyViewModel = viewModel { EnergyViewModel() }
            val state by vm.state.collectAsState()
            val platform = remember { getPlatform() }

            LaunchedEffect(Unit) {
                vm.detectLocation()
                vm.start()
            }

            val density = LocalDensity.current
            val maxHeaderHeightDp = 480.dp
            val minHeaderHeightDp = 64.dp
            val maxPx = with(density) { maxHeaderHeightDp.toPx() }
            val minPx = with(density) { minHeaderHeightDp.toPx() }
            var headerOffsetPx by remember { mutableFloatStateOf(0f) } // -(maxPx-minPx)..0

            val nestedScroll = remember(maxPx, minPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        val newOffset = (headerOffsetPx + delta).coerceIn(-(maxPx - minPx), 0f)
                        val consumed = newOffset - headerOffsetPx
                        headerOffsetPx = newOffset
                        return Offset(0f, consumed)
                    }
                }
            }
            val expandFraction = ((maxPx + headerOffsetPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)
            val currentHeaderDp = with(density) { (maxPx + headerOffsetPx).toDp() }

            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScroll),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding()
                            .padding(top = currentHeaderDp)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryMetrics(state)
                        PriceSourceSection(state, vm)
                        if (platform.isDesktop) DesktopDataSourceCard(state, vm)
                        HistorySection(state, vm)
                        state.errorMessage?.let { ErrorPill(it) }
                        Spacer(Modifier.height(24.dp))
                    }

                    CollapsingPowerHeader(
                        state = state,
                        onStart = { vm.start() },
                        onStop = { vm.stop() },
                        heightDp = currentHeaderDp,
                        expandFraction = expandFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsingPowerHeader(
    state: EnergyUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    heightDp: androidx.compose.ui.unit.Dp,
    expandFraction: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = if (expandFraction < 0.95f) 6.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 64.dp)
            .height(heightDp)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Expanded content: large header + gauge + actions
            if (expandFraction > 0.05f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .alpha(expandFraction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(Modifier.height(12.dp))
                    Header(state)
                    val max = chooseGaugeMax(state.watts)
                    val gaugeSize = (150 + (70 * expandFraction)).toInt()
                    SpeedometerGauge(
                        value = state.watts,
                        min = 0.0,
                        max = max,
                        unit = "Watts",
                        label = state.rate?.source?.let { "Live · $it" } ?: "Current power draw",
                        sizeDp = gaugeSize,
                    )
                    HeaderTrendStrip(state, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.running) {
                            OutlinedButton(onClick = onStop) { Text("Stop session") }
                        } else {
                            Button(
                                onClick = onStart,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) { Text("Start session") }
                        }
                    }
                }
            }
            // Collapsed content: compact bar
            if (expandFraction < 0.5f) {
                CollapsedHeaderBar(
                    state = state,
                    onStart = onStart,
                    onStop = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(1f - (expandFraction / 0.5f).coerceIn(0f, 1f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun CollapsedHeaderBar(
    state: EnergyUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusBadge(state.running)
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedNumber(
                    value = state.watts,
                    digits = 1,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "W",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                state.rate?.source ?: "Power draw",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        CollapsedSparkPair(state)
        if (state.running) {
            TextButton(onClick = onStop) { Text("Stop") }
        } else {
            TextButton(onClick = onStart) { Text("Start") }
        }
    }
}

@Composable
private fun HeaderTrendStrip(state: EnergyUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrendCard(
            modifier = Modifier.weight(1f),
            label = "Usage",
            value = state.kwh,
            digits = 4,
            unit = "kWh",
            color = MaterialTheme.colorScheme.primary,
            select = { it.kwh },
            samples = state.history,
        )
        TrendCard(
            modifier = Modifier.weight(1f),
            label = "Total cost",
            value = state.cost,
            digits = 4,
            unit = state.currency,
            color = MaterialTheme.colorScheme.error,
            select = { it.costInCurrency },
            samples = state.history,
        )
    }
}

@Composable
private fun TrendCard(
    label: String,
    value: Double,
    digits: Int,
    unit: String,
    color: Color,
    select: (com.kawa.energy.counter.history.SessionSample) -> Double,
    samples: List<com.kawa.energy.counter.history.SessionSample>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedNumber(
                value = value,
                digits = digits,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Sparkline(
                samples = samples,
                select = select,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                strokeWidth = 2.5f,
            )
        }
    }
}

@Composable
private fun CollapsedSparkPair(state: EnergyUiState) {
    if (state.history.size < 2) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "kWh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Sparkline(
                samples = state.history,
                select = { it.kwh },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(64.dp).height(20.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                state.currency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Sparkline(
                samples = state.history,
                select = { it.costInCurrency },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(64.dp).height(20.dp),
            )
        }
    }
}

@Composable
private fun Header(state: EnergyUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "EnergyCounter",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Live power & electricity cost",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(state.running)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPill(
                icon = "📍",
                label = locationPillLabel(state),
                accent = MaterialTheme.colorScheme.tertiary,
            )
            HeaderPill(
                icon = "⚡",
                label = "12h · ${roundKwh(recentUsageKwh(state.history, 12 * 3_600_000L))} kWh",
                accent = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeaderPill(icon: String, label: String, accent: Color) {
    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun locationPillLabel(state: EnergyUiState): String {
    val loc = state.location
    return when {
        loc == null -> "Detecting location…"
        loc.label.isNotBlank() && loc.label != loc.countryCode ->
            "${loc.countryCode} · ${loc.label}"
        else -> loc.countryCode
    }
}

/**
 * Sum of kWh consumed in the last [windowMs] milliseconds across all sessions.
 * Each session's kWh column is monotonically increasing from 0, so the per-session
 * contribution is the delta between the latest and earliest sample within the window;
 * a new session is recognised by a timestamp gap > 15s or a kWh reset to a smaller value.
 */
private fun recentUsageKwh(samples: List<com.kawa.energy.counter.history.SessionSample>, windowMs: Long): Double {
    if (samples.isEmpty()) return 0.0
    // platform-agnostic "now" — use the last persisted timestamp as the upper bound
    // so we don't depend on Clock.System availability in commonMain.
    val now = samples.last().timestampMs
    val cutoff = now - windowMs
    val window = samples.filter { it.timestampMs >= cutoff }
    if (window.isEmpty()) return 0.0

    var total = 0.0
    var segmentStartKwh = window.first().kwh
    var segmentLastKwh = window.first().kwh
    var prevTs = window.first().timestampMs

    for (i in 1 until window.size) {
        val s = window[i]
        val gap = s.timestampMs - prevTs
        val sessionReset = s.kwh < segmentLastKwh - 1e-9
        if (gap > 15_000L || sessionReset) {
            total += segmentLastKwh - segmentStartKwh
            segmentStartKwh = s.kwh
        }
        segmentLastKwh = s.kwh
        prevTs = s.timestampMs
    }
    total += segmentLastKwh - segmentStartKwh
    return total
}

private fun roundKwh(v: Double): String {
    val r = kotlin.math.round(v * 10000.0) / 10000.0
    val whole = r.toLong()
    val frac = (kotlin.math.round((r - whole) * 10000.0)).toLong()
        .toString().padStart(4, '0')
    return "$whole.$frac"
}

@Composable
private fun StatusBadge(running: Boolean) {
    val bg = if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (running) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            if (running) "● live" else "○ idle",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryMetrics(state: EnergyUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TilePanel(
            modifier = Modifier.weight(1f),
            title = "Energy",
            value = state.kwh,
            digits = 6,
            unit = "kWh",
            accent = MaterialTheme.colorScheme.primary,
        )
        TilePanel(
            modifier = Modifier.weight(1f),
            title = "Rate",
            value = state.rate?.pricePerKwh,
            digits = 4,
            unit = "${state.currency}/kWh",
            accent = MaterialTheme.colorScheme.tertiary,
        )
        TilePanel(
            modifier = Modifier.weight(1f),
            title = "Cost",
            value = state.cost,
            digits = 4,
            unit = state.currency,
            accent = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TilePanel(
    title: String,
    value: Double?,
    digits: Int,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (value != null) {
                AnimatedNumber(value = value, digits = digits)
            } else {
                Text("—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriceSourceSection(state: EnergyUiState, vm: EnergyViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Price source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.priceSource == PriceSource.Auto,
                    onClick = { vm.setPriceSource(PriceSource.Auto) },
                    label = { Text("Auto (by location)") },
                )
                FilterChip(
                    selected = state.priceSource == PriceSource.Fixed,
                    onClick = { vm.setPriceSource(PriceSource.Fixed) },
                    label = { Text("Fixed") },
                )
            }
            AnimatedVisibility(state.priceSource == PriceSource.Auto) {
                LocationBlock(state, vm)
            }
            AnimatedVisibility(state.priceSource == PriceSource.Fixed) {
                var input by remember { mutableStateOf(state.fixedPricePerKwh.toString()) }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        it.toDoubleOrNull()?.let(vm::setFixedPrice)
                    },
                    label = { Text("Price per kWh (${state.currency})") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LocationBlock(state: EnergyUiState, vm: EnergyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val text = when (val s = state.locationStatus) {
            LocationStatus.Idle -> "Detecting on first launch…"
            LocationStatus.Detecting -> "Detecting…"
            is LocationStatus.Detected -> "${s.info.label} (${s.info.countryCode})"
            LocationStatus.PermissionDenied -> "Permission denied — grant location access"
            is LocationStatus.Failed -> "Failed: ${s.message}"
            is LocationStatus.Unsupported -> "No public price API for ${s.countryCode}"
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorldMap(
            selectedCountry = state.location?.countryCode,
            onCountryPicked = { vm.overrideCountry(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Tap a country on the map to override · supported ones are highlighted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.detectLocation() },
                enabled = state.locationStatus !is LocationStatus.Detecting,
            ) { Text("Detect again") }
            if (state.locationStatus is LocationStatus.Detecting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            }
        }
        var manual by remember { mutableStateOf(state.location?.countryCode.orEmpty()) }
        OutlinedTextField(
            value = manual,
            onValueChange = {
                val v = it.uppercase().take(2)
                manual = v
                if (v.length == 2) vm.overrideCountry(v)
            },
            label = { Text("Or type ISO code (e.g. NL, DE, FR)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DesktopDataSourceCard(state: EnergyUiState, vm: EnergyViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Data sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            KvRow(
                "Power sampling",
                state.powerSourceLabel ?: "OSHI CPU load × estimated TDP (LHM not detected)",
            )
            KvRow("Geolocation", state.location?.provider?.ifBlank { "—" } ?: "—")
            KvRow("Country resolved", state.location?.countryCode ?: "—")
            KvRow("Price API", state.rate?.source ?: "—")
            KvRow("Endpoint", state.rate?.endpoint ?: "—")

            LhmInstallControl(state, vm)

            Text(
                "Sessions are appended to ~/.energy-counter/sessions.ndjson",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LhmInstallControl(state: EnergyUiState, vm: EnergyViewModel) {
    val usingLhm = state.powerSourceLabel?.contains("LHM", ignoreCase = true) == true ||
        state.powerSourceLabel?.contains("PSU telemetry", ignoreCase = true) == true
    val installing = state.installStatus is com.kawa.energy.counter.ui.InstallStatus.InProgress

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.installPowerSource() },
                enabled = vm.isInstallerSupported && !installing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (usingLhm) "Reinstall LibreHardwareMonitor" else "Install LibreHardwareMonitor")
            }
            if (installing) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            }
        }

        when (val s = state.installStatus) {
            com.kawa.energy.counter.ui.InstallStatus.Idle -> Unit
            is com.kawa.energy.counter.ui.InstallStatus.InProgress -> Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is com.kawa.energy.counter.ui.InstallStatus.Done -> Text(
                "✓ ${s.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            is com.kawa.energy.counter.ui.InstallStatus.Failed -> Text(
                "✗ ${s.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            com.kawa.energy.counter.ui.InstallStatus.NotSupported -> Text(
                "Installer is only available on Windows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!vm.isInstallerSupported) {
            Text(
                "LibreHardwareMonitor is Windows-only. On other OSes, the OSHI CPU estimate is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistorySection(state: EnergyUiState, vm: EnergyViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "History · ${state.history.size} samples",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
            }
            EnergyHistoryChart(samples = state.history, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorPill(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun chooseGaugeMax(currentWatts: Double): Double {
    // Round up to a "nice" max — 50/100/200/400/600/1000/2000.
    val scales = listOf(50.0, 100.0, 200.0, 400.0, 600.0, 1000.0, 2000.0, 5000.0)
    val target = currentWatts * 1.3
    return scales.firstOrNull { it >= target } ?: scales.last()
}

