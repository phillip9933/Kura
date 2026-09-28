package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.feature.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InteractionJourneyTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private val device=UiDevice.getInstance(instrumentation)
 private fun click(label:String) {
  if(label in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(label)) && !device.hasObject(By.desc(label))) {
   (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
  }
  repeat(16) { attempt->
   val node=device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
   try {if(node!=null && !node.visibleBounds.isEmpty) {node.click();device.waitForIdle();return}} catch(_:StaleObjectException) {}
   device.findObject(By.scrollable(true))?.scroll(if(attempt<8) Direction.DOWN else Direction.UP,.5f)
  }
  device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"ux-missing-control.xml"))
  error("Missing control: "+label)
 }
 private fun start():VaultController {
  check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
  device.wakeUp();context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
  listOf("2","4","6","8").forEach {device.findObject(By.text(it).pkg("com.android.systemui")).click()}
  device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
  assertTrue(device.wait(Until.hasObject(By.desc("Settings")),20000))
  var vm:VaultController?=null
  instrumentation.runOnMainSync {
   val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
   vm=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]
  }
  return vm!!
 }
 private fun snapshot(name:String) {
  if(android.os.Build.VERSION.SDK_INT<29) return
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
   device.waitForIdle();Thread.sleep(250)
   val bitmap=instrumentation.uiAutomation.takeScreenshot()
   java.io.File(context.getExternalFilesDir(null),name+".png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  } finally {instrumentation.runOnMainSync {windows.forEach {(view,flags)->
   val params=view.layoutParams as android.view.WindowManager.LayoutParams;params.flags=flags
   view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
  }}}
 }
 private fun until(check:()->Boolean) {
  val end=System.currentTimeMillis()+7000
  while(!check() && System.currentTimeMillis()<end) Thread.sleep(50)
  assertTrue(check())
 }

 private fun configure(vm:VaultController) = runBlocking {
  instrumentation.runOnMainSync {vm.lifecycle.setAutoLockMillis(300000)}
  vm.items.value.filter {it.title in setOf("Expiry badge fixture","Synthetic airline","Grid review 1","Grid review 2","Interaction Alpha","Interaction Beta")}.forEach {vm.deletePass(it)}
  vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
  vm.preference("isPassSearchEnabled",true);vm.preference("passSearchStyle","alwaysOn");vm.preference("searchBarPosition","top");vm.preference("showBottomNavigationBar",true)
  instrumentation.runOnMainSync {vm.pending.uiMemory.table.value="passes"}
  vm.preference("controlRowPosition","top")
 }
 private fun finish(vm:VaultController,saved:String,owned:List<Pair<String,Long>> = emptyList()) {
  runBlocking {owned.forEach {(table,id)->vm.items.value.firstOrNull {it.table==table && it.id==id}?.let {vm.deletePass(it)}};vm.work {vm.store.saveSettings(it,JSONObject(saved))};vm.lock()}
  device.pressHome()
 }

 @Test fun expiryPrecisionTemplatesAndImportedPreviews() {
  assertEquals(java.time.LocalDate.of(2032,2,29),expiryDateValue("02/32"))
  assertEquals(java.time.LocalDate.of(2032,2,12),expiryDateValue("2032-02-12"))
  assertNull(expiryDateValue("13/32"));assertNull(expiryDateValue("2031-02-29"))
  assertEquals("0232",paymentExpiry("02/32"));assertEquals("2032-02-12",paymentExpiry("2032-02-12"))
  assertTrue(validRecordField("expiry_date","02/32"));assertTrue(validRecordField("expiry","2032-02-12"))
  val monthlyPass=VaultItem("passes",2,"Monthly","","",false,"""{"expiry_date":"02/32","type":"generic"}""")
  assertEquals(java.time.Instant.parse("2032-02-29T23:59:59.999999999Z"),upcomingAt(monthlyPass,java.time.Instant.parse("2032-02-01T00:00:00Z"),java.time.ZoneOffset.UTC))
  assertFalse(defaultCategories.getValue("wallets").contains("Gym"));assertFalse(defaultCategories.getValue("passes").contains("Concert"))
  val custom=VaultPreferences("""{"paymentsCategories":["Gym","My club"]}""")
  assertEquals(listOf("Gym","My club"),custom.categories("wallets"))
  assertTrue(suggestedFields("passes","Coupon").any {it.name=="Minimum spend" && it.dataType=="currency"})
  assertTrue(suggestedFields("identities","Driver's License").any {it.name=="License class"})
  assertFalse(suggestedFields("passes","Transit").any {it.name=="Gate"})
  assertFalse("relevantDate" in basicFormFields("passes","passes","Coupon"))
  val item=VaultItem("passes",1,"Photo ID","","",false,"""{"type":"generic","sourceType":"pkpass","thumbnailImagePath":"portrait.png.enc"}""")
  assertEquals("portrait.png.enc",passPhotoPath(item));assertEquals("",previewImagePath(item,"front"));assertEquals("",previewImagePath(item,"back"));assertEquals("",previewImagePath(item,"virtualCards"))
 }
 @Test fun explicitSearchChoicesAndBubbleFilterMainGrid() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val owned=mutableListOf<Pair<String,Long>>()
  try {
   configure(vm)
   runBlocking {
    for(name in listOf("Interaction Alpha","Interaction Beta")) vm.saveRecord("passes",JSONObject().put("organizationName",name).put("sourceType","manual").toString())
    owned+=vm.items.value.filter {it.title.startsWith("Interaction ")}.map {it.table to it.id}
   }
   click("Settings");click("Navigation & Layout")
   click("Search Style: Search Button")
   until {vm.presentation.value.preferences.searchStyle=="icon"}
   assertFalse(device.hasObject(By.text("Search Position")))
   click("Search Style: Search Bar")
   until {vm.presentation.value.preferences.searchStyle!="icon"}
   assertTrue(device.wait(Until.hasObject(By.text("Search Position")),5000))
   click("Search Position: Bottom");until {vm.presentation.value.preferences.searchPosition=="bottom"}
   snapshot("interaction109-search-settings")
   click("Search Style: Search Button");click("Back");click("Done")
   assertFalse(device.hasObject(By.desc("Sort / Reorder")))
   click("Search");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Interaction Alpha"
   assertFalse(device.hasObject(By.descStartsWith("Search result ")))
   snapshot("interaction109-search-bubble")
   click("Show matching items")
   assertTrue(device.wait(Until.hasObject(By.desc("Open Interaction Alpha")),5000))
   assertFalse(device.hasObject(By.desc("Open Interaction Beta")))
   click("Clear search")
  } finally {finish(vm,saved,owned)}
 }
 @Test fun expiryModeAndStableManualDialog() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);click("Add");click("Manual Input");click("Cards")
   assertTrue(device.wait(Until.hasObject(By.text("Card number")),5000))
   val windows=mutableListOf<android.view.View>()
   instrumentation.runOnMainSync {windows+=android.view.inspector.WindowInspector.getGlobalWindowViews()}
   click("Identity");assertTrue(device.wait(Until.hasObject(By.text("Document number")),5000))
   instrumentation.runOnMainSync {assertEquals("Changing type must retain the same dialog windows",windows.toSet(),android.view.inspector.WindowInspector.getGlobalWindowViews().toSet())}
   click("Cards");click("Choose date for Expiry")
   assertTrue(device.wait(Until.hasObject(By.text("Year")),5000))
   click("YYYY-MM-DD");assertFalse(device.hasObject(By.text("Year")))
   snapshot("interaction109-expiry-precision")
   click("MM/YY");click("Set date")
   until {Regex("\\d{2}/\\d{2}").matches(vm.pending.uiMemory.form.values["expiry"].orEmpty())}
   instrumentation.runOnMainSync {vm.pending.uiMemory.form.values["expiry"]="2200-12-31"}
   click("Choose date for Expiry")
   assertTrue("Imported dates outside the default range must remain editable",device.wait(Until.hasObject(By.textContains("2200")),5000))
   click("Cancel");click("Cancel")
  } finally {finish(vm,saved)}
 }
 @Test fun photoPreviewsBackgroundMenuAndDragInMainGrid() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val owned=mutableListOf<Pair<String,Long>>()
  try {
   configure(vm)
   runBlocking {
    vm.preference("passesGridColumns",2);vm.preference("passesGridDisplayMode","front")
    vm.preference("showExpiryIndicators",true)
    val bitmap=android.graphics.Bitmap.createBitmap(320,200,android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.rgb(25,130,170))
    android.graphics.Canvas(bitmap).drawRect(30f,30f,130f,170f,android.graphics.Paint().apply {color=android.graphics.Color.WHITE})
    val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
    val path=try {vm.work {opened->app.kura.nativecore.EncryptedMediaStorage(java.io.File(opened.directory,"media")).write(java.util.UUID.randomUUID().toString()+".png.enc",bytes,opened.key).name}} finally {bytes.fill(0)}
    for(index in 1..2) vm.saveRecord("passes",JSONObject().put("organizationName","Grid review "+index).put("sourceType","pkpass").put("type","generic").put("thumbnailImagePath",path).put("expiry_date",java.time.LocalDate.now().plusDays(if(index==1) 5 else -5).toString()).toString())
    owned+=vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.table to it.id}
    vm.saveOrder("passes",vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.stableKey()})
   }
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Grid review"
   device.waitForIdle()
   assertNotNull(device.wait(Until.findObject(By.descContains("Grid review 1")),5000))
   snapshot("interaction109-photo-expiry")
   val card=device.findObject(By.descContains("Open Grid review 1")).visibleBounds
   val blankY=card.bottom+100
   device.swipe(device.displayWidth/2,blankY,device.displayWidth/2,blankY,120)
   assertTrue(device.wait(Until.hasObject(By.text("Sort / Reorder")),5000))
   click("Sort / Reorder")
   assertTrue(device.wait(Until.hasObject(By.text("Save order")),5000))
   val before=vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.title}
   val from=device.findObject(By.desc("Reorder "+before[0]));val target=device.findObject(By.desc("Reorder "+before[1]))
   from.drag(target.visibleCenter,250)
   assertEquals("Dragging only updates the draft",before,vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.title})
   snapshot("interaction109-grid-reorder")
   click("Save order")
   until {vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.title}==before.reversed()}
   runBlocking {vm.refresh()}
   assertEquals(before.reversed(),vm.items.value.filter {it.title.startsWith("Grid review ")}.map {it.title})
  } finally {finish(vm,saved,owned)}
 }

}
