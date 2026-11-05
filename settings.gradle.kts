import java.security.MessageDigest

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
val cardScanner = providers.gradleProperty("cardScanner").orNull == "true"
val directUpdates = providers.gradleProperty("directUpdates").orNull == "true"
if (directUpdates) {
    val bundle = rootDir.resolve("third_party/android-updater")
    val files = bundle.resolve("SHA256SUMS").readLines().filter { it.isNotBlank() }.map { line ->
        val parts = line.split("  ", limit = 2)
        check(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}"))) { "Invalid updater pin" }
        val file = bundle.resolve(parts[1]).canonicalFile
        check(file.toPath().startsWith(bundle.canonicalFile.toPath()) && file.isFile) { "Missing/unsafe updater file" }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == parts[0]) { "Updater checksum mismatch: ${parts[1]}" }
        parts[1]
    }
    check(files.distinct().size == files.size)
    check(bundle.resolve("maven").walkTopDown().filter { it.isFile }.map {
        it.relativeTo(bundle).invariantSeparatorsPath
    }.toSet() == files.filter { it.startsWith("maven/") }.toSet()) { "Unpinned updater Maven files" }
}
if (cardScanner) {
    mapOf("eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        "jpn" to "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d").forEach { (language, expected) ->
        val model = rootDir.resolve("app/src/cardScanner/assets/card-ocr/$language.traineddata")
        check(model.isFile && MessageDigest.getInstance("SHA-256").digest(model.readBytes()).joinToString("") { "%02x".format(it) } == expected) { "Scanner model pin mismatch" }
    }
    val bundle = rootDir.resolve("third_party/card-scanner")
    val pinned = bundle.resolve("SHA256SUMS").readLines().filter { it.isNotBlank() }.map { line ->
        val parts = line.split("  ", limit = 2)
        check(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}")))
        val file = bundle.resolve(parts[1]).canonicalFile
        check(file.toPath().startsWith(bundle.canonicalFile.toPath()) && file.isFile)
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == parts[0]) { "Scanner checksum mismatch: ${parts[1]}" }
        parts[1]
    }
    check(pinned.distinct().size == pinned.size)
    check(bundle.resolve("maven").walkTopDown().filter { it.isFile }.map {
        it.relativeTo(bundle).invariantSeparatorsPath
    }.toSet() == pinned.filter { it.startsWith("maven/") }.toSet()) { "Unpinned scanner artifacts" }
}
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        if (cardScanner) exclusiveContent {
            forRepository { maven { url = uri(rootDir.resolve("third_party/card-scanner/maven")) } }
            filter { includeGroup("dev.offlinescan") }
        }
        if (directUpdates) exclusiveContent {
            forRepository { maven { url = uri(rootDir.resolve("third_party/android-updater/maven")) } }
            filter { includeModule("io.github.phillip9933", "android-updater") }
        }
    }
}
rootProject.name = "Kura"
include(":core:model", ":core:security", ":core:database")
include(":core:storage", ":core:import")
include(":feature:vault", ":app")

include(":benchmark")
