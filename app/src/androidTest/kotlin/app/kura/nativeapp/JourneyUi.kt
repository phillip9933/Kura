package app.kura.nativeapp

import android.os.Build
import android.view.Choreographer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synchronize coordinate input with Compose layout, not just accessibility events. */
internal fun settleJourneyUi(device: UiDevice) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    val frames = CountDownLatch(1)
    instrumentation.runOnMainSync {
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback { frames.countDown() }
        }
    }
    check(frames.await(5, TimeUnit.SECONDS)) { "UI frame publication did not finish" }
    device.waitForIdle()
    if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
}

/** Find an existing value by scrolling its pane, without clicking or editing it. */
internal fun findJourneyNode(device: UiDevice, selector: BySelector): UiObject2? {
    repeat(20) { attempt ->
        settleJourneyUi(device)
        device.findObject(selector)?.let { if (!it.visibleBounds.isEmpty) return it }
        val pane = device.findObject(By.desc("Crop adjustments").scrollable(true))
            ?: device.findObject(By.desc("Card review").scrollable(true))
            ?: device.findObject(By.scrollable(true))
        pane?.scroll(if (attempt < 10) Direction.DOWN else Direction.UP, .5f)
    }
    device.dumpWindowHierarchy(File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "journey-missing-value.xml"))
    return null
}

internal fun clickJourneyControl(device: UiDevice, label: String, beforeClick: ((UiObject2) -> Unit)? = null) {
    val overflowActions = setOf("Edit", "Delete", "Archive", "Unarchive", "Share securely", "Export encrypted pass", "Export as .pkpass")
    repeat(16) { attempt ->
        settleJourneyUi(device)
        try {
            val node = device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
            if (node != null && node.isEnabled && !node.visibleBounds.isEmpty) {
                beforeClick?.invoke(node)
                node.click()
                settleJourneyUi(device)
                return
            }
            // The detail screen may not have published its overflow control on the first lookup.
            if (label in overflowActions) {
                val menu = device.findObject(By.desc("More pass actions"))
                    ?: device.findObject(By.desc("More item actions"))
                if (menu != null) {
                    menu.click()
                    settleJourneyUi(device)
                    return@repeat
                }
            }
            val scroll = device.findObject(By.desc("Crop adjustments").scrollable(true))
                ?: device.findObject(By.scrollable(true))
            if (scroll != null) scroll.scroll(if (attempt < 8) Direction.DOWN else Direction.UP, .5f)
            else device.wait(Until.hasObject(By.text(label)), 500)
        } catch (_: StaleObjectException) {
            // Re-query after the layout changes; never replay a successful click.
        }
    }
    device.dumpWindowHierarchy(File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "journey-missing-control.xml"))
    error("Missing UI control: $label")
}

/** A previously discovered update can cover the vault after credential entry. */
internal fun waitForJourneyVault(device: UiDevice, timeoutMillis: Long = 20_000): Boolean {
    val deadline = android.os.SystemClock.uptimeMillis() + timeoutMillis
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        settleJourneyUi(device)
        if (device.hasObject(By.text("New version available"))) {
            device.findObject(By.text("Later"))?.click()
            settleJourneyUi(device)
        }
        if (device.wait(Until.hasObject(By.desc("Settings")), 500)) return true
    }
    return false
}
