package com.kawa.energy.counter.data.price

import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.PriceProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AwattarPriceProvider(
    private val client: HttpClient = defaultClient(),
    private val region: Region = Region.DE,
) : PriceProvider {

    enum class Region(val host: String) {
        DE("https://api.awattar.de"),
        AT("https://api.awattar.at"),
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun currentRate(): ElectricityRate {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val url = "${region.host}/v1/marketdata?start=$nowMs"
        val response: AwattarResponse = client.get(url).body()
        val slot = response.data.firstOrNull { nowMs in it.startTimestamp..<it.endTimestamp }
            ?: response.data.firstOrNull()
            ?: error("No market data returned")
        // marketprice is in EUR/MWh -> EUR/kWh
        return ElectricityRate(
            pricePerKwh = slot.marketprice / 1000.0,
            currency = "EUR",
            source = "awattar:${region.name}",
            endpoint = url,
        )
    }

    companion object {
        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

@Serializable
private data class AwattarResponse(val data: List<AwattarSlot>)

@Serializable
private data class AwattarSlot(
    val start_timestamp: Long,
    val end_timestamp: Long,
    val marketprice: Double,
    val unit: String,
) {
    val startTimestamp: Long get() = start_timestamp
    val endTimestamp: Long get() = end_timestamp
}
