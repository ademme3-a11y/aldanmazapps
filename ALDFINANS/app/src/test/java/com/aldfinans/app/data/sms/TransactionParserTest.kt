package com.aldfinans.app.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {
    private val time = 1_792_000_000_000L

    @Test fun readsCommaDecimalWithoutChangingKurus() {
        val item = TransactionParser.parse("AKBANK", "Akbank: 39,75 TL harcama yaptınız.", time)
        assertNotNull(item)
        assertEquals(3975L, item!!.amountKurus)
    }

    @Test fun readsTurkishThousandsAndDecimalSeparators() {
        val item = TransactionParser.parse("AKBANK", "Akbank: 1.234,56 TL harcama yaptınız.", time)
        assertNotNull(item)
        assertEquals(123456L, item!!.amountKurus)
    }

    @Test fun readsDotDecimalAmount() {
        val item = TransactionParser.parse("AKBANK", "Akbank: 39.75 TL harcama yaptınız.", time)
        assertNotNull(item)
        assertEquals(3975L, item!!.amountKurus)
    }

    @Test fun rejectsCampaignThresholdAndReward() {
        val item = TransactionParser.parse(
            "AKBANK",
            "1000 TL üzeri alışverişe 200 TL Win Para kampanyası! Hemen katılın.",
            time
        )
        assertNull(item)
    }

    @Test fun doesNotInferBankFromMentionInMiddleOfUnrelatedMessage() {
        val item = TransactionParser.parse(
            "Bilinmeyen gönderici",
            "Bilgilendirme: TEB kartınız için kampanya detaylarını inceleyin.",
            time
        )
        assertNull(item)
    }

    @Test fun rejectsTransfersAsPurchases() {
        val item = TransactionParser.parse("AKBANK", "Akbank: 500,00 TL EFT işleminiz gerçekleştirildi.", time)
        assertNull(item)
    }

    @Test fun explicitPurchaseCanMentionCampaign() {
        val item = TransactionParser.parse(
            "AKBANK",
            "Akbank: Kampanya kapsamında kartınızla 39,75 TL harcama yaptınız.",
            time
        )
        assertNotNull(item)
        assertEquals(3975L, item!!.amountKurus)
    }

    @Test fun refundIsNotCountedAsExpenseType() {
        val item = TransactionParser.parse("AKBANK", "Akbank: 39,75 TL iade işleminiz gerçekleştirildi.", time)
        assertNotNull(item)
        assertEquals("REFUND", item!!.transactionType)
    }

    @Test fun repeatedSmsHasSameFingerprint() {
        val a = TransactionParser.fingerprint("Akbank", "39,75 TL harcama yaptınız", time)
        val b = TransactionParser.fingerprint("AKBANK", "39,75 TL harcama yaptınız", time)
        assertEquals(a, b)
    }
}
