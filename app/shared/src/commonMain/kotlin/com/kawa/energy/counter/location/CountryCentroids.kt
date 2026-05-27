package com.kawa.energy.counter.location

/** Approximate (lat, lon) for ISO-3166-1 alpha-2 country codes used by the world-map picker. */
object CountryCentroids {

    data class Point(val lat: Double, val lon: Double)

    val all: Map<String, Point> = mapOf(
        // Awattar coverage
        "DE" to Point(51.2, 10.4),
        "AT" to Point(47.6, 13.3),
        // energy-charts coverage
        "NL" to Point(52.2, 5.3),
        "BE" to Point(50.5, 4.5),
        "FR" to Point(46.6, 2.2),
        "CH" to Point(46.8, 8.2),
        "LU" to Point(49.8, 6.1),
        "PL" to Point(52.0, 19.4),
        "PT" to Point(39.4, -8.2),
        "ES" to Point(40.4, -3.7),
        "IT" to Point(42.8, 12.8),
        "DK" to Point(56.0, 9.5),
        "NO" to Point(62.0, 10.0),
        "SE" to Point(62.0, 15.0),
        "FI" to Point(64.5, 26.0),
        "HU" to Point(47.1, 19.5),
        "CZ" to Point(49.8, 15.5),
        "SK" to Point(48.7, 19.7),
        "IE" to Point(53.4, -8.0),
        "GR" to Point(39.0, 22.0),
        "HR" to Point(45.1, 15.2),
        "SI" to Point(46.1, 14.8),
        "RO" to Point(45.9, 24.9),
        "BG" to Point(42.7, 25.5),
        "EE" to Point(58.6, 25.0),
        "LV" to Point(56.9, 24.6),
        "LT" to Point(55.2, 23.9),
        "RS" to Point(44.0, 21.0),
        // Unsupported but useful for context
        "GB" to Point(54.8, -4.0),
        "US" to Point(39.0, -98.0),
        "CA" to Point(56.0, -106.0),
        "MX" to Point(23.6, -102.5),
        "BR" to Point(-10.0, -55.0),
        "AR" to Point(-38.0, -64.0),
        "CL" to Point(-35.0, -71.0),
        "ZA" to Point(-29.0, 24.0),
        "EG" to Point(27.0, 30.0),
        "MA" to Point(31.0, -7.0),
        "NG" to Point(9.0, 8.0),
        "KE" to Point(0.0, 38.0),
        "TR" to Point(39.0, 35.0),
        "SA" to Point(24.0, 45.0),
        "IL" to Point(31.5, 35.0),
        "IN" to Point(21.0, 78.0),
        "CN" to Point(35.0, 105.0),
        "JP" to Point(36.0, 138.0),
        "KR" to Point(36.0, 128.0),
        "ID" to Point(-2.0, 118.0),
        "TH" to Point(15.0, 100.0),
        "VN" to Point(16.0, 108.0),
        "PH" to Point(13.0, 122.0),
        "AU" to Point(-25.0, 133.0),
        "NZ" to Point(-41.0, 174.0),
        "RU" to Point(60.0, 90.0),
        "UA" to Point(49.0, 32.0),
        "IS" to Point(64.9, -19.0),
    )

    fun forCountry(code: String): Point? = all[code.uppercase()]

    /** Find the country whose centroid is closest to the given lat/lon (Euclidean on lat/lon). */
    fun nearest(lat: Double, lon: Double): String? {
        var bestCode: String? = null
        var bestDist = Double.MAX_VALUE
        for ((code, p) in all) {
            val dLat = p.lat - lat
            val dLon = p.lon - lon
            val d = dLat * dLat + dLon * dLon
            if (d < bestDist) {
                bestDist = d
                bestCode = code
            }
        }
        return bestCode
    }
}
