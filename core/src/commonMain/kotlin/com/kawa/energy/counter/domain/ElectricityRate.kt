package com.kawa.energy.counter.domain

data class ElectricityRate(
    val pricePerKwh: Double,
    val currency: String,
    val source: String,
    val endpoint: String? = null,
)
