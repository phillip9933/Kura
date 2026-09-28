pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "Kura"
include(":core:model", ":core:security", ":core:database")
include(":core:storage", ":core:import")
include(":feature:vault", ":app")

include(":benchmark")
