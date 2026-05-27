package com.kawa.energy.counter.domain

interface PriceProvider {
    suspend fun currentRate(): ElectricityRate
}
