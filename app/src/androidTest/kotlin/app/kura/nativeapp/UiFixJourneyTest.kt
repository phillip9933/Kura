package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.feature.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UiFixJourneyTest {
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
  var window:android.view.Window?=null
  instrumentation.runOnMainSync {
   window=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single().window
   window!!.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
  }
  try {
   device.waitForIdle();Thread.sleep(250)
   val bitmap=instrumentation.uiAutomation.takeScreenshot()
   java.io.File(context.getExternalFilesDir(null),name+".png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   bitmap.recycle()
  } finally {instrumentation.runOnMainSync {window!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}}
 }
 @Test fun currencyAndDateSchemasPreserveFormatsAndRejectInvalidValues() {
  val prefs=VaultPreferences(JSONObject().put("identityCustomFieldSchemas","[{\"name\":\"Balance\",\"dataType\":\"currency\"},{\"name\":\"Renewal\",\"dataType\":\"date\",\"dateFormat\":\"MM/yy\"},{\"name\":\"Birthday\",\"dataType\":\"date\",\"dateFormat\":\"dd/MM/yy\"}]").toString())
  val fields=prefs.customFields("identities")
  assertEquals("currency",fields[0].dataType);assertTrue(validCustomValue(fields[0],"12.50"));assertFalse(validCustomValue(fields[0],"abc"))
  assertTrue(validCustomValue(fields[1],"12/30"));assertFalse(validCustomValue(fields[1],"13/30"))
  assertTrue(validCustomValue(fields[2],"29/02/28"));assertFalse(validCustomValue(fields[2],"29/02/27"))
  assertFalse(validCustomValue(fields[2],"31/04/28"))
  assertEquals("29/02/28",customDisplayValue(fields[2],"2028-02-29","USD"))
  assertEquals("12/30",customDisplayValue(fields[1],"2030-12-31","USD"))
  assertTrue(customDisplayValue(fields[0],"12.5","USD").contains("12.50"))
 }
 @Test fun longPressSearchThreeColumnCycleAndUnconditionalDetailBarcodes() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val ids=mutableListOf<Pair<String,Long>>()
  try {
   runBlocking {
    vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
    vm.preference("passSearchStyle","icon");vm.preference("showPassQrButton",false)
    vm.preference("showUpcomingPasses",false)
    vm.saveRecord("wallets",JSONObject().put("name","UX payment").put("number","1234567890123456").put("category","Credit").toString())
    vm.saveRecord("passes",JSONObject().put("organizationName","UX pass").put("type","generic").put("barcodeValue","UX-TICKET").put("barcodeFormat","QR Code").toString())
    val bitmap=android.graphics.Bitmap.createBitmap(8,5,android.graphics.Bitmap.Config.ARGB_8888)
    val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
    val bytes=output.toByteArray()
    try {vm.imageChange(ImageTarget("identities",-1,"frontImagePath"),bytes)} finally {bytes.fill(0)}
    vm.saveRecord("identities",JSONObject().put("name","UX identity").put("value","ID-123").put("cardType","Passport").toString(),vm.pending.takeDraft())
    vm.items.value.filter {it.title.startsWith("UX ")}.forEach {ids+=it.table to it.id}
   }
   for((table,label,title) in listOf(Triple("wallets","Cards","UX payment"),Triple("passes","Passes","UX pass"),Triple("identities","Identity","UX identity"))) {
    runBlocking {vm.preference(sectionKey(table)+"GridColumns",1)}
    click(label)
    assertFalse("Search must be a button",device.hasObject(By.clazz("android.widget.EditText")))
    click("Use two columns");assertTrue(device.wait(Until.hasObject(By.desc("Use three columns")),5000))
    click("Use three columns");assertTrue(device.wait(Until.hasObject(By.desc("Use one column")),5000))
    assertEquals(3,vm.presentation.value.preferences.columns(table))
    click("Use one column")
    click("Search")
    val input=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!
    input.text=title
    click("Show matching items")
    click("Open "+title)
    click("Close")
    // Long hold the actual preview (identity exercises encrypted image display).
    val scroll=UiScrollable(UiSelector().scrollable(true))
    if(!device.hasObject(By.desc("Open "+title))) scroll.scrollIntoView(UiSelector().description("Open "+title))
    val card=device.wait(Until.findObject(By.desc("Open "+title)),5000)!!
    card.longClick()
    assertTrue(device.wait(Until.hasObject(By.text("Edit")),5000))
    assertTrue(device.hasObject(By.text("Archive")));assertTrue(device.hasObject(By.text("Delete")))
    click("Edit");assertTrue(device.wait(Until.hasObject(By.text("Edit item")),5000));click("Cancel")
    click("Open "+title)
    click(if(table=="passes") "Pass barcode" else "Item barcode")
    assertTrue(device.wait(Until.hasObject(By.text("Rotate")),5000))
    click("Close barcode");click("Close")
   }
  } finally {
   runBlocking {ids.forEach {(table,id)->vm.items.value.firstOrNull {it.table==table && it.id==id}?.let {vm.deletePass(it)}};vm.work {vm.store.saveSettings(it,JSONObject(saved))}}
  }
 }
 @Test fun settingsPagesAreSeparatedAndBackupSetupCanBeCancelled() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   runBlocking {
    vm.preference("autoBackupEnabled",false);vm.preference("autoBackupUri","");vm.preference("autoBackupPath","")
    vm.preference("isExpiryNotificationEnabled",false)
    listOf("wallets","passes","identities").forEach {vm.preference(sectionKey(it)+"CustomFieldSchemas","[]")}
   }
   click("Settings");click("Backup & Storage")
   assertFalse(device.hasObject(By.text("Backup Location")));assertFalse(device.hasObject(By.text("Backup Retention")))
   assertTrue(device.hasObject(By.text("Create Backup")));assertTrue(device.hasObject(By.text("Restore Backup")));assertTrue(device.hasObject(By.text("Delete All Data")))
   snapshot("ux-backup-disabled")
   click("Enable Auto-Backup");assertTrue(device.wait(Until.hasObject(By.text("Set up automatic backups")),5000))
   click("Enable backups")
   assertFalse(vm.presentation.value.preferences.bool("autoBackupEnabled",false))
   assertTrue(device.hasObject(By.text("Set up automatic backups")))
   click("Cancel");assertFalse(vm.presentation.value.preferences.bool("autoBackupEnabled",false))
   click("Back")
   for((table,label) in listOf("wallets" to "Cards Settings","passes" to "Passes Settings","identities" to "Identity Settings")) {
    click(label)
    assertTrue(device.hasObject(By.text("Manage Categories")));assertTrue(device.hasObject(By.text("Manage Custom Fields")))
    assertFalse(device.hasObject(By.text("Edit Categories")));assertFalse(device.hasObject(By.text("Add Custom Field")))
    click("Manage Categories");assertTrue(device.wait(Until.hasObject(By.text(vm.presentation.value.preferences.categories(table).first())),5000))
    click("Back");click("Manage Custom Fields");click("Add Custom Field")
    device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Balance"
    click("Currency");click("Add")
    assertTrue(device.wait(Until.hasObject(By.text("Balance")),5000))
    assertEquals("currency",vm.presentation.value.preferences.customFields(table).single().dataType)
    click("Add Custom Field");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Renewal"
    click("Date");click("MM/YY");click("Add")
    assertTrue(device.wait(Until.hasObject(By.text("Renewal")),5000))
    assertEquals("MM/yy",vm.presentation.value.preferences.customFields(table).last().dateFormat)
    if(table=="identities") {
     click("Add Custom Field");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Full date"
     click("Date");click("DD/MM/YY");click("Add")
    }
    click("Back");click("Back")
   }
   click("Done");click("Identity");click("Add");click("Manual Input")
   click("Choose date for Renewal")
   assertTrue(device.wait(Until.hasObject(By.text("Choose month and year")),5000))
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="2030"
   click("Dec");click("Set date")
   assertEquals("2030-12-01",vm.pending.uiMemory.form.customValues["Renewal"])
   assertEquals("12/30",customDisplayValue(CustomField("Renewal","date","MM/yy"),vm.pending.uiMemory.form.customValues["Renewal"].orEmpty(),"USD"))
   instrumentation.runOnMainSync {vm.pending.uiMemory.form.customValues["Full date"]="1985-01-02"}
   click("Choose date for Full date");click("Set date")
   assertEquals("1985-01-02",vm.pending.uiMemory.form.customValues["Full date"])
   assertTrue(customDisplayValue(CustomField("Full date","date","dd/MM/yy"),vm.pending.uiMemory.form.customValues["Full date"].orEmpty(),"USD").matches(Regex("\\d{2}/\\d{2}/\\d{2}")))
   click("Cancel")
  } finally {runBlocking {vm.work {vm.store.saveSettings(it,JSONObject(saved))}}}
 }
}
