<p align="center">
  <img src="docs/app_logo.svg" alt="EnergyCounter" width="120" />
</p>

<h1 align="center">EnergyCounter</h1>

<p align="center">
  Live electricity-cost calculator built with Kotlin Multiplatform + Compose Multiplatform.<br/>
  Samples your device's power draw, pulls real day-ahead spot prices for your country,
  and shows running kWh and EUR cost on a speedometer dashboard.
</p>

---

## Screenshots

<p align="center">
  <img src="docs/screenshot.svg" alt="EnergyCounter dashboard" width="720" />
</p>

> _Mockup above — replace `docs/screenshot.svg` (or add `docs/screenshot.png` and switch the path) with a real capture._

## Features

- **Speedometer power gauge** with animated needle, gradient arc and live source label.
- **Auto-detect location**: GPS + Geocoder on Android, IP geolocation (ipinfo.io → api.country.is fallback) on desktop.
- **Tap-a-country world map** in the price-source card to override your region manually.
- **Live spot prices**:
  - DE / AT — Awattar (`api.awattar.de`, `api.awattar.at`)
  - NL, BE, FR, CH, LU, PL, PT, ES, IT, DK, NO, SE, FI, HU, CZ, SK, IE, GR, HR, SI, RO, BG, EE, LV, LT, RS — energy-charts.info (Fraunhofer ISE)
  - Anywhere else — fall back to a fixed price you type in.
- **Collapsing toolbar** with sparklines: usage (kWh) and cumulative cost are visible whether the header is expanded or shrunk.
- **Persistent history** at `~/.energy-counter/sessions.ndjson` (desktop) or app `filesDir` (Android); the chart re-loads on startup and shows visual gaps for paused sessions.
- **Cumulative cost chart** on a shared time axis with kWh consumed.
- **Selectable text** everywhere — copy any metric out of the UI.

## Platforms

| Target  | Power source                                | Price source                                     |
|---------|---------------------------------------------|--------------------------------------------------|
| Desktop | OSHI · CPU load × estimated TDP             | Awattar / energy-charts.info via country lookup  |
| Android | `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER` deltas × live voltage | Same as desktop                                  |
| iOS     | Stubbed (no monitor implementation yet)     | Same as desktop                                  |

## Running

```bash
# Desktop (hot reload)
./gradlew :app:desktopApp:hotRun --auto

# Desktop (standard)
./gradlew :app:desktopApp:run

# Android
./gradlew :app:androidApp:assembleDebug

# Ktor server scaffold (placeholder)
./gradlew :server:run
```

iOS: open `app/iosApp` in Xcode and run.

## Tests

```bash
./gradlew :core:jvmTest :app:shared:jvmTest
```

CI runs both on every push and PR to `main` (see `.github/workflows/ci.yml`).

## Architecture

```
core/          ── Pure-Kotlin domain models (PowerReading, EnergyAccumulator, ElectricityRate)
app/shared/    ── Compose UI, expect/actual interfaces:
                   * PowerMonitor    (JVM = OSHI, Android = BatteryManager)
                   * LocationProvider (JVM = IP geo, Android = GPS + IP fallback)
                   * SessionStore    (file-backed NDJSON per platform)
                   PriceProvider implementations (Awattar, energy-charts) +
                   PriceProviderResolver that maps ISO country codes to providers.
app/androidApp ── Android entry point and runtime permission flow
app/desktopApp ── Desktop entry point
app/iosApp     ── iOS entry point
server/        ── Ktor server placeholder
```

## Notes on accuracy

EnergyCounter auto-detects the most accurate power source available on Windows:

1. **PSU telemetry via [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor)** — supported
   Corsair AXi/HXi/RMi, NZXT C-series, EVGA Pxx/Txx and Seasonic Prime PSUs
   report total input/output wattage over USB. This is the closest to wall-socket draw.
2. **CPU package + GPU + DRAM via LHM RAPL / NVAPI / ADL** — sum of the three
   biggest consumers in the box (typically ~80% of real draw).
3. **CPU load × estimated TDP via OSHI** — fallback when LHM isn't running.
   Accurate to ±50%.

### Enabling LibreHardwareMonitor

1. Download from <https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/releases> and run it (it ships a signed kernel driver).
2. `Options → Remote Web Server → Run` (default port `8085`).
3. Optionally `Options → Run On Windows Startup` so it survives reboots.

EnergyCounter probes `http://localhost:8085/data.json` on startup; if it
responds within 1.5 s, the LHM monitor is selected automatically and the source
label in the gauge changes to `PSU telemetry · <vendor>` or `LHM components`.

For wall-socket truth, layer a smart plug (Shelly Plus Plug S, Tapo P110,
Tasmota) on top — straightforward to add as another `PowerMonitor` actual.

## License

No license is set yet — assume "All rights reserved" until one is added.
