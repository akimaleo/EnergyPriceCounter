package com.kawa.energy.counter.monitor

interface PowerSourceInstaller {
    val isSupported: Boolean
    val displayName: String

    /**
     * Install + launch the underlying power-telemetry tool.
     * Progress strings are pushed via [onProgress].
     */
    suspend fun install(onProgress: (String) -> Unit = {}): InstallResult
}

sealed class InstallResult {
    data object NotSupported : InstallResult()
    data class Success(val message: String) : InstallResult()
    data class Failure(val message: String) : InstallResult()
}

expect fun createPowerSourceInstaller(): PowerSourceInstaller
