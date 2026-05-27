package com.kawa.energy.counter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.EnergyAccumulator
import com.kawa.energy.counter.domain.PowerReading
import com.kawa.energy.counter.history.SessionSample
import com.kawa.energy.counter.history.SessionStore
import com.kawa.energy.counter.history.createSessionStore
import com.kawa.energy.counter.location.LocationInfo
import com.kawa.energy.counter.location.LocationProvider
import com.kawa.energy.counter.location.LocationResult
import com.kawa.energy.counter.location.createLocationProvider
import com.kawa.energy.counter.monitor.InstallResult
import com.kawa.energy.counter.monitor.MonitorConfig
import com.kawa.energy.counter.monitor.PowerMonitor
import com.kawa.energy.counter.monitor.PowerSourceInstaller
import com.kawa.energy.counter.monitor.createPowerMonitor
import com.kawa.energy.counter.monitor.createPowerSourceInstaller
import com.kawa.energy.counter.price.FixedPriceProvider
import com.kawa.energy.counter.price.PriceProvider
import com.kawa.energy.counter.price.PriceProviderResolver
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

sealed class LocationStatus {
    data object Idle : LocationStatus()
    data object Detecting : LocationStatus()
    data class Detected(val info: LocationInfo) : LocationStatus()
    data object PermissionDenied : LocationStatus()
    data class Failed(val message: String) : LocationStatus()
    data class Unsupported(val countryCode: String) : LocationStatus()
}

class EnergyViewModel : ViewModel() {

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

    fun start() {
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

    fun clearHistory() {
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

    fun stop() {
        monitor?.stop()
        monitor = null
        _state.update { it.copy(running = false, watts = 0.0) }
    }

    fun setPriceSource(source: PriceSource) {
        _state.update { it.copy(priceSource = source) }
        viewModelScope.launch { refreshRate() }
    }

    fun setFixedPrice(price: Double) {
        _state.update { it.copy(fixedPricePerKwh = price) }
        if (_state.value.priceSource == PriceSource.Fixed) {
            viewModelScope.launch { refreshRate() }
        }
    }

    fun detectLocation() {
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

    fun overrideCountry(countryCode: String) {
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

    val isInstallerSupported: Boolean get() = installer.isSupported

    fun installPowerSource() {
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
