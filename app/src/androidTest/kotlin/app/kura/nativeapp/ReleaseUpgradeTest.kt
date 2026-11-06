package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.kura.nativecore.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Two invocations around adb install -r. Prototype identity and generated fixtures only. */
class ReleaseUpgradeTest {
    @Test fun retainedSyntheticWalletAcrossPrototypeUpgrade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("releaseUpgradePhase")
        org.junit.Assume.assumeTrue(phase in setOf("seed", "verify"))
        val context = instrumentation.targetContext
        val phoneOnly = InstrumentationRegistry.getArguments().getString("releaseUpgradeAccess") == "phone"
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (phase == "verify" && phoneOnly) {
            assertTrue("Upgraded phone-only vault must open without an app prompt",
                waitForJourneyVault(device))
            assertFalse(device.hasObject(By.text("2").pkg("com.android.systemui")))
            assertTrue(phoneAccessEnabled(context))
        } else {
            val prompt = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15_000)
            assertNotNull("Expected real synthetic-credential prompt", prompt)
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            val visible = waitForJourneyVault(device)
            if (!visible) device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "upgrade-entry-failure.xml"))
            assertTrue("Vault controls must appear after real credential entry", visible)
        }
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            vm = ViewModelProvider(activity)[VaultController::class.java]
        }
        if (phase == "seed") {
            runBlocking {
                vm.work { opened ->
                    check(vm.store.rows(opened, "passes").none { it.optString("organizationName") == SENTINEL })
                    val bitmap = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888)
                    val output = ByteArrayOutputStream()
                    val encoded = try {
                        bitmap.eraseColor(Color.rgb(42, 96, 144)); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        output.toByteArray()
                    } finally { bitmap.eraseColor(0); bitmap.recycle(); output.close() }
                    try {
                        val media = EncryptedMediaStorage(File(opened.directory, "media"))
                        val file = media.write("release-upgrade-sentinel.png.enc", encoded, opened.key)
                        val fields = JSONObject().put("customFields", JSONObject().put("Member number", "00009901"))
                        vm.store.insert(opened, "passes", JSONObject().put("organizationName", SENTINEL).put("type", "generic")
                            .put("frontImagePath", file.name).put("fields", fields.toString()))
                        val settings = vm.store.settings(opened).put("selectedCurrencyCode", "JPY")
                        vm.store.saveSettings(opened, settings)
                    } finally { encoded.fill(0) }
                }
            }
            if (phoneOnly) {
                clickJourneyControl(device, "Settings")
                clickJourneyControl(device, "Require app unlock")
                clickJourneyControl(device, "Use phone unlock")
                val deadline = android.os.SystemClock.uptimeMillis() + 10000
                while (!phoneAccessEnabled(context) && android.os.SystemClock.uptimeMillis() < deadline)
                    android.os.SystemClock.sleep(25)
                assertTrue("Phone-only wrapper must be published before upgrade", phoneAccessEnabled(context))
            }
        } else {
            runBlocking {
                var image = ""
                vm.work { opened ->
                    val row = vm.store.rows(opened, "passes").single { it.optString("organizationName") == SENTINEL }
                    assertEquals("00009901", JSONObject(row.getString("fields")).getJSONObject("customFields").getString("Member number"))
                    assertEquals("JPY", vm.store.settings(opened).getString("selectedCurrencyCode"))
                    image = row.getString("frontImagePath")
                    val encrypted = File(opened.directory, "media/$image").inputStream().use { stream -> ByteArray(8).also { stream.read(it) } }
                    try { assertFalse(encrypted.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) }
                    finally { encrypted.fill(0) }
                }
                val plain = vm.image(image)
                try {
                    val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                    assertNotNull(bitmap)
                    try { assertEquals(40, bitmap.width); assertEquals(24, bitmap.height); assertEquals(Color.rgb(42, 96, 144), bitmap.getPixel(0, 0)) }
                    finally { if (bitmap.isMutable) bitmap.eraseColor(0); bitmap.recycle() }
                } finally { plain.fill(0) }
            }
        }
    }
    private fun phoneAccessEnabled(context: android.content.Context) = app.kura.nativecore.DeviceUnlock(context, true).enabled
    companion object { private const val SENTINEL = "Release upgrade sentinel 2.1" }
}
