package com.kawa.energy.counter.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.LocationInfo
import com.kawa.energy.counter.domain.SessionSample

/** Renders the whole dashboard with realistic mock data and a no-op action surface. */
@Composable
@Preview
fun DashboardScreenPreview() {
    DashboardScreen(
        state = previewState(),
        actions = NoOpDashboardActions,
        isDesktop = true,
    )
}

/** Same content but as it appears on the Android target (no Desktop data sources card). */
@Composable
@Preview
fun DashboardScreenMobilePreview() {
    DashboardScreen(
        state = previewState(),
        actions = NoOpDashboardActions,
        isDesktop = false,
    )
}

/** Idle state — nothing running, no rate fetched, history empty. */
@Composable
@Preview
fun DashboardScreenIdlePreview() {
    DashboardScreen(
        state = EnergyUiState(
            running = false,
            locationStatus = LocationStatus.Idle,
        ),
        actions = NoOpDashboardActions,
        isDesktop = true,
    )
}

/** Fixed-price mode preview — shows the manual-rate input instead of the world map. */
@Composable
@Preview
fun DashboardScreenFixedPricePreview() {
    DashboardScreen(
        state = previewState().copy(
            priceSource = PriceSource.Fixed,
            rate = ElectricityRate(0.30, "EUR", "manual"),
        ),
        actions = NoOpDashboardActions,
        isDesktop = true,
    )
}

private object NoOpDashboardActions : DashboardActions {
    override val isInstallerSupported: Boolean get() = true
}

private fun previewState(): EnergyUiState {
    val now = 1_700_000_000_000L
    val rate = ElectricityRate(
        pricePerKwh = 0.2643,
        currency = "EUR",
        source = "energy-charts:NL",
        endpoint = "https://api.energy-charts.info/price?bzn=NL",
    )
    val location = LocationInfo(
        countryCode = "NL",
        label = "Utrecht, Netherlands",
        provider = "ipinfo.io",
    )
    // Two sessions in the last 12 h: a steady ramp, a gap, then a second ramp.
    val history = buildList {
        repeat(36) { i ->
            val ts = now - 11 * 3_600_000L + i * 60_000L
            val kwh = i * 0.0035
            add(
                SessionSample(
                    timestampMs = ts,
                    kwh = kwh,
                    pricePerKwh = 0.2643,
                    currency = "EUR",
                    costInCurrency = kwh * 0.2643,
                    countryCode = "NL",
                )
            )
        }
        repeat(24) { i ->
            val ts = now - 2 * 3_600_000L + i * 60_000L
            val kwh = i * 0.0042
            add(
                SessionSample(
                    timestampMs = ts,
                    kwh = kwh,
                    pricePerKwh = 0.2810,
                    currency = "EUR",
                    costInCurrency = kwh * 0.2810,
                    countryCode = "NL",
                )
            )
        }
    }
    val sessionKwh = 0.0084
    return EnergyUiState(
        running = true,
        watts = 78.4,
        kwh = sessionKwh,
        rate = rate,
        cost = sessionKwh * rate.pricePerKwh,
        priceSource = PriceSource.Auto,
        currency = "EUR",
        location = location,
        locationStatus = LocationStatus.Detected(location),
        history = history,
        powerSourceLabel = "PSU telemetry · Corsair AXi (LHM)",
        installStatus = InstallStatus.Idle,
    )
}
