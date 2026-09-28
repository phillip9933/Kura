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
