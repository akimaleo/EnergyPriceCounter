package com.kawa.energy.counter.feature.dashboard

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isDesktop: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()