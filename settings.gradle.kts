import java.security.MessageDigest

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
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
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
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
