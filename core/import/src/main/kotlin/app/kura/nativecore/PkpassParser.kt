package app.kura.nativecore

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

data class ParsedPass(val record: JSONObject, val assets: Map<String, ByteArray>, val manifestChecked: Boolean) : AutoCloseable {
    override fun close() = assets.values.forEach { it.fill(0) }
}
object PkpassParser {
    private val formats = setOf("PKBarcodeFormatQR", "PKBarcodeFormatAztec", "PKBarcodeFormatPDF417", "PKBarcodeFormatCode128","QR Code","Aztec","PDF417","Code 128","Code 39","Code 93","EAN-13","EAN-8","UPC-A","UPC-E","Data Matrix","Codabar","ITF","ITF-14","GS1-128","ISBN","Telepen","POSTNET","RM4SCC","EAN-2","EAN-5","QR_CODE","AZTEC","PDF_417","CODE_128","CODE_39","CODE_93","EAN_13","EAN_8","UPC_A","UPC_E","DATA_MATRIX","CODABAR")
    fun parse(input: InputStream, language: String = "en"): List<ParsedPass> = parse(input, language, ImportBudget(), 0)
    private fun parse(input: InputStream, language: String, budget: ImportBudget, depth: Int): List<ParsedPass> {
        require(depth <= 3) { "Nested passes exceed depth limit" }
        val files = BoundedZip.read(input, budget)
        val results = mutableListOf<ParsedPass>()
        try {
            val manifests = files.keys.filter { it == "pass.json" || it.endsWith("/pass.json") }
            if (manifests.isEmpty()) {
                val nested = files.filterKeys { it.endsWith(".pkpass",true) || it.endsWith(".pkpasses",true) }
                require(nested.isNotEmpty())
                nested.values.forEach { results += parse(it.inputStream(), language, budget, depth + 1) }
                return results
            }
            require(manifests.size == 1 && files.keys.none { it.endsWith(".pkpass",true) || it.endsWith(".pkpasses",true) }) { "Ambiguous pass archive" }
            val jsonPath = manifests.single()
            val prefix = jsonPath.removeSuffix("pass.json")
            val json = JSONObject(files.getValue(jsonPath).toString(Charsets.UTF_8))
            require(json.getInt("formatVersion") == 1)
            val types = listOf("storeCard", "coupon", "eventTicket", "generic", "boardingPass").filter(json::has)
            require(types.size == 1) { "Pass must have exactly one type" }
            val strings = listOf(language, language.substringBefore('-'), "en", "Base").distinct()
                .firstNotNullOfOrNull { files[prefix + it + ".lproj/pass.strings"] }?.let(::localized).orEmpty()
            fun local(value: String): String = strings[value] ?: value
            val body = json.getJSONObject(types.single())
            val groups = JSONObject()
            for (name in listOf("primaryFields", "secondaryFields", "auxiliaryFields", "headerFields", "backFields")) {
                val fields = body.optJSONArray(name) ?: JSONArray()
                require(fields.length() <= 100)
                for (i in 0 until fields.length()) {
                    val field = fields.getJSONObject(i)
                    for (key in listOf("label", "value")) if (field.opt(key) is String) field.put(key, local(field.getString(key)))
                }
                groups.put(name, fields)
            }
            val barcodes = json.optJSONArray("barcodes") ?: JSONArray().also { json.optJSONObject("barcode")?.let(it::put) }
            require(barcodes.length() <= 32)
            val barcode = (0 until barcodes.length()).map { barcodes.getJSONObject(it) }.firstOrNull { it.optString("format") in formats }
            val metadata=JSONObject().put("barcodes",barcodes).put("signatureVerified",false)
            json.optJSONObject("userInfo")?.optJSONObject("app.kura.wallet")?.let {info->
                info.optString("section").takeIf {it in setOf("wallets","passes","identities")}?.let {metadata.put("section",it)}
                info.optString("category").trim().takeIf {it.isNotEmpty() && it.length<=100}?.let {metadata.put("category",it)}
                info.optJSONObject("customFields")?.let {groups.put("customFields",it)}
            }
            groups.put("_kura", metadata)
            val record = JSONObject().put("type", types.single()).put("sourceType", "pkpass")
                .put("fields", groups.toString()).put("orderIndex", 0).put("isArchived", false)
            for (key in listOf("organizationName", "description", "logoText")) record.put(key, local(json.optString(key)))
            for (key in listOf("backgroundColor", "foregroundColor", "labelColor"))
                if (json.has(key)) record.put(key, color(json.getString(key)))
            barcode?.let {
                record.put("barcodeValue", it.getString("message")).put("barcodeFormat", it.getString("format"))
                    .put("barcodeAltText", it.optString("altText"))
            }
            record.put("transitType", body.optString("transitType"))
            if (json.has("relevantDate")) record.put("relevantDate", json.getString("relevantDate"))
            if (json.has("expirationDate")) record.put("expiry_date", json.getString("expirationDate"))
            val manifest = files[prefix + "manifest.json"]?.let { JSONObject(it.toString(Charsets.UTF_8)) }
            if (manifest != null) {
                for ((path, bytes) in files) {
                    if (path == prefix + "manifest.json" || path == prefix + "signature") continue
                    require(path.startsWith(prefix))
                    val expected = manifest.getString(path.removePrefix(prefix)).lowercase()
                    require(expected.matches(Regex("[0-9a-f]{40}")))
                    val actual = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
                    require(MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) { "Pass manifest mismatch" }
                }
                for (name in manifest.keys()) require(files.containsKey(prefix + name)) { "Missing manifest asset" }
            }
            val assets = linkedMapOf<String, ByteArray>()
            for ((stem, column) in mapOf("icon" to "iconImagePath", "logo" to "logoImagePath", "strip" to "stripImagePath",
                "thumbnail" to "thumbnailImagePath", "background" to "frontImagePath", "footer" to "footerImagePath")) {
                listOf("@3x.png", "@2x.png", ".png").firstNotNullOfOrNull { files[prefix + stem + it] }
                    ?.let { assets[column] = it.copyOf() }
            }
            results += ParsedPass(record, assets, manifest != null)
            return results
        } catch (e: Throwable) { results.forEach { it.close() }; throw e }
        finally { files.values.forEach { it.fill(0) } }
    }
    private fun localized(bytes: ByteArray): Map<String, String> {
        val charset = if (bytes.size >= 2 && ((bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) ||
            (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()))) Charsets.UTF_16 else Charsets.UTF_8
        val pattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*;")
        return pattern.findAll(bytes.toString(charset)).associate {
            JSONObject("{\"v\":\"" + it.groupValues[1] + "\"}").getString("v") to
                JSONObject("{\"v\":\"" + it.groupValues[2] + "\"}").getString("v")
        }
    }
    fun color(value: String): String {
        if (value.matches(Regex("#[0-9a-fA-F]{6}"))) return value
        val match = Regex("rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)").matchEntire(value)
            ?: error("Invalid pass color")
        val components = match.groupValues.drop(1).map(String::toInt)
        require(components.all { it in 0..255 })
        return "#" + components.joinToString("") { "%02x".format(it) }
    }
}