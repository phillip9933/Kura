package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SectionJourneyTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private fun click(label:String) {
  if(label in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(label)) && !device.hasObject(By.desc(label))) {
   (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
  }
        val target=device.wait(Until.findObject(By.desc(label)),1500) ?: device.wait(Until.findObject(By.text(label)),5000)
        assertNotNull("Missing "+label,target);target!!.click();device.waitForIdle()
    }
    private fun snapshot(name:String) {
        val context=instrumentation.targetContext
        val windows=mutableListOf<Pair<android.view.View,Int>>()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach {view->
                val params=view.layoutParams as? android.view.WindowManager.LayoutParams ?: return@forEach
                windows.add(view to params.flags)
                params.flags=params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
            }
        }
        try {
            device.waitForIdle();Thread.sleep(200)
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null),name+".png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync {windows.forEach {(view,flags)->
                val params=view.layoutParams as android.view.WindowManager.LayoutParams
                params.flags=flags;view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
            }}
        }
    }
    @Test fun sectionsSwipeClassifyFilterAndHideEmptyUpcoming() {
        require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val context=instrumentation.targetContext
        device.wakeUp()
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
        device.waitForIdle()
        fun credentialTap(selector:BySelector) {
            repeat(3) {
                val control=device.wait(Until.findObject(selector),5000) ?: error("Credential control unavailable")
                try {control.click();return} catch(_:StaleObjectException) {device.waitForIdle()}
            }
            error("Credential control did not stabilize")
        }
        listOf("2","4","6","8").forEach {credentialTap(By.text(it).pkg("com.android.systemui"))}
        credentialTap(By.desc("Enter").pkg("com.android.systemui"))
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")),20000))
        var vm:VaultController?=null
        instrumentation.runOnMainSync {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            vm=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]
        }
        val controller=vm!!
        val saved=runBlocking {controller.work {controller.store.settings(it).toString()}}
        val marker="Section fixture "+java.util.UUID.randomUUID().toString().take(8)
        val ids=mutableListOf<Long>()
        try {
            runBlocking {
                controller.preference("defaultScreenIndex",1)
                controller.preference("showPaymentsTab",true);controller.preference("showPassesTab",true);controller.preference("showIdentityTab",true)
                controller.preference("showBottomNavigationBar",true);controller.preference("gestureNavigationEnabled",true)
                controller.preference("isPassSearchEnabled",true);controller.preference("passSearchStyle","alwaysOn")
                controller.preference("showUpcomingPasses",true)
                controller.preference("isExpiryNotificationEnabled",false)
                controller.preference("paymentsCategories",org.json.JSONArray(app.kura.feature.defaultCategories.getValue("wallets")).toString())
                controller.preference("paymentsGridColumns",1);controller.preference("passesGridColumns",1)
                controller.work {vault->
                    for((type,suffix) in listOf("loyaltyCard" to "Loyalty","eventTicket" to "Event","corporateBadge" to "Identity")) {
                        val row=JSONObject().put("organizationName",marker+" "+suffix).put("type",type).put("barcodeValue","SYNTHETIC").put("barcodeFormat","PKBarcodeFormatQR")
                        if(type=="eventTicket") row.put("relevantDate","2099-01-01T12:00:00Z")
                        controller.store.insert(vault,"passes",row)
                        ids+=controller.store.rows(vault,"passes").single {it.optString("organizationName")==marker+" "+suffix}.getLong("id")
                    }
                }
                controller.refresh()
            }
            click("Passes")
            fun search() {device.findObject(UiSelector().className("android.widget.EditText")).setText(marker);device.waitForIdle()}
            search()
            assertTrue(device.wait(Until.hasObject(By.desc("Open "+marker+" Event")),10000))
            assertFalse(device.hasObject(By.desc("Open "+marker+" Loyalty")))
            click("Upcoming")
            snapshot("sections-upcoming")
            assertTrue(device.hasObject(By.desc("Open "+marker+" Event")))
            // Right swipe goes from center Passes to Cards; left returns to center.
            device.swipe(device.displayWidth/5,device.displayHeight/2,device.displayWidth*4/5,device.displayHeight/2,25)
            assertTrue(device.wait(Until.hasObject(By.text("Search cards...")),5000))
            search()
            assertTrue(device.wait(Until.hasObject(By.desc("Open "+marker+" Loyalty")),5000))
            assertFalse(device.hasObject(By.desc("Open "+marker+" Event")))
            assertFalse(device.hasObject(By.text("Upcoming")))
            snapshot("sections-cards")
            click("All Categories")
            assertFalse(device.hasObject(By.text("Membership"))) // configured, but no active membership fixture
            device.pressBack()
            click("Open "+marker+" Loyalty")
            click("Pass barcode")
            assertTrue(device.wait(Until.hasObject(By.desc("Fullscreen barcode")),5000))
            click("Close barcode")
            click("More pass actions");click("Edit");click("Choose Category");click("Library");click("Save")
            runBlocking {withTimeout(10000) {controller.items.first {list->
                list.any {it.title==marker+" Loyalty" && it.displayCategory=="Library" && it.section.key=="wallets"}
            }}}
            val edited=controller.items.value.single {it.title==marker+" Loyalty"}
            assertEquals("loyaltyCard",JSONObject(edited.json).getString("type"))
            assertEquals("SYNTHETIC",JSONObject(edited.json).getString("barcodeValue"))
            device.swipe(device.displayWidth*4/5,device.displayHeight/2,device.displayWidth/5,device.displayHeight/2,25)
            assertTrue(device.wait(Until.hasObject(By.text("Search passes...")),5000))
            click("Identity");search()
            assertTrue(device.wait(Until.hasObject(By.desc("Open "+marker+" Identity")),5000))
            snapshot("sections-identity")
            click("Passes")
            runBlocking {controller.preference("showUpcomingPasses",false)}
            device.waitForIdle();Thread.sleep(400)
            assertFalse(device.hasObject(By.text("Upcoming")))
            // Isolated empty-list binding verifies automatic hiding, without deleting any existing fixture.
            val snapshot=controller.items.value
            instrumentation.runOnMainSync {controller.items.value=emptyList()}
            runBlocking {controller.preference("showUpcomingPasses",true)}
            device.waitForIdle();Thread.sleep(400)
            assertFalse(device.hasObject(By.text("Upcoming")))
            instrumentation.runOnMainSync {controller.items.value=snapshot}
        } finally {
            runBlocking {
                controller.work {vault->ids.forEach {controller.store.deleteRecord(vault,"passes",it)}
                    controller.store.saveSettings(vault,JSONObject(saved))}
                controller.presentation.value=PresentationSettings.parse(JSONObject(saved));controller.refresh()
            }
            device.pressHome()
        }
    }
}
