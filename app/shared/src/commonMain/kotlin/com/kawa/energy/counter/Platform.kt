package com.kawa.energy.counter

interface Platform {
    val name: String
    val isDesktop: Boolean
}

expect fun getPlatform(): Platform