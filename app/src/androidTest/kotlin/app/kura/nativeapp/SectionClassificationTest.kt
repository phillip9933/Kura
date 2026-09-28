package app.kura.nativeapp

import app.kura.feature.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SectionClassificationTest {
    private fun item(type:String, archived:Boolean=false, extra:JSONObject=JSONObject()):VaultItem {
        extra.put("type",type)
        return VaultItem("passes",1,"Synthetic","","",archived,extra.toString())
    }
    @Test fun defaultAndSectionOrderPreserveExplicitLegacyPreferences() {
        assertEquals(listOf("Cards","Passes","Identity"),sectionNames.values.toList())
        assertEquals("passes",PresentationSettings.parse(JSONObject()).initialTable)
        assertEquals("wallets",PresentationSettings.parse(JSONObject().put("defaultScreenIndex",0)).initialTable)
        assertEquals("passes",PresentationSettings.parse(JSONObject().put("defaultScreenIndex",99)).initialTable)
    }
    @Test fun cardAliasesKeepStableStorageReferencesAndFullPassData() {
        for(type in listOf("storeCard","loyaltyCard","loyalty","Membership","Membership Card","giftCard","libraryCard","gymCard","inStorePayment")) {
            val row=JSONObject().put("barcodeValue","UNCHANGED").put("frontImagePath","original.enc").put("fields","{\"primaryFields\":[]}")
            val card=item(type,extra=row)
            assertEquals(type,VaultSection.CARDS,card.section)
            assertEquals("passes",card.table);assertEquals(1L,card.id)
            assertEquals("UNCHANGED",JSONObject(card.json).getString("barcodeValue"))
            assertEquals("original.enc",JSONObject(card.json).getString("frontImagePath"))
        }
    }
    @Test fun nullableLegacyCategoriesNeverBecomeLiteralNullLabels() {
        assertEquals("Other",classifyItem("passes",JSONObject().put("type",JSONObject.NULL)).category)
        assertEquals("Card",classifyItem("wallets",JSONObject().put("category",JSONObject.NULL)).category)
        assertEquals("Identity",classifyItem("identities",JSONObject().put("cardType",JSONObject.NULL)).category)
        val fields=JSONObject().put("_kura",JSONObject().put("category",JSONObject.NULL))
        assertEquals("Loyalty",classifyItem("passes",JSONObject().put("type","storeCard").put("fields",fields.toString())).category)
    }
    @Test fun temporalAndSensitiveTypesStayInSeparateSections() {
        for(type in listOf("boardingPass","eventTicket","transitPass","coupon","offer","Reservation","Parking","Temporary Access")) assertEquals(type,VaultSection.PASSES,item(type).section)
        for(type in listOf("campusId","corporateBadge","digitalCredential","genericPrivate","healthInsuranceCard")) assertEquals(type,VaultSection.IDENTITY,item(type).section)
        assertEquals(VaultSection.IDENTITY,VaultItem("identities",1,"","","",false,"{}").section)
    }
    @Test fun categoriesOnlyIncludeActivePopulatedItemsAndNeverFileFormats() {
        val values=listOf(item("storeCard"),item("loyaltyCard"),item("Membership",true),item("coupon"),item("pkpass"),item("pkpasses"))
        assertEquals(listOf("Loyalty"),populatedCategories(values,"wallets"))
        assertEquals(listOf("Coupon","Other"),populatedCategories(values,"passes"))
        assertTrue(populatedCategories(values,"identities").isEmpty())
    }
    @Test fun userClassificationPreservesIssuerLayoutAndFields() {
        val fields=JSONObject().put("primaryFields",org.json.JSONArray()).put("_kura",JSONObject().put("section","wallets").put("category","Gym"))
        val card=item("generic",extra=JSONObject().put("fields",fields.toString()))
        assertEquals(VaultSection.CARDS,card.section);assertEquals("Gym",card.displayCategory)
        assertEquals("generic",JSONObject(card.json).getString("type"))
        fields.getJSONObject("_kura").put("section","identities")
        assertEquals(VaultSection.IDENTITY,item("generic",extra=JSONObject().put("fields",fields.toString())).section)
    }
    @Test fun upcomingHonorsOffsetExpiryAndSectionBoundaries() {
        val now=Instant.parse("2030-01-02T00:00:00Z");val zone=ZoneId.of("Asia/Tokyo")
        fun timed(type:String="eventTicket",date:String="2030-01-02T10:00:00+09:00",expiry:String="",archived:Boolean=false)=item(type,archived,JSONObject().put("relevantDate",date).put("expiry_date",expiry))
        assertEquals(Instant.parse("2030-01-02T01:00:00Z"),upcomingAt(timed(),now,zone))
        assertNull(upcomingAt(timed(date="2030-01-02T08:00:00+09:00"),now,zone))
        assertNull(upcomingAt(timed(expiry="2030-01-01T23:59:59Z"),now,zone))
        assertNull(upcomingAt(timed(type="storeCard"),now,zone))
        assertNull(upcomingAt(timed(archived=true),now,zone))
        assertNotNull(upcomingAt(timed(date="2030-01-02"),now,zone))
        assertNotNull(upcomingAt(timed(date="",expiry="2030-01-03"),now,zone))
        assertNull(upcomingAt(timed(date="invalid"),now,zone))
    }
}
