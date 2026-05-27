package com.kawa.energy.counter.feature.dashboard

interface Platform {
    val name: String
    val isDesktop: Boolean
}

expect fun getPlatform(): Platform