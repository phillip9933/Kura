package app.kura.feature

import org.json.JSONObject
import java.time.*
import java.util.Locale

/** Presentation/security scope is independent of the legacy storage table and stable record ID. */
typealias VaultSection = app.kura.nativecore.VaultSection
data class ItemClassification(val section: VaultSection, val category: String)

fun classifyItem(table: String, json: JSONObject): ItemClassification {
    fun text(key:String, fallback:String="") = json.optString(key).trim().takeUnless {it.isEmpty() || it=="null"} ?: fallback
    if (table == "identities") return ItemClassification(VaultSection.IDENTITY, text("cardType", "Identity"))
    if (table == "wallets") return ItemClassification(VaultSection.CARDS, text("category", "Card"))
    val type = text("type")
    val normalized = type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
    val category = when (normalized) {
        "storecard", "loyalty", "loyaltycard" -> "Loyalty"
        "instorepayment" -> "Store Card"
        "membership", "membershipcard", "membercard" -> "Membership"
        "gift", "giftcard" -> "Gift Card"
        "library", "librarycard" -> "Library"
        "gym", "gymcard" -> "Gym"
        "boardingpass" -> if (json.optString("transitType") in setOf("PKTransitTypeTrain", "PKTransitTypeBus", "PKTransitTypeBoat")) "Transit" else "Boarding Pass"
        "eventticket", "event" -> "Event"
        "coupon", "offer" -> "Coupon"
        "transitpass" -> "Transit"
        "campusid" -> "Student ID"
        "corporatebadge" -> "Employee ID"
        "digitalcredential" -> "Digital Credential"
        "genericprivate" -> "Private Document"
        "healthinsurancecard" -> "Health Insurance"
        "healthtestrecord" -> "Health Record"
        "healthvaccinecard" -> "Vaccination"
        "hotelkey" -> "Temporary Access"
        "digitalcarkey", "multifamilykey" -> "Access Card"
        "parking", "parkingpass" -> "Parking"
        "reservation" -> "Reservation"
        "temporaryaccess", "temporaryaccesspass" -> "Temporary Access"
        "", "generic", "pkpass", "pkpasses" -> "Other"
        else -> type
    }
    val automatic = if (normalized in setOf("storecard", "loyalty", "loyaltycard", "instorepayment", "digitalcarkey", "multifamilykey", "membership", "membershipcard", "membercard", "gift", "giftcard", "library", "librarycard", "gym", "gymcard")) VaultSection.CARDS else if(normalized in setOf("campusid","corporatebadge","digitalcredential","genericprivate","healthinsurancecard","healthtestrecord","healthvaccinecard")) VaultSection.IDENTITY else VaultSection.PASSES
    val metadata = runCatching { JSONObject(json.optString("fields", "{}")) }.getOrNull()?.optJSONObject("_kura")
    // Overrides are explicit user choices. Never infer identity sensitivity from an issuer's free text.
    val section = when (metadata?.optString("section")) { "wallets" -> VaultSection.CARDS; "passes" -> VaultSection.PASSES; "identities" -> VaultSection.IDENTITY; else -> automatic }
    val override = metadata?.optString("category")?.trim()?.takeIf { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in setOf("pkpass", "pkpasses", "null") }
    return ItemClassification(section, override ?: category)
}

fun populatedCategories(items: List<VaultItem>, section: String): List<String> = items.asSequence()
    .filter { it.section.key == section && !it.archived }.map { it.displayCategory }.filter(String::isNotBlank)
    .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toList()

/** Offset timestamps respect the current time zone; date-only passes remain upcoming through that day. */
fun upcomingAt(item: VaultItem, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant? {
    if (item.section != VaultSection.PASSES || item.archived) return null
    fun date(raw: String, endOfDay: Boolean): Instant? = if(raw.isBlank() || raw=="null") null else runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).atZone(zone).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw).let { if (endOfDay) it.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1) else it.atStartOfDay(zone).toInstant() } }.getOrNull()
    val expiry = date(item.expiryValue, true) ?: expiryDateValue(item.expiryValue)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusNanos(1)
    if (expiry != null && expiry < now) return null
    val relevant = item.relevantValue
    val scheduled = date(relevant, relevant.length == 10)
    return if (scheduled != null) scheduled.takeIf { it >= now } else expiry?.takeIf { it >= now }
}
