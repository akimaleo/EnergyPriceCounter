package com.kawa.energy.counter.data.price

import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.PriceProvider

class FixedPriceProvider(
    private val rate: ElectricityRate,
) : PriceProvider {
    override suspend fun currentRate(): ElectricityRate = rate
}
