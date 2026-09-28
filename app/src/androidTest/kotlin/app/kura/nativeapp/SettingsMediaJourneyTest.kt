package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsMediaJourneyTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private val context get()=instrumentation.targetContext
    private fun click(label:String) {
  if(label in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(label)) && !device.hasObject(By.desc(label))) {
   (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
  }
        if(label=="Settings") assertTrue(device.wait(Until.hasObject(By.desc("Settings")),10000))
        // Let a newly opened menu/dialog appear before attempting to scroll its parent.
        device.wait(Until.hasObject(By.desc(label)),750).let { found->
         if(!found) device.wait(Until.hasObject(By.text(label)),750)
        }
        var imageScrollAttempted=false
        repeat(10) {
            // API 37 can retain the pre-scroll dialog tree after the pixels have moved.
            // Refresh the automation cache, then locate and tap the actual visible control.
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            val target=device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
            try {if(target!=null && !target.visibleBounds.isEmpty) {
                // Bring image pencils away from the clipped bottom edge before tapping.
                if(!imageScrollAttempted && label.startsWith("Edit ") && target.visibleBounds.bottom>device.displayHeight*4/5 && scrollContent(false)) {
          imageScrollAttempted=true
                    device.waitForIdle()
                } else {if(!label.startsWith("Edit ") || !clickImageEdit(label)) target.click();device.waitForIdle();return}
            }} catch(_:StaleObjectException) {}
            scrollContent(label=="Enable Auto-Backup")
            device.waitForIdle()
        }
        device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"settings-media-missing.xml"))
        snapshot("settings-media-missing")
        error("Missing control: "+label)
    }
    private fun clickImageEdit(label:String):Boolean {
  // Invoke the pencil's semantic action after scrolling rather than relying on cached coordinates.
  fun find(node:android.view.accessibility.AccessibilityNodeInfo):Boolean {
   if(node.contentDescription?.toString()==label) {
    var target:android.view.accessibility.AccessibilityNodeInfo?=node
    while(target!=null) {
     if(target.isClickable) return target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
     target=target.parent
    }
   }
   for(index in 0 until node.childCount) {val child=node.getChild(index) ?: continue;if(find(child)) return true}
   return false
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::find) ?: false
 }
 private fun scrollContent(up: Boolean): Boolean {
        // Use a real gesture in the active dialog's viewport. Accessibility scroll actions
        // can acknowledge a request without moving this nested Compose dialog's content.
        val viewport = device.findObject(By.scrollable(true))?.visibleBounds ?: return false
        if (viewport.isEmpty) return false
        val x = viewport.centerX()
        val top = viewport.top + viewport.height() / 4
        val bottom = viewport.top + viewport.height() * 3 / 4
        return device.swipe(x, if (up) top else bottom, x, if (up) bottom else top, 30)
    }
    private fun authorize() {
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
        listOf("2","4","6","8").forEach {device.findObject(By.text(it).pkg("com.android.systemui")).click()}
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")),20000))
    }
    private fun activity():MainActivity {
        var result:MainActivity?=null
        instrumentation.runOnMainSync {result=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()}
        return result!!
    }
    private fun controller(activity:MainActivity):VaultController {
        var result:VaultController?=null
        instrumentation.runOnMainSync {result=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]}
        return result!!
    }
    private fun snapshot(name:String) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val windows=mutableListOf<Pair<android.view.View,Int>>()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach {view->
                val params=view.layoutParams as? android.view.WindowManager.LayoutParams ?: return@forEach
                windows.add(view to params.flags);params.flags=params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
            }
        }
        try {
            device.waitForIdle();Thread.sleep(200)
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null),name+".png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync {windows.forEach { (view,flags)->
                val params=view.layoutParams as android.view.WindowManager.LayoutParams;params.flags=flags
                view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
            }}
        }
    }
    @Test fun settingsFullscreenImagesArchiveAndAutomaticResume() {
        require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        device.wakeUp()
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        val owner=activity();val vm=controller(owner)
        val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
        var recordId=0L
        val displayName="Kura-image-"+java.util.UUID.randomUUID()+".png"
        val imageUri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/Kura-Test");put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)})!!
        val image=Bitmap.createBitmap(80,50,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        context.contentResolver.openOutputStream(imageUri)!!.use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        image.eraseColor(0);image.recycle()
        context.contentResolver.update(imageUri,android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.IS_PENDING,0)},null,null)
        try {
            runBlocking {
                vm.preference("isExpiryNotificationEnabled",false)
                vm.preference("maxBrightnessOnBarcodeView",true)
                vm.preference("defaultBarcodeOrientation","defaultOrientation")
                vm.preference("passesGridColumns",1)
                vm.work {opened->
                    vm.store.insert(opened,"passes",JSONObject().put("organizationName","UI media journey")
                        .put("type","generic").put("barcodeValue","KURA-MEDIA-123").put("barcodeFormat","PKBarcodeFormatQR"))
                    recordId=vm.store.rows(opened,"passes").single {it.optString("organizationName")=="UI media journey"}.getLong("id")
                };vm.refresh()
            }
            click("Settings")
            assertFalse(device.hasObject(By.text("Lock")));assertFalse(device.hasObject(By.text("Unlock vault")))
            assertTrue(device.hasObject(By.text("SECTION MANAGEMENT")))
            snapshot("settings-overhaul")
            click("General Display");click("App Theme");click("Dark");click("Done")
            instrumentation.runOnMainSync {
                assertFalse(androidx.core.view.WindowCompat.getInsetsController(owner.window,owner.window.decorView).isAppearanceLightNavigationBars)
                assertFalse(owner.window.isNavigationBarContrastEnforced)
            }
            click("Add")
            snapshot("floating-add-actions")
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"floating-add.xml"))
            assertTrue(device.hasObject(By.desc("Scan for Sharing or Import")))
            assertTrue(device.hasObject(By.desc("Import File")))
            assertTrue(device.hasObject(By.desc("Manual Input")))
            snapshot("floating-add-actions")
            click("Close add options")
            click("Passes")
            device.findObject(UiSelector().className("android.widget.EditText")).setText("UI media journey")
            click("Open UI media journey")
            assertFalse(device.hasObject(By.text("Full brightness")))
            click("Pass barcode")
            assertTrue(device.wait(Until.hasObject(By.desc("Fullscreen barcode")),10000))
            instrumentation.runOnMainSync {assertEquals(1f,owner.window.attributes.screenBrightness,.001f)}
            click("Rotate");click("QR Code");click("Code 128")
            assertTrue(device.wait(Until.hasObject(By.desc("Fullscreen barcode")),10000))
            snapshot("fullscreen-barcode")
            click("Close barcode")
            instrumentation.runOnMainSync {assertTrue(owner.window.attributes.screenBrightness<1f)}
            click("Edit Front image");click("Choose photo")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),10000) || device.hasObject(By.pkg("com.android.documentsui")))
            val picked=device.findObject(UiSelector().descriptionStartsWith(displayName))
            if(!picked.waitForExists(5000)) {
                val roots=device.findObject(UiSelector().description("Show roots"))
                assertTrue(roots.waitForExists(10000));roots.click()
                val images=device.findObject(UiSelector().text("Images"))
                assertTrue(images.waitForExists(10000));images.click()
                val folder=device.findObject(UiSelector().text("Kura-Test"))
                if(folder.waitForExists(5000)) folder.click()
                device.waitForIdle()
            }
            val exists=picked.waitForExists(10000)
            if(!exists) device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"image-picker.xml"))
            assertTrue("Published image fixture must appear in system picker",exists);picked.click()
            assertTrue(device.wait(Until.hasObject(By.text("Crop card image")),10000))
            click("Save image")
            var path:String?=null
            repeat(30) {
                if(path==null) {Thread.sleep(200);path=runBlocking {vm.work {opened->vm.store.rows(opened,"passes",recordId).single().optString("frontImagePath").takeUnless {it=="null" || it.isBlank()}}}}
            }
            assertNotNull("Image must be committed after confirmation",path)
            assertTrue(path!!.endsWith(".enc"))
            // The picker and crop keep the authenticated generation alive.
            assertTrue(vm.state.value is app.kura.nativecore.VaultState.Unlocked)
            click("View Front image")
            snapshot("front-image-viewer")
            click("Close image")
            click("Edit");click("Edit Back image");click("Choose photo")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),10000) || device.hasObject(By.pkg("com.android.documentsui")))
            repeat(4) {
                if(device.hasObject(By.pkg("com.google.android.documentsui")) || device.hasObject(By.pkg("com.android.documentsui"))) {
                    device.pressBack();device.waitForIdle()
                }
            }
            val returned=device.wait(Until.hasObject(By.desc("Edit Back image")),10000)
            if(!returned) device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"picker-return.xml"))
            assertTrue("Returning from picker must preserve detail screen",returned)
            assertTrue(vm.state.value is app.kura.nativecore.VaultState.Unlocked)
            click("Cancel");click("Open UI media journey")
            // Return to the detail toolbar.
            UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
            click("Archive")
            click("Settings");click("Archive")
            assertTrue(device.wait(Until.hasObject(By.text("UI media journey")),10000))
            click("Sort archive");click("Expiry");click("Reverse archive order")
            snapshot("sortable-archive")
            click("Restore UI media journey")
            assertTrue(device.wait(Until.gone(By.text("UI media journey")),10000))
            click("Back to vault")
            device.pressHome();Thread.sleep(1500)
            assertEquals(app.kura.nativecore.VaultState.Locked,vm.state.value)
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            authorize() // No unlock button was pressed.
            // Cancel one automatic attempt and verify it does not spin or relaunch.
            device.pressHome();Thread.sleep(1500)
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
            device.pressBack()
            device.wait(Until.findObject(By.text("OK")),5000)?.click()
            Thread.sleep(1500)
            assertFalse(device.hasObject(By.text("2").pkg("com.android.systemui")))
            assertTrue(device.hasObject(By.text("Privacy first pass wallet.")))
            assertFalse(device.hasObject(By.text("Unlock vault")))
            snapshot("locked-branding")
            click("Unlock Kura");authorize()
        } finally {
            if(vm.state.value is app.kura.nativecore.VaultState.Unlocked) runBlocking {
                vm.work {opened->vm.store.deleteRecord(opened,"passes",recordId);vm.store.saveSettings(opened,JSONObject(saved))}
                vm.presentation.value=PresentationSettings.parse(JSONObject(saved));vm.refresh()
            }
            context.contentResolver.delete(imageUri,null,null)
            device.pressHome();Thread.sleep(1200)
        }
    }
}
