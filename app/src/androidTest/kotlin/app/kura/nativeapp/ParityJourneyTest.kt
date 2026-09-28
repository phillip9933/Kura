package app.kura.nativeapp
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.nativecore.*
import app.kura.feature.stableKey
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ParityJourneyTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private val device=UiDevice.getInstance(instrumentation)
 private fun click(label:String) {
  if(label in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(label)) && !device.hasObject(By.desc(label))) {
   (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
  }
  // Let a newly opened menu/dialog appear before attempting to scroll its parent.
  device.wait(Until.hasObject(By.desc(label)),750).let { found->
   if(!found) device.wait(Until.hasObject(By.text(label)),750)
  }
  var imageScrollAttempted=false
  repeat(12) {
   val node=device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
   try {if(node!=null && !node.visibleBounds.isEmpty) {
    // Bring image pencils away from the clipped bottom edge before tapping.
    if(!imageScrollAttempted && label.startsWith("Edit ") && node.visibleBounds.bottom>device.displayHeight*4/5 && scrollContent(false)) {
    imageScrollAttempted=true
     device.waitForIdle()
    } else {if(!label.startsWith("Edit ") || !clickImageEdit(label)) node.click();device.waitForIdle();return}
   }} catch(_:StaleObjectException) {}
   scrollContent(label=="Enable Auto-Backup")
   device.waitForIdle()
  }
  device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"parity-missing-control.xml"))
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
 private fun scrollContent(up:Boolean):Boolean {
     fun scroll(node:android.view.accessibility.AccessibilityNodeInfo):Boolean {
         if(node.className=="android.widget.ScrollView" && node.isScrollable) {
             val accepted=node.performAction(if(up) android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                 else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                // Compose scroll actions animate asynchronously; do not restart the same animation in a tight loop.
                if(accepted) Thread.sleep(400)
                return accepted
         }
         for(index in 0 until node.childCount) {
             val child=node.getChild(index) ?: continue
             if(scroll(child)) return true
         }
         return false
     }
     return instrumentation.uiAutomation.rootInActiveWindow?.let(::scroll) ?: false
 }
 private fun start():VaultController {
  check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
  device.wakeUp();context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
  listOf("2","4","6","8").forEach {device.findObject(By.text(it).pkg("com.android.systemui")).click()}
  device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
  assertTrue(device.wait(Until.hasObject(By.desc("Settings")),20000))
  var controller:VaultController?=null
  instrumentation.runOnMainSync {
   val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
   controller=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]
  }
  return controller!!
 }
 private fun snapshot(name:String) {
  check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
  val windows=mutableListOf<Pair<android.view.View,Int>>()
  var activityWindow:android.view.Window?=null;var secure=false
  instrumentation.runOnMainSync {
   activityWindow=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single().window
   secure=activityWindow!!.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0
   activityWindow!!.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
   android.view.inspector.WindowInspector.getGlobalWindowViews().forEach {view->
   val params=view.layoutParams as? android.view.WindowManager.LayoutParams ?: return@forEach
   windows.add(view to params.flags);params.flags=params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE.inv()
   view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
  }}
  try {device.waitForIdle();val bitmap=instrumentation.uiAutomation.takeScreenshot()
   java.io.File(context.getExternalFilesDir(null),name+".png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   bitmap.recycle()
  } finally {instrumentation.runOnMainSync {windows.forEach { (view,flags)->
   val params=view.layoutParams as android.view.WindowManager.LayoutParams;params.flags=flags
   view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
  };if(secure) activityWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}}
 }
 @Test fun mixedSectionRenameOrderDraftImagesAndSecureSharing() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val names=listOf("Parity payment","Parity loyalty")
  val ids=mutableListOf<Pair<String,Long>>()
  try {
   runBlocking {
    vm.preference("autoBackupEnabled",false)
    vm.preference("isExpiryNotificationEnabled",false)
    vm.saveRecord("wallets",JSONObject().put("name",names[0]).put("category","Loyalty").toString())
    vm.saveRecord("passes",JSONObject().put("organizationName",names[1]).put("type","storeCard").put("barcodeValue","123456").put("fields",JSONObject().put("backFields",org.json.JSONArray().put(JSONObject().put("label","Terms").put("value","A".repeat(2200)))).toString()).toString())
    val payment=vm.items.value.single {it.title==names[0]};val loyalty=vm.items.value.single {it.title==names[1]}
    ids+=payment.table to payment.id;ids+=loyalty.table to loyalty.id
    vm.preference("renameCategory:wallets",JSONObject().put("old","Loyalty").put("name","Club").toString())
    assertTrue(vm.items.value.filter {it.title in names}.all {it.displayCategory=="Club" && it.section.key=="wallets"})
    vm.saveOrder("wallets",listOf(loyalty.stableKey(),payment.stableKey()));assertEquals(names[1],vm.items.value.first {it.title in names}.title)
    vm.refresh();assertEquals(names[1],vm.items.value.first {it.title in names}.title)
    val bitmap=android.graphics.Bitmap.createBitmap(8,5,android.graphics.Bitmap.Config.ARGB_8888)
    val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.eraseColor(0);bitmap.recycle()
    val bytes=output.toByteArray()
    try {vm.imageChange(ImageTarget("identities",-1,"frontImagePath"),bytes);vm.imageChange(ImageTarget("identities",-1,"backImagePath"),bytes)}
    finally {bytes.fill(0)}
    val draft=vm.pending.takeDraft();assertEquals(2,draft.size)
    vm.saveRecord("identities",JSONObject().put("name","Parity draft identity").toString(),draft)
    val identity=vm.items.value.single {it.title=="Parity draft identity"};ids+=identity.table to identity.id
    assertTrue(JSONObject(identity.json).getString("frontImagePath").endsWith(".enc"));assertTrue(draft.values.all {it.isFile})
   }
   click("Cards")
   device.findObject(UiSelector().className("android.widget.EditText")).setText("Parity")
   device.wait(Until.findObject(By.desc("Open "+names[1])),5000)!!.longClick();click("Sort / Reorder")
   assertTrue(device.wait(Until.hasObject(By.desc("Reorder "+names[1])),5000))
   device.waitForIdle()
   click("Reorder "+names[1])
   assertTrue("First tap must select the source",device.wait(Until.hasObject(By.textStartsWith("Tap a destination for "+names[1])),5000))
   click("Reorder "+names[0])
   device.waitForIdle()
   click("Save order")
   val deadline=System.currentTimeMillis()+5000
   while(vm.items.value.first {it.title in names}.title!=names[0] && System.currentTimeMillis()<deadline) Thread.sleep(50)
   assertEquals(names[0],vm.items.value.first {it.title in names}.title)
   snapshot("parity-reorder")
   device.findObject(UiSelector().className("android.widget.EditText")).setText(names[1])
   click("Open "+names[1]);click("More pass actions");click("Share securely")
   assertTrue(device.wait(Until.hasObject(By.text("Set transfer password")),5000))
   device.findObject(UiSelector().className("android.widget.EditText")).setText("Kura parity password");click("Continue")
   assertTrue("Share failed: "+vm.error.value,device.wait(Until.hasObject(By.desc("Encrypted transfer code")),30000))
   snapshot("parity-secure-sharing")
   assertTrue(device.hasObject(By.text("Next")));click("Next");click("Play slideshow");click("Pause slideshow")
   val chunks=vm.pending.sharedChunks!!
   click("Close secure sharing")
   instrumentation.runOnMainSync {vm.pending.transferInput=chunks}
   assertTrue(device.wait(Until.hasObject(By.text("Enter transfer password")),5000))
   device.findObject(UiSelector().className("android.widget.EditText")).setText("Kura parity password");click("Continue")
   assertTrue(device.wait(Until.hasObject(By.text("Import "+names[1]+"?")),30000));click("Cancel")
   assertEquals(1,vm.items.value.count {it.title==names[1]})
   click("Close")
   click("Settings");click("Backup & Storage");click("Delete All Data")
   assertTrue(device.wait(Until.hasObject(By.text("Delete All Data?")),5000));click("Cancel")
   assertTrue(vm.state.value is VaultState.Unlocked)
  } finally {
   runBlocking {vm.work {opened->ids.forEach {vm.store.deleteRecord(opened,it.first,it.second)};vm.store.saveSettings(opened,JSONObject(saved))};vm.refresh()}
  }
 }
 @Test fun creationDraftAndImagesSurviveRecreationAtLargeFont() {
  val vm=start()
  val priorScale=device.executeShellCommand("settings get system font_scale").trim()
  val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   runBlocking {vm.preference("isExpiryNotificationEnabled",false)}
   click("Identity");click("Add");click("Manual Input")
   click("Full name")
   device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)),5000)!!.text="Draft survives rotation"
   assertEquals("Draft survives rotation",vm.pending.uiMemory.form.values["name"])
   instrumentation.runOnMainSync {
    val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
    activity.recreate()
   }
   assertTrue(device.wait(Until.hasObject(By.text("Draft survives rotation")),15000))
   device.executeShellCommand("settings put system font_scale 2.0")
   assertTrue(device.wait(Until.hasObject(By.text("Draft survives rotation")),15000))
   snapshot("parity-large-font-form")
   click("Edit Front image");click("Choose photo")
   assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),10000) || device.hasObject(By.pkg("com.android.documentsui")))
   device.pressBack()
   if(!device.wait(Until.hasObject(By.text("Manual Input")),3000)) device.pressBack()
   assertTrue(device.wait(Until.hasObject(By.text("Manual Input")),10000))
   assertEquals("Draft survives rotation",vm.pending.uiMemory.form.values["name"])
   click("Cancel")
   assertTrue(vm.pending.uiMemory.form.values.isEmpty());assertTrue(vm.pending.draftImages.isEmpty())
   assertTrue(vm.items.value.none {it.title=="Draft survives rotation"})
  } finally {
   device.executeShellCommand("settings put system font_scale "+priorScale.takeIf {it.toFloatOrNull()!=null}.orEmpty().ifBlank {"1.0"})
   runBlocking {vm.work {vm.store.saveSettings(it,JSONObject(saved))}}
  }
 }
 @Test fun realSafAutomaticBackupWritesAndRetainsVerifiedFiles() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val folder="Kura-Parity-"+java.util.UUID.randomUUID()
  device.executeShellCommand("mkdir -p /sdcard/Documents/"+folder)
  var destination:SafBackupDestination?=null
  try {
   runBlocking {vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)}
   click("Settings");click("Backup & Storage");click("Enable Auto-Backup");click("Backup Location")
   assertTrue(device.wait(Until.hasObject(By.text(folder)),15000))
   click(folder)
   val use=device.findObject(UiSelector().textMatches("(?i)use this folder"));assertTrue(use.waitForExists(5000));use.click()
   val allow=device.findObject(UiSelector().textMatches("(?i)allow"));assertTrue(allow.waitForExists(5000));allow.click()
   assertTrue(device.wait(Until.hasObject(By.text("Backup Location")),10000))
   click("Change Backup Password")
   device.findObject(UiSelector().className("android.widget.EditText")).setText("Kura parity password");click("Continue")
   assertTrue(device.wait(Until.gone(By.text("Automatic backup password")),10000))
   runBlocking {vm.preference("autoBackupRetentionCount",1)}
   click("Enable backups")
   val deadline=System.currentTimeMillis()+40000
   while(!vm.autoBackupStatus.value.startsWith("Saved ") && System.currentTimeMillis()<deadline) Thread.sleep(100)
   assertTrue(vm.autoBackupStatus.value,vm.autoBackupStatus.value.startsWith("Saved "))
   val tree=android.net.Uri.parse(vm.presentation.value.preferences.text("autoBackupUri"))
   destination=SafBackupDestination(context,tree)
   assertEquals(1,destination.list().size)
   runBlocking {vm.automaticBackupNow()}
   val docs=destination.list();assertEquals(1,docs.size);assertTrue(docs.single().name.endsWith("0002.wbk"))
   destination.read(docs.single().id).use {input->BackupCodec.read(input,"Kura parity password".toCharArray()).use {assertEquals("4.0",it.data.getString("version"))}}
   assertTrue(vm.state.value is VaultState.Unlocked)
   snapshot("parity-automatic-backup")
   runBlocking {vm.preference("autoBackupEnabled",false)}
  } finally {
   runBlocking {vm.preference("autoBackupEnabled",false);vm.work {vm.store.saveSettings(it,JSONObject(saved))}}
   destination?.list()?.forEach {destination.delete(it.id)}
   device.executeShellCommand("rmdir /sdcard/Documents/"+folder)
  }
 }
}
