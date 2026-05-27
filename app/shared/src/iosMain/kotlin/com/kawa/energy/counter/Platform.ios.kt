package com.kawa.energy.counter

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val isDesktop: Boolean = false
}

actual fun getPlatform(): Platform = IOSPlatform()