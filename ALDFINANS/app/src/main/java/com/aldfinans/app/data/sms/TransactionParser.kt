package com.aldfinans.app.data.sms

import com.aldfinans.app.data.TransactionEntity
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale

object TransactionParser {
    private val bankAliases = listOf(
        "YAPI KREDI" to "Yapı Kredi", "YAPIKREDI" to "Yapı Kredi",
        "IS BANKASI" to "İş Bankası", "ISBANK" to "İş Bankası", "ISCEP" to "İş Bankası",
        "AKBANK" to "Akbank", "GARANTI BBVA" to "Garanti BBVA", "GARANTI" to "Garanti BBVA",
        "HALKBANK" to "Halkbank", "VAKIFBANK" to "VakıfBank", "ZIRAAT" to "Ziraat Bankası",
        "DENIZBANK" to "DenizBank", "QNB" to "QNB", "TEB" to "TEB", "ING" to "ING",
        "KUVEYT TURK" to "Kuveyt Türk", "KUVEYTTURK" to "Kuveyt Türk", "ENPARA" to "Enpara"
    )

    private val completedCues = listOf(
        "HARCAMA YAPTINIZ", "HARCAMA YAPILDI", "HARCAMANIZDAN CEKILDI",
        "KARTINIZDAN CEKILDI", "KARTTAN CEKILDI", "HESABINIZDAN CEKILDI",
        "ODEME YAPILDI", "ODEME GERCEKLESTI", "ODEME GERCEKLESTIRILDI",
        "ISLEMINIZ GERCEKLESTI", "ISLEMINIZ GERCEKLESTIRILDI",
        "ISLEM GERCEKLESTI", "ISLEM GERCEKLESTIRILDI",
        "ALISVERIS YAPTINIZ", "ALISVERIS YAPILDI", "HARCAMA GERCEKLESTI",
        "TUTARINDA HARCAMA", "TUTARINDA ISLEM", "POS ISLEMI"
    )
    private val promotionalWords = listOf(
        "KAMPANYA", "PROMOSYON", "WINPARA", "WIN PARA", "HEDIYE", "PUAN KAZAN",
        "BONUS KAZAN", "KAZANIN", "KAZANDIR", "BEDAVA", "ODUL", "INDIRIM"
    )
    private val transferWords = listOf(
        "EFT", "HAVALE", "TRANSFER", "PARA GONDERILDI", "PARA GONDER",
        "GELEN FAST", "GELEN EFT", "HESABINIZA PARA GELDI"
    )
    private val moneyRegex = Regex(
        """(?<![\\d.,])((?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?)\\s*(?:TL|TRY)\\b"""
    )

    private fun normalized(value: String): String =
        value.uppercase(Locale("tr", "TR"))
            .replace('İ', 'I').replace('ı', 'I').replace('Ş', 'S')
            .replace('Ğ', 'G').replace('Ü', 'U').replace('Ö', 'O').replace('Ç', 'C')

    private fun compact(value: String) = normalized(value).filter(Char::isLetterOrDigit)

    private fun identifyBank(sender: String, body: String): String? {
        val senderNorm = normalized(sender)
        val senderCompact = compact(sender)
        val bySender = bankAliases.firstOrNull { (alias, _) ->
            val key = compact(alias)
            senderCompact == key ||
                (key.length >= 4 && senderCompact.contains(key)) ||
                (key in listOf("TEB", "QNB", "ING") &&
                    Regex("""(?:^|\\W)${Regex.escape(key)}(?:$|\\W)""").containsMatchIn(senderNorm))
        }
        if (bySender != null) return bySender.second

        // Short-code fallback is intentionally limited to the opening of the message.
        val opening = normalized(body).trimStart(' ', '*', '-', ':').take(80)
        return bankAliases.firstOrNull { (alias, _) ->
            val key = normalized(alias)
            opening.startsWith(key) ||
                Regex("""^\\s*(?:DEGERLI\\s+)?${Regex.escape(key)}(?:\\s|[:,.!-])""")
                    .containsMatchIn(opening)
        }?.second
    }

    private fun parseKurus(raw: String): Long? {
        val normalizedAmount = when {
            raw.contains(",") && raw.contains(".") ->
                if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) raw.replace(".", "").replace(",", ".")
                else raw.replace(",", "")
            raw.contains(",") -> {
                val decimal = raw.substringAfter(",")
                if (decimal.length in 1..2) raw.replace(",", ".") else raw.replace(",", "")
            }
            raw.contains(".") -> {
                val decimal = raw.substringAfter(".")
                if (decimal.length in 1..2) raw else raw.replace(".", "")
            }
            else -> raw
        }
        return try {
            BigDecimal(normalizedAmount).movePointRight(2).longValueExact().takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    fun fingerprint(sender: String, body: String, timestamp: Long): String {
        val canonical = "$timestamp|${compact(sender)}|${normalized(body).trim().replace(Regex("""\\s+"""), " ")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun parse(sender: String, body: String, timestamp: Long, source: String = "SMS"): TransactionEntity? {
        val text = normalized(body)
        val bank = identifyBank(sender, body) ?: return null
        val cues = completedCues.filter(text::contains)
        if (cues.isEmpty()) return null
        if (transferWords.any(text::contains)) return null

        // A promotion is never a transaction by itself; an explicit completed transaction cue is required.
        if (promotionalWords.any(text::contains) && cues.isEmpty()) return null

        val matches = moneyRegex.findAll(body.uppercase(Locale("tr", "TR"))).toList()
        if (matches.isEmpty()) return null
        val cuePositions = cues.mapNotNull { text.indexOf(it).takeIf { pos -> pos >= 0 } }
        val amountMatch = if (cuePositions.isEmpty()) matches.first() else matches.minBy { match ->
            cuePositions.minOf { cue -> kotlin.math.abs(cue - match.range.first) }
        }
        val amountKurus = parseKurus(amountMatch.groupValues[1]) ?: return null

        val type = when {
            "IADE" in text || "GERI ODEME" in text -> "REFUND"
            "IPTAL" in text -> "CANCELLED"
            listOf("ATM", "NAKIT CEK", "CEKIM").any(text::contains) -> "CASH_WITHDRAWAL"
            "FATURA" in text -> "BILL"
            else -> "EXPENSE"
        }
        val merchant = listOf(
            Regex("""(?:ISYERI|FIRMA|MAGAZA|UYE ISYERI)\\s*[:=-]?\\s*([^,.;\\n]+)"""),
            Regex("""ISYERINDE\\s+([^,.;\\n]+)""")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()
        val category = when {
            listOf("SHELL", "OPET", "PETROL", "AKARYAKIT", "YAKIT").any(text::contains) -> "Akaryakıt"
            listOf("MIGROS", "A101", "BIM", "SOK", "MARKET").any(text::contains) -> "Market"
            listOf("RESTORAN", "YEMEK", "CAFE", "KAFE").any(text::contains) -> "Yemek"
            "FATURA" in text -> "Fatura"
            else -> "Diğer"
        }
        val cardLast4 = Regex("""(?:KART|CARD)[^\\d]{0,16}(\\d{4})\\b""")
            .find(text)?.groupValues?.get(1)

        return TransactionEntity(
            dateTime = timestamp,
            bank = bank,
            amountKurus = amountKurus,
            category = category,
            merchant = merchant.ifBlank { null },
            cardLast4 = cardLast4,
            source = source,
            rawData = body,
            transactionType = type,
            fingerprint = fingerprint(sender, body, timestamp)
        )
    }
}
