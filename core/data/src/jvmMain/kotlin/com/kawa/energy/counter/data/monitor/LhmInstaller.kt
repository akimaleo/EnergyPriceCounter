package com.kawa.energy.counter.data.monitor

import com.kawa.energy.counter.domain.PowerSourceInstaller
import com.kawa.energy.counter.domain.InstallResult

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

actual fun createPowerSourceInstaller(): PowerSourceInstaller = LhmInstaller()

class LhmInstaller : PowerSourceInstaller {

    override val displayName: String = "LibreHardwareMonitor"

    override val isSupported: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun install(onProgress: (String) -> Unit): InstallResult =
        withContext(Dispatchers.IO) {
            if (!isSupported) return@withContext InstallResult.NotSupported
            try {
                onProgress("Looking up latest LibreHardwareMonitor release…")
                val asset = fetchLatestZipAsset()
                    ?: return@withContext InstallResult.Failure("No zip asset found in latest release")

                val installRoot = File(System.getProperty("user.home"), ".energy-counter/LibreHardwareMonitor")
                installRoot.mkdirs()

                onProgress("Downloading ${asset.name} (${asset.size / 1_000_000} MB)…")
                val zipFile = File(installRoot.parentFile, asset.name)
                downloadTo(asset.browser_download_url, zipFile) { downloaded ->
                    val pct = if (asset.size > 0) (downloaded * 100 / asset.size).toInt() else 0
                    onProgress("Downloading… $pct%")
                }

                onProgress("Extracting…")
                extractZipSafely(zipFile, installRoot)
                zipFile.delete()

                val exe = locateExecutable(installRoot)
                    ?: return@withContext InstallResult.Failure(
                        "Could not locate LibreHardwareMonitor.exe under $installRoot"
                    )

                onProgress("Enabling web server on port 8085…")
                writeConfig(exe.parentFile)

                onProgress("Requesting admin rights — accept the Windows UAC prompt…")
                launchProcess(exe)

                onProgress("Waiting for web server…")
                repeat(20) {
                    delay(500)
                    if (LhmPowerMonitor.isAvailable()) {
                        return@withContext InstallResult.Success(
                            "LibreHardwareMonitor is running. Restart the session to switch to PSU/CPU/GPU telemetry."
                        )
                    }
                }
                InstallResult.Failure(
                    "Installed and launched, but the LHM web server didn't respond within 10 s. " +
                        "If you saw a UAC prompt, accept it. Then click Detect."
                )
            } catch (t: Throwable) {
                InstallResult.Failure(t.message ?: t::class.simpleName ?: "Unknown error")
            }
        }

    private suspend fun fetchLatestZipAsset(): GhAsset? {
        val release: GhRelease = http.get(GITHUB_LATEST_RELEASE) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body()
        // Prefer the net472 zip (broadest .NET Framework compatibility on Windows).
        return release.assets
            .filter { it.name.endsWith(".zip", ignoreCase = true) }
            .let { zips ->
                zips.firstOrNull { it.name.contains("net472", ignoreCase = true) }
                    ?: zips.firstOrNull { it.name.contains("net4", ignoreCase = true) }
                    ?: zips.firstOrNull()
            }
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        onBytes: (Long) -> Unit = {},
    ) {
        http.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.execute { response ->
            val channel: ByteReadChannel = response.bodyAsChannel()
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buf, 0, buf.size)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                    onBytes(total)
                }
            }
        }
    }

    private fun extractZipSafely(zip: File, dest: File) {
        val destCanonical = dest.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                // Zip-slip guard: refuse paths that escape the destination directory.
                require(outFile.canonicalPath.startsWith(destCanonical.path)) {
                    "Refusing zip entry outside extraction directory: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zin.copyTo(out) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    private fun locateExecutable(root: File): File? {
        if (!root.exists()) return null
        // LHM zip may extract files directly or under a versioned subdirectory.
        return root.walk()
            .firstOrNull { it.isFile && it.name.equals("LibreHardwareMonitor.exe", ignoreCase = true) }
    }

    private fun writeConfig(exeDir: File) {
        val config = File(exeDir, "LibreHardwareMonitor.config")
        // Overwriting is fine — fresh install. Enables the web server on 8085
        // so EnergyCounter's LhmPowerMonitor can talk to it immediately.
        config.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
            |<configuration>
            |  <startup>
            |    <supportedRuntime version="v4.0" sku=".NETFramework,Version=v4.7.2" />
            |  </startup>
            |  <appSettings>
            |    <add key="runWebServerMenuItem" value="true" />
            |    <add key="listenerPort" value="8085" />
            |    <add key="minTrayMenuItem" value="true" />
            |  </appSettings>
            |</configuration>
            |""".trimMargin()
        )
    }

    private fun launchProcess(exe: File) {
        // ProcessBuilder calls CreateProcess directly, which fails with
        // ERROR_ELEVATION_REQUIRED for apps marked requireAdministrator and
        // never shows a UAC prompt. To trigger the prompt we need ShellExecute
        // with the "runas" verb. PowerShell's Start-Process exposes that:
        //   Start-Process -FilePath '<exe>' -WorkingDirectory '<dir>' -Verb RunAs
        // If the user cancels UAC, PowerShell exits with a non-zero status —
        // we surface that through process.waitFor() below.
        val escapedExe = exe.absolutePath.replace("'", "''")
        val escapedDir = exe.parentFile.absolutePath.replace("'", "''")
        val psCommand =
            "Start-Process -FilePath '$escapedExe' -WorkingDirectory '$escapedDir' -Verb RunAs"
        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle", "Hidden",
            "-Command", psCommand,
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val exited = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        if (exited && process.exitValue() != 0) {
            // Most commonly: user cancelled the UAC prompt (exit 1).
            throw RuntimeException(
                "Couldn't elevate LibreHardwareMonitor — did you accept the Windows admin prompt?"
            )
        }
    }

    companion object {
        private const val GITHUB_LATEST_RELEASE =
            "https://api.github.com/repos/LibreHardwareMonitor/LibreHardwareMonitor/releases/latest"
        private const val USER_AGENT = "EnergyCounter/1.0"
    }
}

@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    val size: Long = 0L,
    val browser_download_url: String,
)
