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

/**
 * Day-ahead spot prices from api.energy-charts.info (Fraunhofer ISE).
 * No auth, multi-country via bidding zone.
 */
class EnergyChartsPriceProvider(
    private val biddingZone: String,
    private val client: HttpClient = defaultClient(),
) : PriceProvider {

    private val url = "https://api.energy-charts.info/price?bzn=$biddingZone"

    @OptIn(ExperimentalTime::class)
    override suspend fun currentRate(): ElectricityRate {
        val response: EnergyChartsResponse = client.get(url).body()

        val prices = response.price ?: error("No price data for zone $biddingZone")
        val times = response.unix_seconds ?: error("No timestamps in response")
        require(prices.size == times.size && prices.isNotEmpty()) {
            "Malformed energy-charts response for $biddingZone"
        }

        val nowSec = Clock.System.now().epochSeconds
        var index = times.indexOfLast { it <= nowSec }
        if (index < 0) index = 0

        val eurPerMwh = prices[index]
        return ElectricityRate(
            pricePerKwh = eurPerMwh / 1000.0,
            currency = "EUR",
            source = "energy-charts:$biddingZone",
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
private data class EnergyChartsResponse(
    val unix_seconds: List<Long>? = null,
    val price: List<Double>? = null,
    val unit: String? = null,
)
