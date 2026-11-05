package app.kura.nativeapp

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import io.github.phillip9933.appupdater.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.regex.Pattern

/** Explicit opt-in, generated APKs and disposable prototype only. Never bypasses APK verification. */
class InstallerAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val app get() = context.applicationContext as KuraUpdateApplication
    private val manager get() = app.updateManager
    private val args get() = InstrumentationRegistry.getArguments()
    private val fixtures get() = File(context.getExternalFilesDir(null), "installer-fixtures")
    private val version get() = args.getString("payloadVersion") ?: "2.2.0-validation.1"
    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue(message, predicate())
    }
    private fun guard() {
        check(BuildConfig.PROTOTYPE && context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(device.executeShellCommand("getconf PAGE_SIZE").trim() == "16384")
    }
    @Suppress("DEPRECATION") private fun installedCode() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun release(file: File) = AndroidUpdateRelease("v$version", "Generated installer acceptance fixture",
        "Kura-$version.apk", "https://github.com/phillip9933/Kura/releases/download/v$version/Kura-$version.apk",
        file.length(), sha(file), null)
    private fun setField(name: String, value: Any) {
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }
    private fun transport(file: File, mode: String = "normal") {
        // Fault transport only. The unchanged SDK still streams, hashes and verifies the real APK.
        setField("client", OkHttpClient.Builder().addInterceptor { chain ->
            val source = object : ForwardingSource(file.inputStream().source()) {
                var count = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (mode == "interrupted" && count >= 65536) throw IOException("Synthetic interrupted transfer")
                    if (mode == "slow") Thread.sleep(10)
                    val n = super.read(sink, minOf(byteCount, 16384)); if (n > 0) count += n
                    return n
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength() = file.length()
                override fun source(): BufferedSource = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body).build()
        }.build())
    }
    private fun offer(file: File, mode: String = "normal") {
        manager.setAutomaticChecks(false); manager.setWifiDownload(false)
        manager.setChannel(AndroidUpdateChannel.BETA)
        val fixture = release(file)
        setField("latestRelease", { _: AndroidUpdateChannel -> fixture })
        transport(file, mode)
        runBlocking { assertTrue(manager.checkNow()) }
        assertEquals(fixture, manager.state.value.available)
    }
    private fun checkpoint(message: String) {
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Fixture check passed: $message\n") })
    }
    private fun assertRejected(error: UpdateError) {
        assertEquals(error, manager.state.value.error)
        assertFalse(manager.state.value.ready)
        assertFalse(File(context.cacheDir, "updates/update.part").exists())
        runBlocking { assertNull(manager.prepareInstall()) }
        assertEquals(120L, installedCode())
    }
    private fun openHost(normalPrototypeEntry: Boolean = false) {
        device.wakeUp()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 5000)
        if (pin != null) {
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
        assertTrue(waitForJourneyVault(device))
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            // The production entry retains its prototype prohibition. Only this guarded test
            // host renders the same dialogs with installation enabled for same-package fixtures.
            app.updatePanelVisible.value = true
            if (!normalPrototypeEntry) activity.setContent { AppUpdateDialogs(app, installationAllowed = true) }
        }
        assertNotNull(findUpdateControl())
    }
    private fun findUpdateControl(): UiObject2? {
        repeat(6) {
            settleJourneyUi(device)
            device.findObject(By.text("Install update"))?.let { return it }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .5f)
        }
        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "installer-dialog-missing.xml"))
        return null
    }
    private fun returnToDialog(fromInstaller: Boolean = false) {
        if (fromInstaller) {
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "installer-cancel-transition.xml"))
            assertTrue("Cancelled installer must close", device.wait(Until.gone(By.pkg(Pattern.compile(".*packageinstaller.*"))), 5000))
        }
        val deadline = SystemClock.uptimeMillis() + 20000
        var entered = false
        while (SystemClock.uptimeMillis() < deadline) {
            assertFalse("Permission/installer return must not launch an installer automatically",
                device.hasObject(By.pkg(Pattern.compile(".*packageinstaller.*"))))
            if (!entered && device.hasObject(By.text("2").pkg("com.android.systemui"))) {
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "installer-return-auth.xml"))
                listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
                device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
                entered = true
            }
            if (device.hasObject(By.text("Install update"))) { assertNotNull(findUpdateControl()); return }
            SystemClock.sleep(50)
        }
        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "installer-return-missing.xml"))
        fail("Update dialog must remain available after the external screen and real reauthentication")
    }
    private fun click(label: String) = clickJourneyControl(device, label)
    private fun installerPrompt() {
        assertNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)cancel")).pkg(Pattern.compile(".*packageinstaller.*"))), 15000))
    }
    @Test fun installerAcceptance() {
        val phase = args.getString("installerPhase")
        org.junit.Assume.assumeTrue(phase != null)
        guard()
        when (phase) {
            "faults" -> {
                for ((name, error) in listOf("wrong-package.apk" to UpdateError.WRONG_PACKAGE,
                    "wrong-signer.apk" to UpdateError.SIGNATURE_MISMATCH, "debug.apk" to UpdateError.DEBUG_APK)) {
                    offer(File(fixtures, name)); runBlocking { manager.download() }; assertRejected(error)
                    checkpoint(name)
                }
                offer(File(fixtures, "candidate.apk"), "interrupted")
                runBlocking { manager.download() }; assertRejected(UpdateError.DOWNLOAD_FAILED)
                checkpoint("interrupted transfer cleanup")
                offer(File(fixtures, "candidate.apk"), "slow")
                runBlocking {
                    val job = async(Dispatchers.IO) { manager.download() }
                    waitFor("Transfer must start before cancellation") {
                        manager.state.value.downloading && manager.state.value.bytesDownloaded > 0
                    }
                    manager.cancelDownload(); job.await()
                }
                assertRejected(UpdateError.CANCELLED)
                checkpoint("cancelled active transfer cleanup")
            }
            "offline" -> {
                val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                waitFor("Offline test requires Android to report no active network") { connectivity.activeNetwork == null }
                manager.setAutomaticChecks(false)
                runBlocking { manager.checkNow() }
                assertEquals(UpdateError.NETWORK, manager.state.value.error)
                assertFalse(manager.state.value.checking)
                assertFalse(manager.state.value.ready)
                assertEquals(120L, installedCode())
            }
            "ready-seed" -> {
                offer(File(fixtures, "candidate.apk")); runBlocking { manager.download() }
                assertTrue("Verified fixture must become ready: ${manager.state.value.error}", manager.state.value.ready)
                assertNotNull(runBlocking { manager.prepareInstall() })
            }
            "ready-verify" -> {
                waitFor("Verified candidate must restore after process death") { manager.state.value.ready }
                val intent = runBlocking { manager.prepareInstall() }
                assertNotNull(intent); assertEquals("${context.packageName}.android-updates", intent!!.data!!.authority)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertFalse(context.packageManager.resolveContentProvider(intent.data!!.authority!!, 0)!!.exported)
            }
            "prototype-prohibition" -> {
                waitFor("Ready fixture before prototype guard") { manager.state.value.ready }
                openHost(normalPrototypeEntry = true)
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "prototype-install-guard.xml"))
                var control: UiObject2? = findUpdateControl()
                var disabled = false
                val states = mutableListOf<Boolean>()
                repeat(5) { control?.let { node ->
                    states += node.isEnabled
                    disabled = disabled || !node.isEnabled
                    control = node.parent
                } }
                assertTrue("Normal prototype Install control must be disabled; ancestry=$states", disabled)
                assertFalse(device.hasObject(By.pkg(Pattern.compile(".*packageinstaller.*"))))
            }
            "permission" -> {
                waitFor("Ready candidate before permissions") { manager.state.value.ready }
                assertFalse(context.packageManager.canRequestPackageInstalls())
                openHost(); click("Install update")
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10000))
                device.pressBack()
                returnToDialog()
                assertFalse(context.packageManager.canRequestPackageInstalls())
                assertEquals(120L, installedCode())
                click("Install update")
                val permission = device.wait(Until.findObject(By.checkable(true).pkg("com.android.settings")), 10000)
                assertNotNull("Real Android install-source permission switch", permission)
                permission!!.click(); device.pressBack()
                returnToDialog()
                assertTrue(context.packageManager.canRequestPackageInstalls())
                assertFalse(device.hasObject(By.pkg(Pattern.compile(".*packageinstaller.*"))))
                assertEquals("Permission return must not install", 120L, installedCode())
                click("Install update"); installerPrompt()
                device.findObject(By.text(Pattern.compile("(?i)cancel")).pkg(Pattern.compile(".*packageinstaller.*"))).click()
                returnToDialog(fromInstaller = true)
                assertEquals("Installer cancellation must not claim success", 120L, installedCode())
                assertTrue(manager.state.value.ready)
            }
            "handoff" -> {
                waitFor("Ready candidate before handoff") { manager.state.value.ready }
                assertTrue(context.packageManager.canRequestPackageInstalls())
                openHost(); click("Install update"); installerPrompt()
                // The independent host driver confirms installation and checks the new package.
            }
            "corrupt-seed" -> context.getSharedPreferences(kuraUpdateConfig.stateName, 0).edit()
                .putString("automatic", "true").commit().also { assertTrue(it) }
            "corrupt-verify" -> {
                assertFalse(manager.state.value.automaticChecks)
                assertFalse(context.getSharedPreferences(kuraUpdateConfig.stateName, 0).getBoolean("automatic", true))
            }
            "installed" -> {
                waitFor("Installed version must come from PackageManager") { manager.state.value.installedVersion == version }
                assertEquals(121L, installedCode())
                assertNull("Installed candidate must not remain offered", manager.state.value.available)
                assertFalse(manager.state.value.ready)
            }
            "corrupt-cache" -> {
                offer(File(fixtures, "candidate.apk")); runBlocking { manager.download() }; assertTrue(manager.state.value.ready)
                val candidate = File(context.cacheDir, "updates").listFiles()!!.single { it.extension == "apk" }
                candidate.appendBytes(byteArrayOf(1))
                assertNull(runBlocking { manager.prepareInstall() }); assertFalse(manager.state.value.ready)
                runBlocking { manager.download() }
                assertTrue("Fresh download must recover a corrupt cached APK: ${manager.state.value.error}", manager.state.value.ready)
            }
            else -> error("Unknown installer phase")
        }
    }
}
