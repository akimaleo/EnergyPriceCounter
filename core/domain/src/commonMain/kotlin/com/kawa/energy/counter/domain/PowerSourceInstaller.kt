package com.kawa.energy.counter.domain

interface PowerSourceInstaller {
    val isSupported: Boolean
    val displayName: String
    suspend fun install(onProgress: (String) -> Unit = {}): InstallResult
}

sealed class InstallResult {
    data object NotSupported : InstallResult()
    data class Success(val message: String) : InstallResult()
    data class Failure(val message: String) : InstallResult()
}
