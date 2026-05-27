package com.kawa.energy.counter.location

/** Approximate (lat, lon) for ISO-3166-1 alpha-2 country codes used by the world-map picker. */
object CountryCentroids {

    data class Point(val lat: Double, val lon: Double)

    /** Geographic bounding box of a country (mainland only — overseas territories ignored). */
    data class BBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

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

    fun bboxFor(code: String): BBox? = bboxes[code.uppercase()]

    /** Mainland bounding boxes — coarse, used to render a wireframe overlay. */
    val bboxes: Map<String, BBox> = mapOf(
        // Awattar / energy-charts coverage
        "DE" to BBox(47.3, 55.0, 5.9, 15.0),
        "AT" to BBox(46.4, 49.0, 9.5, 17.2),
        "NL" to BBox(50.7, 53.5, 3.4, 7.2),
        "BE" to BBox(49.5, 51.5, 2.5, 6.4),
        "FR" to BBox(42.3, 51.1, -5.0, 9.0),
        "CH" to BBox(45.8, 47.8, 5.9, 10.5),
        "LU" to BBox(49.4, 50.2, 5.7, 6.5),
        "PL" to BBox(49.0, 54.8, 14.1, 24.1),
        "PT" to BBox(37.0, 42.2, -9.5, -6.2),
        "ES" to BBox(35.9, 43.8, -9.3, 4.3),
        "IT" to BBox(35.5, 47.1, 6.6, 18.5),
        "DK" to BBox(54.6, 57.7, 8.1, 12.7),
        "NO" to BBox(58.0, 71.0, 4.6, 31.0),
        "SE" to BBox(55.3, 69.0, 11.0, 24.2),
        "FI" to BBox(59.8, 70.1, 20.5, 31.6),
        "HU" to BBox(45.7, 48.6, 16.1, 22.9),
        "CZ" to BBox(48.5, 51.1, 12.1, 18.9),
        "SK" to BBox(47.7, 49.6, 16.8, 22.6),
        "IE" to BBox(51.4, 55.4, -10.5, -5.4),
        "GR" to BBox(34.8, 41.7, 19.4, 28.2),
        "HR" to BBox(42.4, 46.6, 13.5, 19.4),
        "SI" to BBox(45.4, 46.9, 13.4, 16.6),
        "RO" to BBox(43.6, 48.3, 20.3, 29.7),
        "BG" to BBox(41.2, 44.2, 22.4, 28.6),
        "EE" to BBox(57.5, 59.7, 21.8, 28.2),
        "LV" to BBox(55.7, 58.1, 20.9, 28.2),
        "LT" to BBox(53.9, 56.5, 20.9, 26.8),
        "RS" to BBox(42.2, 46.2, 18.8, 23.0),
        // Wider context
        "GB" to BBox(49.9, 60.8, -8.6, 1.8),
        "IS" to BBox(63.3, 66.6, -24.5, -13.5),
        "UA" to BBox(44.4, 52.4, 22.1, 40.2),
        "RU" to BBox(41.2, 77.0, 27.0, 180.0),
        "TR" to BBox(36.0, 42.1, 26.0, 44.8),
        "EG" to BBox(22.0, 31.7, 24.7, 36.9),
        "MA" to BBox(27.7, 35.9, -13.2, -1.0),
        "NG" to BBox(4.3, 13.9, 2.7, 14.7),
        "KE" to BBox(-4.7, 5.0, 33.9, 41.9),
        "ZA" to BBox(-34.8, -22.1, 16.5, 32.9),
        "SA" to BBox(16.4, 32.2, 34.5, 55.7),
        "IL" to BBox(29.5, 33.3, 34.3, 35.9),
        "IN" to BBox(8.0, 35.5, 68.1, 97.4),
        "CN" to BBox(18.2, 53.6, 73.5, 134.8),
        "JP" to BBox(31.0, 45.5, 129.5, 145.8),
        "KR" to BBox(33.2, 38.6, 125.1, 129.6),
        "ID" to BBox(-10.4, 6.0, 95.0, 141.0),
        "TH" to BBox(5.6, 20.5, 97.3, 105.6),
        "VN" to BBox(8.6, 23.4, 102.1, 109.5),
        "PH" to BBox(4.6, 21.1, 116.9, 126.6),
        "AU" to BBox(-43.6, -10.7, 113.2, 153.6),
        "NZ" to BBox(-46.6, -34.4, 166.5, 178.6),
        "US" to BBox(24.5, 49.4, -125.0, -66.9),
        "CA" to BBox(41.7, 70.0, -141.0, -52.6),
        "MX" to BBox(14.5, 32.7, -118.4, -86.7),
        "BR" to BBox(-33.7, 5.3, -73.9, -34.7),
        "AR" to BBox(-55.0, -21.8, -73.6, -53.6),
        "CL" to BBox(-55.9, -17.5, -75.6, -66.4),
    )

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
