package com.kawa.energy.counter.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.EnergyAccumulator
import com.kawa.energy.counter.domain.PowerReading
import com.kawa.energy.counter.domain.SessionSample
import com.kawa.energy.counter.domain.SessionStore
import com.kawa.energy.counter.data.history.createSessionStore
import com.kawa.energy.counter.domain.LocationInfo
import com.kawa.energy.counter.domain.LocationProvider
import com.kawa.energy.counter.domain.LocationResult
import com.kawa.energy.counter.data.location.createLocationProvider
import com.kawa.energy.counter.domain.InstallResult
import com.kawa.energy.counter.domain.MonitorConfig
import com.kawa.energy.counter.domain.PowerMonitor
import com.kawa.energy.counter.domain.PowerSourceInstaller
import com.kawa.energy.counter.data.monitor.createPowerMonitor
import com.kawa.energy.counter.data.monitor.createPowerSourceInstaller
import com.kawa.energy.counter.data.price.FixedPriceProvider
import com.kawa.energy.counter.domain.PriceProvider
import com.kawa.energy.counter.data.price.PriceProviderResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnergyUiState(
    val running: Boolean = false,
    val watts: Double = 0.0,
    val kwh: Double = 0.0,
    val rate: ElectricityRate? = null,
    val cost: Double = 0.0,
    val priceSource: PriceSource = PriceSource.Auto,
    val fixedPricePerKwh: Double = 0.30,
    val currency: String = "EUR",
    val location: LocationInfo? = null,
    val locationStatus: LocationStatus = LocationStatus.Idle,
    val history: List<SessionSample> = emptyList(),
    val powerSourceLabel: String? = null,
    val installStatus: InstallStatus = InstallStatus.Idle,
    val errorMessage: String? = null,
)

sealed class InstallStatus {
    data object Idle : InstallStatus()
    data class InProgress(val message: String) : InstallStatus()
    data class Done(val message: String) : InstallStatus()
    data class Failed(val message: String) : InstallStatus()
    data object NotSupported : InstallStatus()
}

enum class PriceSource { Auto, Fixed }

/**
 * UI-facing surface of the dashboard. Splitting it out lets the composables
 * depend on an interface instead of the concrete ViewModel — meaning @Preview
 * composables can pass a no-op stub without constructing the real VM (whose
 * `init { }` reads from disk / starts native monitors).
 */
interface DashboardActions {
    fun start() {}
    fun stop() {}
    fun detectLocation() {}
    fun overrideCountry(countryCode: String) {}
    fun setPriceSource(source: PriceSource) {}
    fun setFixedPrice(price: Double) {}
    fun installPowerSource() {}
    fun clearHistory() {}
    val isInstallerSupported: Boolean get() = false
}

sealed class LocationStatus {
    data object Idle : LocationStatus()
    data object Detecting : LocationStatus()
    data class Detected(val info: LocationInfo) : LocationStatus()
    data object PermissionDenied : LocationStatus()
    data class Failed(val message: String) : LocationStatus()
    data class Unsupported(val countryCode: String) : LocationStatus()
}

class EnergyViewModel : ViewModel(), DashboardActions {

    private val accumulator = EnergyAccumulator()
    private var monitor: PowerMonitor? = null
    private val locationProvider: LocationProvider = createLocationProvider()
    private val store: SessionStore = createSessionStore()
    private val installer: PowerSourceInstaller = createPowerSourceInstaller()
    private var lastPersistMs: Long = 0L
    private val persistIntervalMs: Long = 5_000L

