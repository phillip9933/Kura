package app.kura.feature


val itemSortChoices=linkedMapOf("custom" to "Custom order","nameAsc" to "Name A–Z","nameDesc" to "Name Z–A","addedDesc" to "Newest added","addedAsc" to "Oldest added")
fun VaultItem.stableKey()=table+":"+id
fun sortedVaultItems(items:List<VaultItem>,prefs:VaultPreferences,section:String,pinFavorites:Boolean=true,mode:String=prefs.sortMode(section)):List<VaultItem> {
 val ordered=when(mode) {
  "nameAsc"->items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {it.title})
  "nameDesc"->items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) {it.title})
  "addedAsc","addedDesc"->items.sortedWith(compareBy<VaultItem> {prefs.addedAt(it)==0L}.thenComparator {a,b->
   val time=prefs.addedAt(a).compareTo(prefs.addedAt(b))
   if(time!=0) if(mode=="addedDesc") -time else time else compareValuesBy(a,b,{it.table},{it.id})
  })
  else->items
 }
 return if(pinFavorites && prefs.bool("favoritesFirst",false)) ordered.sortedByDescending {prefs.isFavorite(it)} else ordered
}
/** A draft is presentation-only until Save order. Other sections and newly added items retain their slots. */
fun mergeSectionOrder(all:List<VaultItem>,section:String,requested:List<String>):List<String> {
 require(requested.distinct().size==requested.size)
 val eligible=all.filter {it.section.key==section && !it.archived}.map {it.stableKey()}.toSet()
 require(requested.all {it in eligible})
 val queue=(requested+all.map {it.stableKey()}.filter {it in eligible && it !in requested}).iterator()
 return all.map {if(it.stableKey() in eligible) queue.next() else it.stableKey()}
}
fun movedKeys(keys:List<String>,source:String,target:String):List<String> {
 val from=keys.indexOf(source);val to=keys.indexOf(target)
 if(from<0 || to<0 || from==to) return keys
 return keys.toMutableList().apply {add(to,removeAt(from))}
}

/** Switching sort families starts with A-Z or newest; repeating reverses direction. */
fun toggledSortMode(current:String,family:String):String = when(family) {
 "name" -> if(current=="nameAsc") "nameDesc" else "nameAsc"
 "added" -> if(current=="addedDesc") "addedAsc" else "addedDesc"
 else -> "custom"
}
