package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.feature.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FormReviewJourneyTest {
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
 private fun until(check:()->Boolean) {
  val end=System.currentTimeMillis()+7000
  while(!check() && System.currentTimeMillis()<end) Thread.sleep(50)
  assertTrue(check())
 }
 @Test fun formTypesAndDefaultsMatchEachSectionWithoutGuessingData() {
  val prefs=VaultPreferences(JSONObject().put("paymentsCategories","[\"Debit\",\"Credit\"]").toString())
  assertEquals("Credit",defaultFormCategory("wallets",prefs))
  assertEquals("Passport",defaultFormCategory("identities",prefs))
  assertEquals("Other",defaultFormCategory("passes",prefs))
  assertEquals("storeCard",initialPassLayout("wallets","Loyalty"))
  assertEquals("eventTicket",initialPassLayout("passes","Concert"))
  assertFalse("type" in basicFormFields("passes","passes"))
  assertFalse("relevantDate" in basicFormFields("passes","wallets"))
  assertTrue(suggestedFields("wallets","Gift Card").contains(CustomField("Balance","currency")))
  assertTrue(suggestedFields("identities","Passport").any {it.name=="Date of birth" && it.dataType=="date"})
  assertTrue(suggestedFields("passes","Boarding Pass").any {it.name=="Seat"})
  val styled=JSONObject("""{"primaryFields":[{"key":"balance","value":"12.50","currencyCode":"USD"}]}""")
  assertTrue(validStructuredFields(styled,JSONObject()))
  styled.getJSONArray("primaryFields").getJSONObject(0).put("value","not money")
  assertFalse(validStructuredFields(styled,JSONObject()))
  assertTrue(validStructuredFields(styled,JSONObject(styled.toString())))
  val numericOriginal=JSONObject("""{"primaryFields":[{"key":"points","value":12}]}""")
  val numericDraft=JSONObject(numericOriginal.toString())
  val numericField=numericDraft.getJSONArray("primaryFields").getJSONObject(0)
  numericField.put("value","not a number")
  assertTrue(structuredFieldIsNumeric(numericField,numericOriginal.getJSONArray("primaryFields").getJSONObject(0)))
  assertFalse(validStructuredFields(numericDraft,numericOriginal))
  numericField.put("value","12.50")
  assertTrue(validStructuredFields(numericDraft,numericOriginal))
  val numericSaved=structuredFieldsForSave(JSONObject(numericDraft.toString()),numericOriginal)
  assertTrue(numericSaved.getJSONArray("primaryFields").getJSONObject(0).get("value") is Number)
  assertEquals("12.50",numericField.get("value"))
  assertEquals("2030-12-01",paymentExpiry("2030-12-01"));assertEquals("1230",paymentExpiry("12/30"))
  assertEquals("12/30",expiryInput("1230"))
  assertTrue(validRecordField("maxlimit","12.50"));assertFalse(validRecordField("maxlimit","lots"))
  assertTrue(validRecordField("billdate","31"));assertFalse(validRecordField("billdate","32"))
  assertFalse(validRecordField("expiry_date","2028-02-31"))
  assertEquals("2030-05-02T11:30+09:00",eventWithDate("2030-05-01T11:30+09:00",java.time.LocalDate.of(2030,5,2)))
  assertEquals("2030-05-01T16:45+09:00",eventWithTime("2030-05-01T11:30+09:00",java.time.LocalTime.of(16,45)))
 }
 @Test fun categoryControlsRemovedSettingsAndTypedFormsPreserveHiddenValues() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  var id:Long?=null
  try {
   runBlocking {
    vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
    vm.preference("passSearchStyle","alwaysOn");vm.preference("isPassSearchEnabled",true)
    vm.preference("paymentsCategories","[\"Zulu\",\"Alpha\",\"Beta\",\"Credit\"]")
    vm.preference("identityCategories","[\"Passport\"]");vm.preference("passesCategories","[\"Other\",\"Boarding Pass\"]")
    vm.saveRecord("wallets",JSONObject().put("name","Review card").put("number","1234567890123456").put("expiry","1230")
     .put("cardtype","Legacy tier").put("category","Credit").put("spends","12.50")
     .put("customFields",JSONObject().put("Metadata",JSONObject().put("keep",true)).toString()).toString())
    id=vm.items.value.single {it.title=="Review card"}.id
   }
   click("Settings")
   for(label in listOf("Cards Settings","Passes Settings","Identity Settings")) {
    click(label);assertFalse(device.hasObject(By.text("Grid Columns")));click("Back")
   }
   click("Barcode & Scanning");assertFalse(device.hasObject(By.text("Show Barcode Shortcut")));click("Back")
   click("Cards Settings");click("Manage Categories");click("Sort A–Z")
   until {vm.presentation.value.preferences.categories("wallets")==listOf("Alpha","Beta","Credit","Zulu")}
   click("Move Beta up")
   until {vm.presentation.value.preferences.categories("wallets").first()=="Beta"}
   assertEquals("Beta",runBlocking {vm.work {app.kura.feature.VaultPreferences(vm.store.settings(it).toString()).categories("wallets").first()}})
   click("Done");click("Cards")
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Review card"
   click("Open Review card");click("Edit")
   assertFalse(device.hasObject(By.text("Spending")));assertFalse(device.hasObject(By.text("Card type")))
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Reviewed card"
   click("Save")
   until {vm.items.value.any {it.title=="Reviewed card"}}
   val row=JSONObject(vm.items.value.single {it.id==id && it.table=="wallets"}.json)
   assertEquals("1230",row.getString("expiry"));assertEquals("12.50",row.getString("spends"))
   assertEquals("Legacy tier",row.getString("cardtype"));assertTrue(JSONObject(row.getString("customFields")).getJSONObject("Metadata").getBoolean("keep"))
   click("Identity");click("Add");click("Manual Input")
   click("Choose date for Date of birth");assertTrue(device.wait(Until.hasObject(By.text("Set date")),5000))
   click("Cancel");click("Cancel")
   click("Passes");click("Add");click("Manual Input")
   assertFalse(device.hasObject(By.text("Pass layout")));assertFalse(device.hasObject(By.text("Foreground color")))
   click("Choose Category");click("Boarding Pass")
   until {vm.pending.uiMemory.form.values["type"]=="boardingPass"}
   assertEquals("PKTransitTypeAir",vm.pending.uiMemory.form.values["transitType"])
   instrumentation.runOnMainSync {
    vm.pending.uiMemory.form.values["transitType"]="PKTransitTypeBus"
    val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
    activity.recreate()
   }
   assertTrue(device.wait(Until.hasObject(By.text("Manual Input")),10000))
   assertEquals("PKTransitTypeBus",vm.pending.uiMemory.form.values["transitType"])
   click("Choose date for Event / departure date");click("Set date")
   click("Add time (optional)");assertTrue(device.wait(Until.hasObject(By.text("Set time")),5000));click("Set time")
   assertTrue(vm.pending.uiMemory.form.values["relevantDate"].orEmpty().contains("T"))
   click("Cancel")
  } finally {runBlocking {
   vm.items.value.firstOrNull {it.table=="wallets" && it.id==id}?.let {vm.deletePass(it)}
   vm.work {vm.store.saveSettings(it,JSONObject(saved))}
  }}
 }
 @Test fun clearArchiveConfirmsAllSectionsAndKeepsActiveAndNewlyArchivedItems() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  val existing=vm.items.value.filter {it.archived};val owned=mutableListOf<Pair<String,Long>>()
  try {
   runBlocking {
    vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
    existing.forEach {vm.archive(it)}
    for(table in listOf("wallets","passes","identities")) {
     vm.saveRecord(table,JSONObject().put(if(table=="passes") "organizationName" else "name","Archive review "+table).toString())
     val item=vm.items.value.single {it.title=="Archive review "+table};owned+=table to item.id
     if(table!="wallets") vm.archive(item)
    }
   }
   click("Settings");click("Archive")
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Archive review passes"
   click("Delete all archived items");assertTrue(device.wait(Until.hasObject(By.text("Delete all archived items?")),5000))
   click("Cancel");assertEquals(2,vm.items.value.count {it.archived})
   click("Delete all archived items")
   runBlocking {
    vm.saveRecord("passes",JSONObject().put("organizationName","Later archive review").toString())
    val later=vm.items.value.single {it.title=="Later archive review"};owned+=later.table to later.id;vm.archive(later)
   }
   click("Delete archived items")
   until {vm.items.value.none {it.title in setOf("Archive review passes","Archive review identities")}}
   assertTrue(vm.items.value.any {it.title=="Archive review wallets" && !it.archived})
   assertTrue(vm.items.value.any {it.title=="Later archive review" && it.archived})
  } finally {runBlocking {
   owned.forEach {(table,id)->vm.items.value.firstOrNull {it.table==table && it.id==id}?.let {vm.deletePass(it)}}
   existing.forEach {old->vm.items.value.firstOrNull {it.table==old.table && it.id==old.id && !it.archived}?.let {vm.archive(it)}}
   vm.work {vm.store.saveSettings(it,JSONObject(saved))}
  }}
 }
}
