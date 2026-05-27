package com.kawa.energy.counter.data.monitor

import com.kawa.energy.counter.domain.PowerSourceInstaller
import com.kawa.energy.counter.domain.InstallResult

actual fun createPowerSourceInstaller(): PowerSourceInstaller = object : PowerSourceInstaller {
    override val isSupported: Boolean = false
    override val displayName: String = "Power source installer"
    override suspend fun install(onProgress: (String) -> Unit): InstallResult = InstallResult.NotSupported
}
