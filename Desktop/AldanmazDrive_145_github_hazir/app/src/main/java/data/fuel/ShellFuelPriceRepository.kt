package com.aldanmaz.drivedashboard.data.fuel

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ShellFuelPrice(
    val pricePerLiter: Double,
    val city: String,
    val fuelType: String,
    val updatedDate: String?,
)

class ShellFuelPriceRepository {
    fun fetchPrice(city: String, fuelType: String): ShellFuelPrice {
        val slug = city.toShellSlug()
        require(slug.isNotBlank()) { "Bulunduğunuz il belirlenemedi." }
        val encodedSlug = URLEncoder.encode(slug, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val url = URL("https://akaryakit.org/$encodedSlug-shell-fiyatlari")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "AldanmazDrive/143 Android")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val text = html
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&uuml;", "ü")
                .replace("&Uuml;", "Ü")
                .replace("&ouml;", "ö")
                .replace("&Ouml;", "Ö")
                .replace("&ccedil;", "ç")
                .replace("&Ccedil;", "Ç")
                .replace(Regex("\\s+"), " ")
            val value = when (fuelType) {
                "Benzin" -> extractPrice(text, "Benzin")
                "Dizel" -> extractPrice(text, "Motorin")
                "Gaz" -> extractPrice(text, "LPG")
                else -> null
            } ?: throw IllegalStateException("$city için Shell $fuelType fiyatı alınamadı.")
            val date = Regex("(\\d{2}\\.\\d{2}\\.\\d{4})")
                .find(text.substringAfter("Güncel $city Shell Fiyatları", ""))
                ?.groupValues?.getOrNull(1)
            ShellFuelPrice(value, city, fuelType, date)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractPrice(text: String, label: String): Double? {
        val pattern = Regex(
            "${Regex.escape(label)}(?: \\(Mazot\\))?\\s*[:|]?\\s*([0-9]{1,3}(?:\\.[0-9]{3})?,[0-9]{2})\\s*(?:₺|TL)?",
            RegexOption.IGNORE_CASE,
        )
        return pattern.find(text)?.groupValues?.getOrNull(1)
            ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull()
    }

    private fun String.toShellSlug(): String = lowercase(Locale("tr", "TR"))
        .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
        .replace("ş", "s").replace("ö", "o").replace("ç", "c")
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')
}