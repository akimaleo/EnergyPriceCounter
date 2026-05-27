package com.kawa.energy.counter.monitor

actual fun createPowerSourceInstaller(): PowerSourceInstaller = object : PowerSourceInstaller {
    override val isSupported: Boolean = false
    override val displayName: String = "Power source installer"
    override suspend fun install(onProgress: (String) -> Unit): InstallResult = InstallResult.NotSupported
}
