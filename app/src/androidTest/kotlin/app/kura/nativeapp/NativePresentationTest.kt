package app.kura.nativeapp

import com.google.zxing.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativePresentationTest {
    @Test fun nativeDecoderRecognizesAllFourBarcodeFormats() {
        for(format in listOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417, BarcodeFormat.CODE_128, BarcodeFormat.DATA_MATRIX, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93)) {
            val matrix = MultiFormatWriter().encode("KURA-123456", format, 640, 480)
            val luma = ByteArray(matrix.width*matrix.height) { i -> if(matrix[i%matrix.width,i/matrix.width]) 0 else 255.toByte() }
            try { assertEquals("KURA-123456", NativeBarcodeDecoder.decode(luma,matrix.width,matrix.height)?.text) }
            finally { luma.fill(0) }
        }
    }
    @Test fun sharedQrPayloadsDecodeExactlyAsDisplayed() {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val payloads = assets.open("qr-payloads.json").bufferedReader().use { org.json.JSONArray(it.readText()) }
        assertTrue(payloads.length() > 0)
        for (index in 0 until payloads.length()) {
            val payload = payloads.getString(index)
            val bitmap = requireNotNull(app.kura.feature.renderBarcode(payload, "QR Code"))
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val luminance = ByteArray(pixels.size) { pixels[it].toByte() }
                assertEquals(payload, NativeBarcodeDecoder.decode(luminance, bitmap.width, bitmap.height)?.text)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun restoredSettingsMapLegacyThemeAndTabsWithoutAuthenticationBypass() {
        val settings = PresentationSettings.parse(JSONObject("""{"themePreference":1,"defaultScreenIndex":2,"showPaymentsTab":false,"showIdentityTab":true,"showAuthenticationScreen":false}"""))
        assertEquals(1,settings.theme); assertEquals("identities",settings.initialTable); assertFalse("wallets" in settings.visibleTables)
        assertEquals(setOf("passes"),PresentationSettings.parse(JSONObject("""{"showPaymentsTab":false,"showPassesTab":false,"showIdentityTab":false}""")).visibleTables)
    }
    @Test fun currencyFieldsFormatAndInvalidDatesRemainReadable() {
        assertTrue(app.kura.feature.formattedField(JSONObject("""{"value":12.5,"currencyCode":"USD"}""")).contains("12"))
        assertEquals("not a date",app.kura.feature.formattedField(JSONObject("""{"value":"not a date","dateStyle":"PKDateStyleShort"}""")))
    }
}
