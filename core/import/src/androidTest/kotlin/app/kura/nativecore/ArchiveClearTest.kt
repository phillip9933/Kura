package app.kura.nativecore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
class ArchiveClearTest {
 @Test fun clearingConfirmedSnapshotKeepsActiveRestoredAndLaterArchivedRecords()=runBlocking {
  val store=VaultStore(InstrumentationRegistry.getInstrumentation().targetContext)
  SensitiveBytes(ByteArray(32) {7}).use {key->
   val opened=store.open(key);val originalSettings=store.settings(opened);val owned=mutableListOf<Pair<String,Long>>();val selected=mutableListOf<Pair<String,Long>>()
   try {
    for(table in listOf("wallets","passes","identities")) {
     for(kind in listOf("active","archived","restored","later")) {
      val label="Archive "+kind+" "+java.util.UUID.randomUUID()
      val titleKey=if(table=="passes") "organizationName" else "name"
      store.insert(opened,table,JSONObject().put(titleKey,label))
      val id=store.rows(opened,table).single {it.optString(titleKey)==label}.getLong("id")
      owned+=table to id
      if(kind!="active") store.archive(opened,table,id,true)
      if(kind in setOf("archived","restored")) selected+=table to id
      if(kind=="restored") store.archive(opened,table,id,false)
     }
    }
    val settings=store.settings(opened)
    settings.put("nativeItemOrder",JSONArray(owned.map {it.first+":"+it.second}))
    settings.put("nativeCategoryOverrides",JSONObject().apply {owned.forEach {put(it.first+":"+it.second,"Preserved label")}})
    settings.put("nativeFavorites",JSONObject().apply {owned.forEach {put(it.first+":"+it.second,true)}})
    settings.put("nativeAddedAt",JSONObject().apply {owned.forEach {put(it.first+":"+it.second,123L)}})
    store.saveSettings(opened,settings)
    try {store.deleteArchived(opened,selected+("invalid" to 1L));fail()} catch(_:IllegalArgumentException) {}
    assertEquals(12,owned.count {store.rows(opened,it.first,it.second).isNotEmpty()})
    store.deleteArchived(opened,selected)
    for((table,group) in owned.groupBy {it.first}) {
     assertTrue(store.rows(opened,table,group[1].second).isEmpty())
     listOf(0,2,3).forEach {assertEquals(1,store.rows(opened,table,group[it].second).size)}
    }
    val overrides=store.settings(opened).getJSONObject("nativeCategoryOverrides")
    assertEquals(9,overrides.length())
    for(name in listOf("nativeFavorites","nativeAddedAt")) {
     val map=store.settings(opened).getJSONObject(name);assertEquals(9,map.length())
     owned.groupBy {it.first}.values.forEach {group->
      assertFalse(map.has(group[1].first+":"+group[1].second))
      listOf(0,2,3).forEach {assertTrue(map.has(group[it].first+":"+group[it].second))}
     }
    }
    owned.groupBy {it.first}.values.forEach {assertTrue(overrides.has(it[2].first+":"+it[2].second))}
   } finally {
    owned.forEach {(table,id)->store.deleteRecord(opened,table,id)}
    store.saveSettings(opened,originalSettings)
    opened.close()
   }
  }
 }
 @Test fun deletingReusedWalletIdClearsOnlyDeletedMetadataAndEditsPreserveIt()=runBlocking {
  val store=VaultStore(InstrumentationRegistry.getInstrumentation().targetContext)
  SensitiveBytes(ByteArray(32) {7}).use {key->
   val opened=store.open(key);val original=store.settings(opened);val owned=mutableListOf<Long>()
   try {
    repeat(2) {
     val label="Favorite deletion "+java.util.UUID.randomUUID()
     store.insert(opened,"wallets",JSONObject().put("name",label))
     owned+=store.rows(opened,"wallets").single {it.optString("name")==label}.getLong("id")
    }
    val target="wallets:"+owned[0];val kept="wallets:"+owned[1]
    store.saveSettings(opened,JSONObject(original.toString())
     .put("nativeFavorites",JSONObject().put(target,true).put(kept,true))
     .put("nativeAddedAt",JSONObject().put(target,123L).put(kept,456L)))
    store.forgetPresentation(opened,"wallets",owned[0])
    assertTrue(store.settings(opened).getJSONObject("nativeFavorites").getBoolean(target))
    assertEquals(123L,store.settings(opened).getJSONObject("nativeAddedAt").getLong(target))
    store.deleteRecord(opened,"wallets",owned[0])
    store.insert(opened,"wallets",JSONObject().put("id",owned[0]).put("name","Reused ID"))
    for(name in listOf("nativeFavorites","nativeAddedAt")) {
     val map=store.settings(opened).getJSONObject(name)
     assertFalse(map.has(target));assertTrue(map.has(kept))
    }
   } finally {owned.forEach {store.deleteRecord(opened,"wallets",it)};store.saveSettings(opened,original);opened.close()}
  }
 }

}
