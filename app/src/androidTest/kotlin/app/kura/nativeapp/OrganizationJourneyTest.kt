package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.kura.feature.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrganizationJourneyTest {
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
  vm.items.value.filter {it.title.startsWith("Organize ")}.forEach {vm.deletePass(it)}
  vm.preference("autoBackupEnabled",false);vm.preference("isExpiryNotificationEnabled",false)
  vm.preference("isPassSearchEnabled",true);vm.preference("passSearchStyle","alwaysOn");vm.preference("searchBarPosition","top");vm.preference("showBottomNavigationBar",true)
  instrumentation.runOnMainSync {vm.pending.uiMemory.table.value="passes"}
  vm.preference("controlRowPosition","top");vm.preference("favoritesFirst",false);vm.preference("passesFavoritesOnly",false);vm.preference("passesSortMode","custom");vm.preference("showUpcomingPasses",false)
  vm.preference("passesGridColumns",2);vm.preference("nativeFavorites",JSONObject())
 }
 private fun finish(vm:VaultController,saved:String,owned:List<Pair<String,Long>> = emptyList()) {
  runBlocking {owned.forEach {(table,id)->vm.items.value.firstOrNull {it.table==table && it.id==id}?.let {vm.deletePass(it)}};vm.work {vm.store.saveSettings(it,JSONObject(saved))};vm.lock()}
  device.pressHome()
 }


 private fun owned(vm:VaultController)=vm.items.value.filter {it.title.startsWith("Organize ")}.map {it.table to it.id}
 private fun fixture(vm:VaultController,name:String,extra:JSONObject=JSONObject())=runBlocking {
  extra.put("organizationName",name).put("sourceType",extra.optString("sourceType","manual"))
  vm.saveRecord("passes",extra.toString());vm.items.value.single {it.title==name}
 }
 private fun photo(vm:VaultController,color:Int):String=runBlocking {
  val bitmap=android.graphics.Bitmap.createBitmap(140,180,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(color)
  val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
  try {vm.work {opened->app.kura.nativecore.EncryptedMediaStorage(java.io.File(opened.directory,"media")).write(java.util.UUID.randomUUID().toString()+".png.enc",bytes,opened.key).name}} finally {bytes.fill(0)}
 }
 @Test fun sortingAndDraftMergesRespectSectionsAndFavorites() {
  fun item(table:String,id:Long,name:String)=VaultItem(table,id,name,"","",false,if(table=="passes") "{\"type\":\"storeCard\"}" else "{}")
  val a=item("wallets",1,"Alpha");val b=item("passes",1,"Zulu");val c=item("identities",1,"ID")
  val prefs=VaultPreferences(JSONObject().put("paymentsSortMode","nameAsc").put("favoritesFirst",true).put("nativeFavorites",JSONObject().put(b.stableKey(),true)).toString())
  assertEquals(listOf(b,a),sortedVaultItems(listOf(a,b),prefs,"wallets"));assertEquals(listOf(a,b),sortedVaultItems(listOf(b,a),prefs,"wallets",false))
  assertEquals(listOf(b.stableKey(),c.stableKey(),a.stableKey()),mergeSectionOrder(listOf(a,c,b),"wallets",listOf(b.stableKey(),a.stableKey())))
  assertTrue(runCatching {mergeSectionOrder(listOf(a,c,b),"wallets",listOf(c.stableKey()))}.isFailure)
  val dated=VaultPreferences(JSONObject().put("paymentsSortMode","addedAsc").put("nativeAddedAt",JSONObject().put(a.stableKey(),0).put(b.stableKey(),123)).toString())
  assertEquals(listOf(b,a),sortedVaultItems(listOf(a,b),dated,"wallets"))
  assertEquals("nameDesc",toggledSortMode("nameAsc","name"));assertEquals("nameAsc",toggledSortMode("nameDesc","name"))
  assertEquals("addedAsc",toggledSortMode("addedDesc","added"));assertEquals("addedDesc",toggledSortMode("addedAsc","added"))
  assertEquals(listOf(b,a),sortedVaultItems(listOf(a,b),dated,"wallets",false,"addedDesc"))
  assertEquals(listOf("b","c","a"),movedKeys(listOf("a","b","c"),"a","c"))
 }
 @Test fun favoritesFilterSortAndEncryptedPersistence() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);val a=fixture(vm,"Organize Alpha");val z=fixture(vm,"Organize Zulu")
   runBlocking {vm.saveOrder("passes",listOf(a.stableKey(),z.stableKey()))}
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize"
   device.wait(Until.findObject(By.desc("Open Organize Zulu")),5000) .also { if(it==null) device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"organization-missing.xml")) }!!.longClick();click("Add favorite")
   until {vm.presentation.value.preferences.isFavorite(z)}
   runBlocking {vm.refresh()};assertTrue(vm.presentation.value.preferences.isFavorite(z));assertTrue(vm.presentation.value.preferences.addedAt(z)>0)
   runBlocking {vm.preference("passesSortMode","nameDesc")}
   until {vm.presentation.value.preferences.sortMode("passes")=="nameDesc"}
   click("Categories");click("Favorites only");device.pressBack()
   assertTrue(device.wait(Until.hasObject(By.desc("Open Organize Zulu")),5000));assertFalse(device.hasObject(By.desc("Open Organize Alpha")))
   click("Settings");click("Navigation & Layout");click("Keep Favorites First")
   until {vm.presentation.value.preferences.bool("favoritesFirst",false)}
   snapshot("organization110-favorite-setting")
   click("Back");click("Done")
   runBlocking {vm.preference("passesFavoritesOnly",false);vm.preference("passesSortMode","nameAsc")}
   until {sortedVaultItems(vm.items.value.filter {it.section.key=="passes" && !it.archived},vm.presentation.value.preferences,"passes").first().stableKey()==z.stableKey()}
   val settings=runBlocking {vm.work {vm.store.settings(it)}}
   assertTrue(settings.getJSONObject("nativeFavorites").getBoolean(z.stableKey()))
   click("Categories");snapshot("organization110-sort-menu");device.pressBack()
  } finally {finish(vm,saved,owned(vm))}
 }
 @Test fun reversibleNameAndDateSortingInMainAndDraft() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);val a=fixture(vm,"Organize Alpha");val z=fixture(vm,"Organize Zulu")
   runBlocking {
    vm.preference("nativeAddedAt",JSONObject().put(a.stableKey(),100).put(z.stableKey(),200))
    vm.saveOrder("passes",listOf(a.stableKey(),z.stableKey()))
   }
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize"
   fun left(label:String)=device.findObject(By.desc(label)).visibleBounds.left
   fun mainOrder(first:String,second:String) {until {device.hasObject(By.desc("Open "+first)) && left("Open "+first)<left("Open "+second)}}
   assertFalse(device.hasObject(By.desc("Sort by name")))
   runBlocking {vm.preference("passesSortMode","addedAsc")}
   fun begin() {device.wait(Until.findObject(By.desc("Open Organize Alpha")),5000)!!.longClick();click("Sort / Reorder");until {device.hasObject(By.text("Save order"))}}
   fun savedOrder()=vm.items.value.filter {it.title.startsWith("Organize ")}.map {it.stableKey()}
   val original=savedOrder()
   begin();until {device.hasObject(By.text("Save order"))}
   click("Sort by name");until {device.hasObject(By.text("Name A–Z"))}
   click("Sort by name");until {device.hasObject(By.text("Name Z–A"))}
   assertTrue(device.hasObject(By.text("Name Z–A")));assertEquals(original,savedOrder())
   click("Save order");until {device.hasObject(By.desc("Settings"))}
   assertEquals(listOf(z.stableKey(),a.stableKey()),savedOrder())
   begin();click("Sort by name");click("Save order")
   until {device.hasObject(By.desc("Settings"))};assertEquals(listOf(a.stableKey(),z.stableKey()),savedOrder())
   begin();click("Sort by date added");until {device.hasObject(By.text("Date added ↓"))};click("Sort by date added");until {device.hasObject(By.text("Date added ↑"))}
   assertTrue(device.hasObject(By.text("Date added ↑")))
   snapshot("sorting111-draft-controls")
   click("Save order");until {device.hasObject(By.desc("Settings"))};assertEquals(listOf(a.stableKey(),z.stableKey()),savedOrder())
   begin();click("Sort by date added");click("Cancel")
   assertEquals(listOf(a.stableKey(),z.stableKey()),savedOrder())
   begin();click("Sort by date added");click("Save order")
   until {device.hasObject(By.desc("Settings"))};assertEquals(listOf(z.stableKey(),a.stableKey()),savedOrder())
  } catch(error:Throwable) {
   device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"sorting111-failure.xml"));snapshot("sorting111-failure");throw error
  } finally {finish(vm,saved,owned(vm))}
 }
 private fun dragPreview(from:UiObject2,to:UiObject2,returned:()->Unit,check:()->Unit) {
  val a=from.visibleCenter;val b=to.visibleCenter;val down=android.os.SystemClock.uptimeMillis()
  fun send(action:Int,x:Float,y:Float) {val e=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;try {instrumentation.uiAutomation.injectInputEvent(e,true)} finally {e.recycle()}}
  send(android.view.MotionEvent.ACTION_DOWN,a.x.toFloat(),a.y.toFloat());Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+200L)
  try {
   for(i in 1..12) {send(android.view.MotionEvent.ACTION_MOVE,a.x+(b.x-a.x)*i/12f,a.y+(b.y-a.y)*i/12f);Thread.sleep(25)}
   Thread.sleep(600);check()
   for(i in 1..12) {send(android.view.MotionEvent.ACTION_MOVE,b.x+(a.x-b.x)*i/12f,b.y+(a.y-b.y)*i/12f);Thread.sleep(25)}
   Thread.sleep(600);returned()
   for(i in 1..12) {send(android.view.MotionEvent.ACTION_MOVE,a.x+(b.x-a.x)*i/12f,a.y+(b.y-a.y)*i/12f);Thread.sleep(25)}
   Thread.sleep(600);check()
  } finally {send(android.view.MotionEvent.ACTION_UP,b.x.toFloat(),b.y.toFloat())}
  device.waitForIdle()
 }
 @Test fun tapReorderSavesBeforeAnyDragHasStarted() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);val a=fixture(vm,"Organize Alpha");val b=fixture(vm,"Organize Beta")
   runBlocking {vm.saveOrder("passes",listOf(a.stableKey(),b.stableKey()))}
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize"
   device.wait(Until.findObject(By.desc("Open Organize Alpha")),5000)!!.longClick();click("Sort / Reorder")
   click("Reorder Organize Alpha");click("Reorder Organize Beta");click("Save order")
   until {vm.items.value.filter {it.title.startsWith("Organize ")}.map {it.stableKey()}==listOf(b.stableKey(),a.stableKey())}
   runBlocking {vm.refresh()}
   assertEquals(listOf(b.stableKey(),a.stableKey()),vm.items.value.filter {it.title.startsWith("Organize ")}.map {it.stableKey()})
  } finally {finish(vm,saved,owned(vm))}
 }
 @Test fun liveReorderCancelSaveAndTapSelectionProtectsScrolling() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);val a=fixture(vm,"Organize Alpha");val b=fixture(vm,"Organize Beta")
   runBlocking {vm.saveOrder("passes",listOf(a.stableKey(),b.stableKey()))}
   fun order()=vm.items.value.filter {it.title.startsWith("Organize ")}.map {it.stableKey()}
   val before=order()
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize"
   fun begin() {device.wait(Until.findObject(By.desc("Open Organize Alpha")),5000) .also { if(it==null) device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"organization-missing.xml")) }!!.longClick();click("Sort / Reorder");assertTrue(device.wait(Until.hasObject(By.text("Save order")),5000))}
   begin();click("Reorder Organize Alpha")
   val source=device.findObject(By.desc("Reorder Organize Beta")).visibleCenter;val target=device.findObject(By.desc("Reorder Organize Alpha")).visibleCenter
   device.swipe(source.x,source.y,target.x,target.y,40)
   click("Save order");until {device.hasObject(By.desc("Settings"))};assertEquals(before,order())
   begin();val oldB=device.findObject(By.desc("Reorder Organize Beta")).visibleBounds.left
   dragPreview(device.findObject(By.desc("Reorder Organize Alpha")),device.findObject(By.desc("Reorder Organize Beta")),returned={assertEquals("Hovering back must restore the destination",oldB,device.findObject(By.desc("Reorder Organize Beta")).visibleBounds.left)}) {
    assertTrue("Hover must displace the destination card",device.findObject(By.desc("Reorder Organize Beta")).visibleBounds.left<oldB)
    assertEquals("No persistence before Save",before,order());snapshot("organization110-live-reorder")
   }
   click("Cancel");assertEquals(before,order())
   begin();click("Reorder Organize Alpha");click("Reorder Organize Alpha") // unselect
   click("Reorder Organize Alpha");click("Reorder Organize Beta");click("Save order")
   until {order()==before.reversed()};runBlocking {vm.refresh()};assertEquals(before.reversed(),order())
  } finally {finish(vm,saved,owned(vm))}
 }
 @Test fun combinedPhotoDetailsAndStagedImageEdits() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm);val front=photo(vm,android.graphics.Color.CYAN);val back=photo(vm,android.graphics.Color.MAGENTA)
   val pass=fixture(vm,"Organize Photo ID",JSONObject().put("sourceType","pkpass").put("thumbnailImagePath",front).put("fields","""{"primaryFields":[{"label":"NAME","value":"Sample Person"}]}"""))
   val member=fixture(vm,"Organize Membership",JSONObject().put("type","storeCard").put("frontImagePath",front).put("backImagePath",back).put("barcodeValue","MEMBER123").put("barcodeFormat","QR_CODE").put("fields","""{"customFields":{"Member number":"123","Membership level":"Gold"}}"""))
   runBlocking {vm.saveOrder("passes",listOf(pass.stableKey()))}
   assertTrue(device.wait(Until.hasObject(By.text("Sample Person")),5000));assertTrue(device.hasObject(By.desc("Pass photo")))
   snapshot("organization110-photo-information")
   click("Cards");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize Membership"
   click("Open Organize Membership")
   assertTrue(device.wait(Until.hasObject(By.desc("More pass actions")),5000));assertFalse(device.hasObject(By.desc("Export encrypted pass")))
   assertTrue(device.hasObject(By.desc("Card logo")));assertFalse(device.hasObject(By.desc("Pass photo")))
   val icon=device.findObject(By.desc("Card logo")).visibleBounds
   snapshot("sorting111-membership-header")
   assertTrue("Membership icon belongs beside the organization",device.findObjects(By.text("Organize Membership")).any {
    val title=it.visibleBounds;icon.right<=title.left && title.bottom>=icon.top && title.top<=icon.bottom
   })
   click("Details");click("Gold");click("View Front image");click("Close image")
   val f=device.findObject(By.desc("View Front image")).visibleBounds;val b=device.findObject(By.desc("View Back image")).visibleBounds
   assertEquals(f.top,b.top);assertTrue(f.right<=b.left)
   assertFalse(device.hasObject(By.desc("Edit Front image")))
   snapshot("organization110-membership-images")
   click("Edit");click("Edit Front image");click("Remove image");click("Remove")
   assertEquals(front,JSONObject(vm.items.value.single {it.stableKey()==member.stableKey()}.json).getString("frontImagePath"))
   click("Cancel");click("Open Organize Membership");click("Edit")
   click("Edit Front image");click("Remove image");click("Remove");click("Save")
   until {JSONObject(vm.items.value.single {it.stableKey()==member.stableKey()}.json).isNull("frontImagePath")}
   assertEquals(back,JSONObject(vm.items.value.single {it.stableKey()==member.stableKey()}.json).getString("backImagePath"))
  } finally {finish(vm,saved,owned(vm))}
 }
 @Test fun frontImageRulesCropMasksAndEphemeralAttachmentRead() {
  val row=JSONObject().put("type","storeCard").put("sourceType","manual").put("frontImagePath","front.enc").put("backImagePath","back.enc")
  val item=VaultItem("passes",1,"Member","","",false,row.toString())
  assertEquals("front.enc",previewImagePath(item,"front"));assertEquals("",previewImagePath(item,"virtualCards"));assertEquals("",previewImagePath(item,"back"))
  row.remove("frontImagePath");assertEquals("",previewImagePath(item.copy(json=row.toString()),"front"))
  row.put("sourceType","pkpass").put("frontImagePath","front.enc")
  assertEquals("",previewImagePath(item.copy(json=row.toString()),"front"))
  val bitmap=android.graphics.Bitmap.createBitmap(240,320,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
  val raw=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
  try {for(aspect in listOf(1f,1.586f)) {
   val cropped=CapturedImageScanner.crop(raw,.8f,.3f,.7f,aspect,aspect==1f)
   try {val decoded=android.graphics.BitmapFactory.decodeByteArray(cropped,0,cropped.size)
    assertEquals(aspect,decoded.width.toFloat()/decoded.height,.02f);assertEquals(0,android.graphics.Color.alpha(decoded.getPixel(0,0)));assertEquals(android.graphics.Color.RED,decoded.getPixel(decoded.width/2,decoded.height/2));decoded.recycle()
   } finally {cropped.fill(0)}
  }} finally {raw.fill(0)}
  val transparent=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888)
  transparent.eraseColor(android.graphics.Color.TRANSPARENT);transparent.setPixel(16,16,android.graphics.Color.BLUE)
  val transparentBytes=java.io.ByteArrayOutputStream().also {transparent.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();transparent.recycle()
  val normalized=CapturedImageScanner.crop(transparentBytes,1f,.5f,.5f)
  try {val decoded=android.graphics.BitmapFactory.decodeByteArray(normalized,0,normalized.size);assertEquals(0,android.graphics.Color.alpha(decoded.getPixel(0,0)));decoded.recycle()} finally {normalized.fill(0);transparentBytes.fill(0)}
  val bytes="Private attachment".toByteArray()
  val uri=AttachmentContent.publish(context,"Notes.txt","text/plain",bytes)
  assertEquals("text/plain",context.contentResolver.getType(uri))
  context.contentResolver.openInputStream(uri)!!.use {assertEquals("Private attachment",it.readBytes().toString(Charsets.UTF_8))}
  if(android.os.Build.VERSION.SDK_INT>=26) context.contentResolver.openFileDescriptor(uri,"r")!!.use {fd->
   android.system.Os.lseek(fd.fileDescriptor,3,android.system.OsConstants.SEEK_SET)
   val part=ByteArray(4);assertEquals(4,android.system.Os.read(fd.fileDescriptor,part,0,4));assertEquals("vate",part.toString(Charsets.UTF_8));part.fill(0)
  }
  AttachmentContent.clear();assertTrue(bytes.all {it==0.toByte()});assertTrue(runCatching {context.contentResolver.openInputStream(uri)}.isFailure)
 }
 @Test fun logoEditorAttachmentsAndHiddenSearchStyle() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm)
   val front=photo(vm,android.graphics.Color.CYAN)
   var member=fixture(vm,"Organize Member editor",JSONObject().put("type","storeCard").put("frontImagePath",front).put("barcodeValue","ABC123").put("barcodeFormat","CODE_128"))
   runBlocking {
    val bytes=vm.image(front)
    try {vm.imageChange(ImageTarget("passes",-1,"kuraLogo"),bytes)} finally {bytes.fill(0)}
    vm.edit(member,member.json,vm.pending.takeDraft())
    vm.attach(member,"Notes.txt","text/plain","Test note".toByteArray())
   }
   member=vm.items.value.single {it.id==member.id && it.table=="passes"}
   assertTrue(app.kura.nativecore.ItemMedia.logo(member.table,JSONObject(member.json)).isNotBlank())
   click("Cards");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize Member editor"
   click("Open Organize Member editor");assertTrue(device.wait(Until.hasObject(By.desc("Card logo")),5000));assertFalse(device.hasObject(By.desc("Pass photo")))
   click("Attachment options: Notes.txt");assertTrue(device.hasObject(By.text("Open")));assertTrue(device.hasObject(By.text("Save a copy")));device.pressBack()
   click("Edit");assertTrue(device.wait(Until.hasObject(By.desc("Edit logo")),5000));assertTrue(device.hasObject(By.text("Save")))
   click("Edit logo");assertTrue(device.hasObject(By.text("Choose photo")));assertTrue(device.hasObject(By.text("Take photo")));device.pressBack()
   assertTrue(device.hasObject(By.desc("Read barcode from photo")));assertTrue(device.hasObject(By.desc("Scan barcode with camera")))
   val matrix=com.google.zxing.MultiFormatWriter().encode("DRAFT-SCAN-112",com.google.zxing.BarcodeFormat.QR_CODE,320,320)
   val bitmap=android.graphics.Bitmap.createBitmap(320,320,android.graphics.Bitmap.Config.ARGB_8888)
   val pixels=IntArray(320*320) {if(matrix[it%320,it/320]) android.graphics.Color.BLACK else android.graphics.Color.WHITE}
   bitmap.setPixels(pixels,0,320,0,0,320,320);pixels.fill(0)
   val scanBytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
   instrumentation.runOnMainSync {vm.pending.imageTarget=ImageTarget("passes",-1,"barcodeValue");vm.pending.cropBytes=scanBytes}
   click("Use barcode");until {vm.pending.uiMemory.form.values["barcodeValue"]=="DRAFT-SCAN-112"}
   assertEquals("ABC123",JSONObject(vm.items.value.single {it.table==member.table && it.id==member.id}.json).getString("barcodeValue"))
   until {scanBytes.all {it==0.toByte()}}
   snapshot("card112-edit-header")
   click("Edit Front image");assertTrue(device.hasObject(By.text("Remove image")));device.pressBack();snapshot("card112-edit-images")
   click("Cancel");click("Settings");click("Navigation & Layout");click("Show Search")
   until {!vm.presentation.value.preferences.search}
   assertFalse(device.hasObject(By.text("Search Style")));click("Back");click("Done")
  } finally {finish(vm,saved,owned(vm))}
 }

 @Test fun editorFieldsAndImportedReadOnly() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm)
   val member=fixture(vm,"Organize Fields",JSONObject().put("type","membership").put("barcodeValue","AB12"))
   val imported=fixture(vm,"Organize Issuer",JSONObject().put("type","generic").put("sourceType","pkpass"))
   assertTrue(runCatching {runBlocking {vm.edit(imported,JSONObject(imported.json).put("organizationName","Changed").toString())}}.isFailure)
   device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize"
   click("Open Organize Issuer");click("More pass actions");assertFalse(device.hasObject(By.text("Edit")));device.pressBack();click("Close")
   click("Cards");click("Open Organize Fields");click("Edit")
   click("Choose Category");assertTrue(device.hasObject(By.text("Membership")));device.pressBack()
   click("Choose Barcode format");click("Code 128")
   assertFalse(device.hasObject(By.text("Additional details and appearance")))
   click("Add custom field");click("Add Custom Field")
   device.findObject(By.clazz("android.widget.EditText")).text="Test amount"
   click("Currency");click("Add");click("Done")
   until {vm.presentation.value.preferences.customFields("wallets").any {it.name=="Test amount" && it.dataType=="currency"}}
   click("Test amount")
   device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)),5000)!!.text="42.50"
   device.pressBack();device.waitForIdle()
   snapshot("editor113-custom-fields")
   click("Save");until {vm.pending.uiMemory.editing.value==null}
   val updated=JSONObject(vm.items.value.single {it.id==member.id && it.table==member.table}.json)
   assertEquals("Code 128",updated.getString("barcodeFormat"))
   assertEquals("Organize Fields",updated.getString("organizationName"))
   assertEquals("42.50",JSONObject(updated.getString("fields")).getJSONObject("customFields").getString("Test amount"))
  } finally {finish(vm,saved,owned(vm))}
 }

 @Test fun categoryFieldMatrix() {
  assertTrue(basicFormFields("wallets","wallets","Credit").contains("maxlimit"))
  assertFalse(basicFormFields("wallets","wallets","Debit").contains("maxlimit"))
  assertTrue(suggestedFields("wallets","Gift Card").any {it.name=="Balance" && it.dataType=="currency"})
  assertFalse(suggestedFields("wallets","Library").any {it.name=="Balance"})
  assertTrue(suggestedFields("identities","Passport").any {it.name=="Issue date" && it.dataType=="date"})
  assertFalse(suggestedFields("identities","Student ID").any {it.name=="License class"})
  assertTrue(suggestedFields("passes","Reservation").any {it.name=="Guests" && it.dataType=="number"})
  defaultCategories.forEach {(section,categories)->categories.filter {it!="Other"}.forEach {category->
   val fields=suggestedFields(section,category)
   assertTrue("Missing defaults for $section/$category",fields.isNotEmpty())
   assertEquals(fields.size,fields.map {it.name}.distinct().size)
   assertTrue(fields.all {it.dataType in customFieldTypes})
  }}
 }

 @Test fun cardFaceRatioIssuerFieldsAndBarcodeEditorOrder() {
  val vm=start();val saved=runBlocking {vm.work {vm.store.settings(it).toString()}}
  try {
   configure(vm)
   val portrait=photo(vm,android.graphics.Color.CYAN)
   val fields=JSONObject("""{"primaryFields":[{"label":"PERSON","value":"Ada Example"}],"secondaryFields":[{"label":"ROLE","value":"Engineer"},{"label":"TEAM","value":"Research"}],"auxiliaryFields":[{"label":"OFFICE","value":"Tokyo"}]}""")
   fixture(vm,"Organize Business",JSONObject().put("type","storeCard").put("sourceType","pkpass").put("thumbnailImagePath",portrait).put("fields",fields.toString()))
   fixture(vm,"Organize Manual",JSONObject().put("type","membership").put("barcodeValue","123456"))
   fixture(vm,"Organize Front",JSONObject().put("type","membership").put("frontImagePath",portrait))
   click("Cards");device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Organize Business"
   for(columns in 1..3) {
    runBlocking {vm.preference("paymentsGridColumns",columns)}
    until {vm.presentation.value.preferences.columns("wallets")==columns};device.waitForIdle()
    val bounds=device.wait(Until.findObject(By.desc("Open Organize Business")),5000)!!.visibleBounds
    assertEquals(CardAspectRatio,bounds.width().toFloat()/bounds.height(),.04f)
    assertTrue(device.hasObject(By.text("Ada Example")));assertTrue(device.hasObject(By.text("Engineer")))
    assertTrue(device.hasObject(By.text("Research")));assertTrue(device.hasObject(By.text("Tokyo")))
    assertTrue(device.hasObject(By.desc("Pass photo")))
    snapshot("cardface114-"+columns+"-columns")
   }
   click("Open Organize Business");assertTrue(device.wait(Until.hasObject(By.text("Research")),5000));click("Close")
   for(name in listOf("Organize Front","Organize Manual")) {
    device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text=name
    val bounds=device.wait(Until.findObject(By.desc("Open "+name)),5000)!!.visibleBounds
    assertEquals(CardAspectRatio,bounds.width().toFloat()/bounds.height(),.04f)
   }
   click("Open Organize Manual");click("Edit")
   val value=device.wait(Until.findObject(By.text("Barcode value")),5000)!!.visibleBounds
   val format=device.wait(Until.findObject(By.desc("Choose Barcode format")),5000)!!.visibleBounds
   val details=device.wait(Until.findObject(By.text("Details")),5000)!!.visibleBounds
   assertTrue(value.top<format.top && format.top<details.top)
   snapshot("cardface114-editor");click("Cancel");click("Settings")
   assertTrue(device.wait(Until.hasObject(By.text("Use biometrics to unlock")),5000))
   assertTrue(device.hasObject(By.textContains("Authentication is always required")))
   assertFalse(device.hasObject(By.text("Require Biometrics")))
  } finally {finish(vm,saved,owned(vm))}
 }

}
