package com.kawa.energy.counter.price

object PriceProviderResolver {

    data class Resolution(
        val provider: PriceProvider,
        val region: String,
        val description: String,
    )

    /**
     * Map an ISO 3166-1 alpha-2 country code to a public day-ahead spot price source.
     * Returns null when no public provider covers the country — caller should fall back
     * to a manual fixed price.
     */
    fun forCountry(countryCode: String): Resolution? {
        val code = countryCode.uppercase()
        // Awattar gives DE/AT spot data directly.
        when (code) {
            "DE" -> return Resolution(
                AwattarPriceProvider(region = AwattarPriceProvider.Region.DE),
                "DE",
                "Awattar Germany",
            )
            "AT" -> return Resolution(
                AwattarPriceProvider(region = AwattarPriceProvider.Region.AT),
                "AT",
                "Awattar Austria",
            )
        }
        val bzn = COUNTRY_TO_BZN[code] ?: return null
        return Resolution(
            EnergyChartsPriceProvider(biddingZone = bzn),
            bzn,
            "energy-charts.info ($bzn)",
        )
    }

    private val COUNTRY_TO_BZN: Map<String, String> = mapOf(
        "NL" to "NL",
        "BE" to "BE",
        "FR" to "FR",
        "CH" to "CH",
        "LU" to "DE-LU",
        "PL" to "PL",
        "PT" to "PT",
        "ES" to "ES",
        "IT" to "IT-North",
        "DK" to "DK1",
        "NO" to "NO1",
        "SE" to "SE3",
        "FI" to "FI",
        "HU" to "HU",
        "CZ" to "CZ",
        "SK" to "SK",
        "IE" to "IE",
        "GR" to "GR",
        "HR" to "HR",
        "SI" to "SI",
        "RO" to "RO",
        "BG" to "BG",
        "EE" to "EE",
        "LV" to "LV",
        "LT" to "LT",
        "RS" to "RS",
    )
}
