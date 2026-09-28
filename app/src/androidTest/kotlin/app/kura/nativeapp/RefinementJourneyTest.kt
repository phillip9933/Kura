package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.feature.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RefinementJourneyTest {
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

 @Test fun templateColorAndCropRules() {
  assertEquals("wallets",manualStorageTable("wallets","Credit"))
  assertEquals("passes",manualStorageTable("wallets","Gift Card"))
  assertEquals("identities",manualStorageTable("identities","Passport"))
  assertEquals(1f,dragCropPosition(.5f,400f,400f,.5f),0f)
  assertEquals(0f,dragCropPosition(.5f,-400f,400f,.5f),0f)
  assertEquals(.5f,dragCropPosition(.5f,400f,400f,1f),0f)
  assertEquals("#FFFF0000",colorCode(wheelColor(100f,0f,100f,1f)))
  assertEquals("#FFFFFFFF",colorCode(wheelColor(0f,0f,100f,1f)))
  assertNull(parsePassColor("#GG0000"))
  val row=JSONObject().put("sourceType","pkpass")
  assertTrue(isImportedPass(VaultItem("passes",1,"Example","","",false,row.toString())))
  row.put("sourceType","manual")
  assertFalse(isImportedPass(VaultItem("passes",1,"Example","","",false,row.toString())))
  val mark=context.getDrawable(R.drawable.kura_mark)!!
  val bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888)
  try {mark.setBounds(0,0,256,256);mark.draw(android.graphics.Canvas(bitmap));assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(0,0)));assertTrue(android.graphics.Color.alpha(bitmap.getPixel(44,160))>0)} finally {bitmap.recycle()}
 }
 private fun configure(vm:VaultController) = runBlocking {
  instrumentation.runOnMainSync {vm.lifecycle.setAutoLockMillis(300000)}
  vm.items.value.filter {it.title in setOf("Expiry badge fixture","Synthetic airline")}.forEach {vm.deletePass(it)}
  vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
  vm.preference("isPassSearchEnabled",true);vm.preference("passSearchStyle","alwaysOn");vm.preference("searchBarPosition","top");vm.preference("showBottomNavigationBar",true)
  instrumentation.runOnMainSync {vm.pending.uiMemory.table.value="passes"}
  vm.preference("controlRowPosition","top")
 }
 private fun finish(vm:VaultController,saved:String,owned:List<Pair<String,Long>> = emptyList()) {
  runBlocking {owned.forEach {(table,id)->vm.items.value.firstOrNull {it.table==table && it.id==id}?.let {vm.deletePass(it)}};vm.work {vm.store.saveSettings(it,JSONObject(saved))};vm.lock()}
  device.pressHome()
 }
 @Test fun selectedSettingsScrollAndBottomControlsStayReachable() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  var owned:Pair<String,Long>?=null
  try {
   configure(vm)
   runBlocking {
    vm.theme(1);vm.preference("controlRowPosition","bottom")
    vm.saveRecord("passes",JSONObject().put("organizationName","Expiry badge fixture").put("sourceType","manual").put("expiry_date",java.time.LocalDate.now().plusDays(1).toString()).toString())
    owned=vm.items.value.single {it.title=="Expiry badge fixture"}.let {it.table to it.id}
    vm.preference("showExpiryIndicators",true)
   }
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Expiry badge fixture"
   assertTrue(device.wait(Until.hasObject(By.desc("Expiry warning for Expiry badge fixture")),10000))
   val settings=device.findObject(By.desc("Settings")).visibleBounds
   val fab=device.findObject(By.desc("Add")).visibleBounds
   assertFalse("FAB must not cover settings",android.graphics.Rect.intersects(settings,fab))
   runBlocking {vm.preference("isPassSearchEnabled",true);vm.preference("searchBarPosition","bottom");vm.preference("passSearchStyle","alwaysOn")}
   device.waitForIdle()
   assertFalse(android.graphics.Rect.intersects(device.findObject(By.desc("Settings")).visibleBounds,device.findObject(By.desc("Add")).visibleBounds))
   click("Settings");click("General Display");click("App Theme")
   device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"refinement-theme.xml"))
   until {runCatching {device.findObject(By.text("Dark"))?.let {it.isSelected || it.isChecked || it.parent?.isSelected==true || it.parent?.isChecked==true}==true}.getOrDefault(false)}
   snapshot("refinement-selected-theme")
   click("Cancel");click("Back")
   assertNotNull("Parent scroll position is retained",device.wait(Until.findObject(By.text("General Display")),5000))
   click("Expiry Alerts");assertNotNull(device.wait(Until.findObject(By.text("Show Expiry Indicators")),5000))
   assertTrue(vm.presentation.value.preferences.bool("showExpiryIndicators",false));assertFalse(vm.presentation.value.preferences.bool("isExpiryNotificationEnabled"))
   click("Back");click("Navigation & Layout")
   click("Control Row Position: Top")
   until {vm.presentation.value.preferences.controlPosition=="top"}
   assertFalse(device.hasObject(By.text("Cancel")))
   click("Back");click("App Version & Trademark")
   click("Close")
   assertNotNull(device.wait(Until.findObject(By.textContains("(${BuildConfig.VERSION_CODE})")),5000))
   click("Done")
   instrumentation.runOnMainSync {
    val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
    assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
    val task=activity.getSystemService(android.app.ActivityManager::class.java).appTasks.first().taskInfo?.taskDescription
    assertEquals(android.graphics.Color.BLACK,task!!.backgroundColor)
   }
   device.pressRecentApps();Thread.sleep(800)
   val screenshot=instrumentation.uiAutomation.takeScreenshot()
   java.io.File(context.getExternalFilesDir(null),"refinement-secure-recents.png").outputStream().use {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
  } finally {finish(vm,saved,listOfNotNull(owned))}
 }
 @Test fun importedDetailsHideTechnicalSectionsAndImageEditing() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  var owned:Pair<String,Long>?=null
  try {
   configure(vm)
   runBlocking {
    val footerBitmap=android.graphics.Bitmap.createBitmap(200,30,android.graphics.Bitmap.Config.ARGB_8888)
    footerBitmap.eraseColor(android.graphics.Color.GREEN)
    val footerBytes=java.io.ByteArrayOutputStream().also {footerBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();footerBitmap.recycle()
    val footerPath=try {vm.work {opened->app.kura.nativecore.EncryptedMediaStorage(java.io.File(opened.directory,"media")).write(java.util.UUID.randomUUID().toString()+".png.enc",footerBytes,opened.key).name}} finally {footerBytes.fill(0)}
    val fields=JSONObject("""{"headerFields":[{"label":"FLIGHT","value":"CX123"}],"primaryFields":[{"label":"FROM","value":"HKG"},{"label":"TO","value":"NRT"}],"secondaryFields":[{"label":"NAME","value":"Test Passenger"}],"auxiliaryFields":[{"label":"SEAT","value":"12A"}],"backFields":[{"label":"TERMS","value":"Synthetic conditions"}]}""")
    vm.saveRecord("passes",JSONObject().put("organizationName","Synthetic airline").put("sourceType","pkpass").put("type","boardingPass").put("footerImagePath",footerPath).put("fields",fields.toString()).put("barcodeValue","TEST123").put("barcodeFormat","PKBarcodeFormatQR").toString())
    owned=vm.items.value.single {it.title=="Synthetic airline"}.let {it.table to it.id}
   }
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Synthetic airline"
   click("Open Synthetic airline")
   for(label in listOf("Header","Primary","Secondary","Auxiliary")) assertFalse(device.hasObject(By.text(label)))
   assertTrue(device.hasObject(By.text("CX123")));assertTrue(device.hasObject(By.text("HKG")))
   assertTrue(device.wait(Until.hasObject(By.desc("Pass footer")),5000))
   assertTrue(device.findObject(By.desc("Pass footer")).visibleBounds.bottom<=device.findObject(By.desc("Pass barcode")).visibleBounds.top)
   snapshot("refinement-imported-boarding")
   click("Terms and additional information")
   assertTrue("Expanded terms have a collapse indicator",device.wait(Until.hasObject(By.descContains("Collapse information")),5000))
   click("Synthetic conditions")
   assertFalse(device.hasObject(By.text("Card images")));assertFalse(device.hasObject(By.desc("Choose Front image")))
   click("Close")
  } finally {finish(vm,saved,listOfNotNull(owned))}
 }
 @Test fun dynamicManualEntryColorCalendarAndDraftImage() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);click("Add");click("Manual Input")
   click("Cards");assertTrue(device.wait(Until.hasObject(By.text("Card number")),5000))
   instrumentation.runOnMainSync {vm.pending.uiMemory.form.values["name"]="Retained payment draft"}
   click("Choose Category");click("Gift Card")
   click("Balance");assertNotNull(device.findObject(By.text("Balance")))
   // Wait for asynchronous keyboard presentation before dismissing it; Back must not close the form.
   if(device.wait(Until.hasObject(By.pkg("com.google.android.inputmethod.latin")),3000)) {
    device.pressBack();assertTrue(device.wait(Until.gone(By.pkg("com.google.android.inputmethod.latin")),5000))
   }
   device.findObject(By.scrollable(true))?.scroll(Direction.UP,1f);device.waitForIdle()
   click("Identity")
   if(!device.wait(Until.hasObject(By.text("Document number")),5000)) {
    println("MANUAL_SECTION="+vm.pending.uiMemory.manualSection.value+" FORM="+vm.pending.uiMemory.form.identity)
    snapshot("organization110-manual-failure");device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"organization110-manual-failure.xml"))
   }
   assertTrue(device.hasObject(By.text("Document number")))
   click("Choose date for Date of birth");assertTrue(device.wait(Until.hasObject(By.text("Set date")),5000));click("Cancel")
   click("Cards")
   until {vm.pending.uiMemory.form.values["name"]=="Retained payment draft"}
   click("Passes");click("Choose Category");click("Boarding Pass")
   assertFalse(device.hasObject(By.text("Additional details and appearance")))
   assertFalse(device.hasObject(By.textContains("Header fields")));assertFalse(device.hasObject(By.textContains("Primary fields")))
   click("Item colors");click("Background color")
   assertTrue(device.hasObject(By.desc("Color wheel")))
   snapshot("refinement-color-wheel")
   click("Color code");device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)),5000)!!.text="#123456"
   device.pressBack();device.waitForIdle()
   click("Apply color");click("Done")
   if(vm.pending.uiMemory.form.values["backgroundColor"]!="#123456") {
    println("COLOR_DRAFT_TABLE="+vm.pending.uiMemory.form.identity+" COLORS="+vm.pending.uiMemory.form.values.filterKeys {it.contains("Color")})
    device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"refinement-colors.xml"))
   }
   until {vm.pending.uiMemory.form.values["backgroundColor"]=="#123456"}
   val bitmap=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.MAGENTA)
   val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
   try {runBlocking {vm.imageChange(ImageTarget("passes",-1,"frontImagePath"),bytes)}} finally {bytes.fill(0)}
   assertEquals("Image staging must retain the form",context.packageName,device.currentPackageName)
   val replacement=UiSelector().description("Edit Front image")
   // Pan along the form margin so editable fields do not consume the scroll gesture.
   repeat(16) {
    if(!device.hasObject(By.desc("Edit Front image"))) {
     device.swipe(24,device.displayHeight*4/5,24,device.displayHeight/4,25);device.waitForIdle()
    }
   }
   assertTrue(device.hasObject(By.desc("Edit Front image")))
   device.findObject(replacement).click();device.waitForIdle();click("Choose photo")
   // GET_CONTENT may use the system picker, chooser, or a user-selected provider.
   until {device.currentPackageName!=context.packageName}
   repeat(3) {if(device.currentPackageName!=context.packageName) {device.pressBack();device.waitForIdle()}}
   assertEquals(context.packageName,device.currentPackageName)
   repeat(3) {if(!device.hasObject(By.desc("View Front image"))) {device.swipe(24,device.displayHeight/3,24,device.displayHeight*3/4,25);device.waitForIdle()}}
   assertTrue(device.wait(Until.hasObject(By.desc("View Front image")),10000))
   snapshot("refinement-draft-image")
   click("Cancel")
   assertTrue(vm.pending.draftImages.isEmpty());assertTrue(vm.pending.uiMemory.manualForms.isEmpty())
  } finally {finish(vm,saved)}
 }
}
