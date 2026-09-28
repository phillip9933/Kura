package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class BackupJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private fun click(text: String) {
        if(text in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(text)) && !device.hasObject(By.desc(text))) {
            (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
        }
        if(text=="Settings") assertTrue(device.wait(Until.hasObject(By.desc("Settings")),10000))
        repeat(8) { attempt->
            val target = device.findObject(By.desc(text)) ?: device.findObject(By.text(text))
            try { if(target != null && !target.visibleBounds.isEmpty) {
                target.click(); device.waitForIdle(); return }
            } catch (_: StaleObjectException) {}
            val scroll = device.findObject(By.scrollable(true))
            if(scroll != null) scroll.scroll(if(attempt<4) Direction.DOWN else Direction.UP,.8f) else device.wait(Until.hasObject(By.text(text)),2000)
        }
        error("Missing UI: " + text)
    }
    private fun authorize() {
        // Real system credential UI; no auth bypass or test master key injection.
        val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000)
        assertNotNull("Expected system credential prompt", pin)
        for (digit in listOf("2", "4", "6", "8")) device.findObject(By.text(digit).pkg("com.android.systemui")).click()
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
    }

    private fun password() {
        val input = device.findObject(UiSelector().className("android.widget.EditText"))
        assertTrue(input.waitForExists(15000))
        input.setText("Synthetic backup test password")
        click("Continue")
    }
    @Test fun missingImagesWarnBeforeConfirmationAndCancelPreservesVault() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = instrumentation.targetContext
        device.wakeUp()
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        lateinit var owner: MainActivity
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            owner = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            vm = androidx.lifecycle.ViewModelProvider(owner)[VaultController::class.java]
        }
        val before = vm.store.active()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Kura-missing-image-${java.util.UUID.randomUUID()}.wbk")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        try {
            val backupPassword = "Synthetic backup test password".toCharArray()
            try {
                app.kura.nativecore.BackupPayload(org.json.JSONObject("""{"version":"4.0","passes":[{"organizationName":"Synthetic omitted logo","logoImagePath":"/old/logo.png.enc","barcodeValue":"TEST"}]}"""), emptyMap()).use { payload ->
                    context.contentResolver.openOutputStream(uri)!!.use { app.kura.nativecore.StreamingBackup.write(payload, it, backupPassword) }
                }
            } finally { backupPassword.fill('\u0000') }
            context.contentResolver.update(uri, android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            // Feed the real encrypted stream into the same dialog used after document selection.
            instrumentation.runOnMainSync {
                vm.pending.backupUri = uri
                vm.pending.passwordAction = "restore"
            }
            password()
            assertTrue(device.wait(Until.hasObject(By.text("Replace vault records?")), 20000))
            assertTrue(device.hasObject(By.textContains("missing 1 image reference")))
            assertEquals(before, vm.store.active())
            click("Cancel")
            assertEquals(before, vm.store.active())
            instrumentation.runOnMainSync {
                assertNull(vm.pending.restoreDirectory)
                assertEquals(0, vm.pending.restoreMissingImages)
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
            instrumentation.runOnMainSync { owner.finish() }
            device.pressHome()
        }
    }

    @Test fun systemDocumentExportAndConfirmedRestoreRoundTrip() {
        device.wakeUp()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        removeUiGalleryFixtures()
        click("Settings"); click("Load synthetic fixtures")
        click("Settings"); click("Backup & Storage"); click("Create Backup"); password()
        val filename = "Kura-test-" + java.util.UUID.randomUUID() + ".wbk"
        val name = device.findObject(UiSelector().resourceId("android:id/title").className("android.widget.EditText"))
        assertTrue("Expected system CreateDocument", name.waitForExists(20000))
        name.setText(filename)
        val save = device.findObject(UiSelector().textMatches("(?i)save").clickable(true))
        assertTrue(save.waitForExists(5000)); save.click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")),15000))
        click("Settings"); click("Backup & Storage"); click("Restore Backup"); click("Choose backup file")
        val document = device.findObject(UiSelector().text(filename))
        assertTrue("Expected exported document in picker", document.waitForExists(15000))
        document.click()
        assertTrue(device.wait(Until.hasObject(By.text("Decrypt backup")),15000))
        password()
        assertTrue(device.wait(Until.hasObject(By.text("Replace vault records?")),20000))
        click("Restore"); authorize()
        click("Passes")
        assertTrue(device.wait(Until.hasObject(By.text("Kura Test Transit 0")),15000))
        device.findObject(UiSelector().description("Open Kura Test Transit 0")).click()
        click("Export encrypted pass")
        assertTrue(device.wait(Until.hasObject(By.text("Encrypt pass")),5000))
        password()
        val passFilename="Kura-pass-test-"+java.util.UUID.randomUUID()+".wbk"
        val passName=device.findObject(UiSelector().resourceId("android:id/title").className("android.widget.EditText"))
        assertTrue(passName.waitForExists(20000));passName.setText(passFilename)
        val passSave=device.findObject(UiSelector().textMatches("(?i)save").clickable(true))
        assertTrue(passSave.waitForExists(5000));passSave.click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")),15000))
        click("Settings");click("Backup & Storage"); click("Restore Backup"); click("Choose backup file")
        val passDocument=device.findObject(UiSelector().text(passFilename))
        assertTrue(passDocument.waitForExists(15000));passDocument.click()
        password()
        assertTrue(device.wait(Until.hasObject(By.text("Import 1 item(s)?")),20000))
        assertFalse(device.hasObject(By.text("Replace vault records?")))
        click("Import")
        val controller=arrayOfNulls<VaultController>(1)
        instrumentation.runOnMainSync {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            controller[0]=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]
        }
        kotlinx.coroutines.runBlocking {
            controller[0]!!.work { opened ->
                val passes=controller[0]!!.store.rows(opened,"passes")
                val copies=passes.filter {it.optString("organizationName")=="Kura Test Transit 0"}
                assertEquals(2,copies.size)
                assertTrue("Other passes must remain",passes.any {it.optString("organizationName")=="Kura Test Transit 1"})
                // Delete only the newly imported synthetic copy; retain the original fixture.
                controller[0]!!.store.deletePass(opened,copies.maxOf {it.getLong("id")})
            }
            controller[0]!!.refresh()
        }
        device.pressHome(); Thread.sleep(1200)

    }
}