    private val _state = MutableStateFlow(EnergyUiState())
    val state: StateFlow<EnergyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val items = runCatching { store.loadAll() }.getOrDefault(emptyList())
            _state.update { it.copy(history = items) }
        }
    }

    override fun start() {
        if (_state.value.running) return
        accumulator.reset()
        _state.update { it.copy(kwh = 0.0, cost = 0.0, running = true, errorMessage = null) }

        val m = monitor ?: createPowerMonitor(MonitorConfig()).also { monitor = it }
        m.start()

        viewModelScope.launch { refreshRate() }
        viewModelScope.launch {
            m.readings.collect { reading ->
                accumulator.add(reading)
                recompute(reading)
                maybePersist(reading.timestampMs)
            }
        }
    }

    override fun clearHistory() {
        viewModelScope.launch {
            runCatching { store.clear() }
            _state.update { it.copy(history = emptyList()) }
        }
    }

    private fun maybePersist(nowMs: Long) {
        if (nowMs - lastPersistMs < persistIntervalMs) return
        val s = _state.value
        val rate = s.rate ?: return
        lastPersistMs = nowMs
        val sample = SessionSample(
            timestampMs = nowMs,
            kwh = s.kwh,
            pricePerKwh = rate.pricePerKwh,
            currency = rate.currency,
            costInCurrency = s.cost,
            countryCode = s.location?.countryCode,
        )
        viewModelScope.launch {
            runCatching { store.append(sample) }
            _state.update { it.copy(history = it.history + sample) }
        }
    }

    override fun stop() {
        monitor?.stop()
        monitor = null
        _state.update { it.copy(running = false, watts = 0.0) }
    }

    override fun setPriceSource(source: PriceSource) {
        _state.update { it.copy(priceSource = source) }
        viewModelScope.launch { refreshRate() }
    }

    override fun setFixedPrice(price: Double) {
        _state.update { it.copy(fixedPricePerKwh = price) }
        if (_state.value.priceSource == PriceSource.Fixed) {
            viewModelScope.launch { refreshRate() }
        }
    }

    override fun detectLocation() {
        _state.update { it.copy(locationStatus = LocationStatus.Detecting) }
        viewModelScope.launch {
            when (val r = locationProvider.detect()) {
                is LocationResult.Ok -> {
                    _state.update {
                        it.copy(
                            location = r.info,
                            locationStatus = LocationStatus.Detected(r.info),
                        )
                    }
                    refreshRate()
                }
                LocationResult.PermissionDenied -> _state.update {
                    it.copy(locationStatus = LocationStatus.PermissionDenied)
                }
                LocationResult.Unavailable -> _state.update {
                    it.copy(locationStatus = LocationStatus.Failed("Location unavailable"))
                }
                is LocationResult.Failure -> _state.update {
                    it.copy(locationStatus = LocationStatus.Failed(r.message))
                }
            }
        }
    }

    override fun overrideCountry(countryCode: String) {
        if (countryCode.length != 2) return
        val info = LocationInfo(countryCode = countryCode.uppercase(), label = countryCode.uppercase())
        _state.update {
            it.copy(
                location = info,
                locationStatus = LocationStatus.Detected(info),
            )
        }
        viewModelScope.launch { refreshRate() }
    }

    private suspend fun refreshRate() {
        val provider: PriceProvider = when (_state.value.priceSource) {
            PriceSource.Fixed -> FixedPriceProvider(
                ElectricityRate(_state.value.fixedPricePerKwh, _state.value.currency, "manual")
            )
            PriceSource.Auto -> {
                val country = _state.value.location?.countryCode
                if (country == null) {
                    // Detection is async; quietly skip and wait for it to complete.
                    // Once the location resolves, overrideCountry/detectLocation
                    // will re-trigger refreshRate.
                    _state.update { it.copy(rate = null) }
                    return
                }
                val resolution = PriceProviderResolver.forCountry(country)
                if (resolution == null) {
                    _state.update {
                        it.copy(
                            rate = null,
                            locationStatus = LocationStatus.Unsupported(country),
                            errorMessage = "No public price API for $country — switch to Fixed",
                        )
                    }
                    return
                }
                resolution.provider
            }
        }
        try {
            val rate = provider.currentRate()
            _state.update { it.copy(rate = rate, currency = rate.currency, errorMessage = null) }
        } catch (t: Throwable) {
            _state.update { it.copy(errorMessage = "Price fetch failed: ${t.message}") }
        }
    }

    private fun recompute(reading: PowerReading) {
        val rate = _state.value.rate
        val cost = rate?.let { accumulator.totalKwh * it.pricePerKwh } ?: 0.0
        _state.update {
            it.copy(
                watts = reading.watts,
                kwh = accumulator.totalKwh,
                cost = cost,
                powerSourceLabel = reading.sourceLabel ?: it.powerSourceLabel,
            )
        }
    }

    override val isInstallerSupported: Boolean get() = installer.isSupported

    override fun installPowerSource() {
        if (!installer.isSupported) {
            _state.update { it.copy(installStatus = InstallStatus.NotSupported) }
            return
        }
        if (_state.value.installStatus is InstallStatus.InProgress) return
        _state.update { it.copy(installStatus = InstallStatus.InProgress("Starting…")) }
        viewModelScope.launch {
            val result = installer.install { msg ->
                _state.update { it.copy(installStatus = InstallStatus.InProgress(msg)) }
            }
            _state.update {
                it.copy(
                    installStatus = when (result) {
                        is InstallResult.Success -> InstallStatus.Done(result.message)
                        is InstallResult.Failure -> InstallStatus.Failed(result.message)
                        InstallResult.NotSupported -> InstallStatus.NotSupported
                    }
                )
            }
            if (result is InstallResult.Success) {
                // Restart the monitor so the new source (LHM) is picked up.
                val wasRunning = _state.value.running
                if (wasRunning) stop()
                if (wasRunning) start()
            }
        }
    }

    override fun onCleared() {
        monitor?.stop()
        monitor = null
    }
}
