package app.kura.nativeapp

import app.kura.feature.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PreferenceParityTest {
    @Test fun legacyPreferencesRoundTripAndConstrainLayout() {
        val prefs=VaultPreferences(JSONObject().put("paymentsGridColumns",3).put("passesGridColumns",99)
            .put("identityGridDisplayMode","back").put("paymentsCategories","[\"Custom\",\"Travel\"]")
            .put("identityCustomFieldSchemas","[{\"name\":\"Renewal\",\"dataType\":\"date\"}]")
            .put("showBottomNavigationBar",false).put("passSearchStyle","icon")
            .put("maxBrightnessOnBarcodeView",true).put("defaultBarcodeOrientation","flipped").toString())
        assertEquals(3,prefs.columns("wallets"));assertEquals(3,prefs.columns("passes"))
        assertEquals("front",prefs.displayMode("identities")) // Removed Back view normalizes to Front Image.
        assertEquals(listOf("Custom","Travel"),prefs.categories("wallets"))
        assertEquals(CustomField("Renewal","date"),prefs.customFields("identities").single())
        assertFalse(prefs.bottomNavigation);assertTrue(prefs.barcodeBrightness);assertTrue(prefs.barcodeFlipped)
        assertEquals("icon",prefs.searchStyle)
        assertEquals(defaultCategories.getValue("passes"),VaultPreferences("""{"passesCategories":"malformed"}""").categories("passes"))
    }
    @Test fun expirySupportsLegacyCardAndIsoDatesWithoutInventingDates() {
        fun item(json:String)=VaultItem("wallets",1,"Test","","",false,json)
        assertEquals(LocalDate.of(2030,12,31),itemExpiry(item("""{"expiry":"1230"}""")))
        assertEquals(LocalDate.of(2030,12,31),itemExpiry(item("""{"expiry":"12/30"}""")))
        assertEquals(LocalDate.of(2028,2,29),itemExpiry(item("""{"expiry_date":"2028-02-29T12:00:00Z"}""")))
        assertNull(itemExpiry(item("""{"expiry_date":"invalid"}""")))
        assertNull(itemExpiry(item("""{"expiry":"1330"}""")))
    }
    @Test fun currencyFormattingHonorsPreferenceAndPreservesText() {
        assertTrue(moneyValue("12.5","USD").contains("12"))
        assertTrue(moneyValue("12.5","JPY").contains("13"))
        assertEquals("points",moneyValue("points","USD"))
    }
    @Test fun cropNormalizesCameraExifOrientation() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File.createTempFile("exif-test-",".jpg",context.cacheDir)
        val bitmap=android.graphics.Bitmap.createBitmap(80,40,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        try {
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it)}
            android.media.ExifInterface(file.path).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
            val raw=file.readBytes()
            try {
                val cropped=CapturedImageScanner.crop(raw,1f,.5f,.5f)
                try {
                    val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeByteArray(cropped,0,cropped.size,bounds)
                    assertEquals(40,bounds.outWidth);assertEquals(80,bounds.outHeight)
                } finally {cropped.fill(0)}
            } finally {raw.fill(0)}
        } finally {bitmap.eraseColor(0);bitmap.recycle();file.delete()}
    }
    @Test fun barcodeMappingDoesNotSilentlyChangeUnknownFormats() {
        assertEquals(com.google.zxing.BarcodeFormat.CODE_128,barcodeFormat("PKBarcodeFormatCode128"))
        assertEquals(com.google.zxing.BarcodeFormat.PDF_417,barcodeFormat("PDF417"))
        assertEquals(com.google.zxing.BarcodeFormat.DATA_MATRIX,barcodeFormat("Data Matrix"))
        assertNull(barcodeFormat("Telepen"))
    }
}
