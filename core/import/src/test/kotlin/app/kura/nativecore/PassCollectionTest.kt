package app.kura.nativecore

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.*

class PassCollectionTest {
    private fun zip(vararg entries:Pair<String,ByteArray>):ByteArray=ByteArrayOutputStream().also {out->
        ZipOutputStream(out).use {zip->entries.forEach {(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}
    }.toByteArray()
    private fun pass(type:String)=zip("pass.json" to """{"formatVersion":1,"organizationName":"Synthetic","TYPE_MARKER":{},"barcode":{"format":"PKBarcodeFormatQR","message":"UNCHANGED"}}""".replace("TYPE_MARKER",type).toByteArray())
    private fun rejects(block:()->Unit) {try {block();fail("Expected bounded collection rejection")} catch(_:IllegalArgumentException) {} catch(_:org.json.JSONException) {}}
    @Test fun mixedPkpassesKeepsActualTypesAndBarcodePayloads() {
        val archive=zip("Loyalty.PKPASS" to pass("storeCard"),"ticket.pkpass" to pass("eventTicket"))
        val parsed=PkpassParser.parse(archive.inputStream())
        try {assertEquals(listOf("storeCard","eventTicket"),parsed.map {it.record.getString("type")})
            assertTrue(parsed.all {it.record.getString("barcodeValue")=="UNCHANGED"})
        } finally {parsed.forEach {it.close()}}
    }
    @Test fun explicitNestedPkpassesSupportedWithSharedDepthLimit() {
        var archive=zip("collection.pkpasses" to zip("one.pkpass" to pass("coupon")))
        val parsed=PkpassParser.parse(archive.inputStream());try {assertEquals(1,parsed.size)} finally {parsed.forEach {it.close()}}
        repeat(4) {archive=zip("collection.pkpasses" to archive)}
        rejects {PkpassParser.parse(archive.inputStream())}
    }
    @Test fun malformedSiblingAndAmbiguousMixedArchiveAreRejected() {
        rejects {PkpassParser.parse(zip("good.pkpass" to pass("coupon"),"bad.pkpass" to zip("pass.json" to "{}".toByteArray())).inputStream())}
        rejects {PkpassParser.parse(zip("pass.json" to """{"formatVersion":1,"generic":{}}""".toByteArray(),"hidden.pkpass" to pass("storeCard")).inputStream())}
    }
    @Test fun collectionUsesOneAggregateUncompressedBudget() {
        val inner=zip("pass.json" to """{"formatVersion":1,"generic":{}}""".toByteArray(),"padding" to ByteArray(6*1024*1024))
        rejects {PkpassParser.parse(zip("one.pkpass" to inner,"two.pkpass" to inner).inputStream())}
    }
}
