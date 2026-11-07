import org.gradle.api.artifacts.dsl.LockMode

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencyLocking {
        lockMode = LockMode.STRICT
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.android.library") version "9.1.1" apply false
    kotlin("jvm") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

// Match the named dependency inventory scopes. Optional app/test graphs have
// separate lock states; switching features must not update another variant.
val appLockVariant = listOf("directUpdates", "cardScanner", "installerAcceptance")
    .joinToString("-") { property -> "${property}-${providers.gradleProperty(property).orNull == "true"}" }

val lockedScopes = mapOf(
    ":app" to setOf(
        "debugCompileClasspath", "debugRuntimeClasspath",
        "releaseCompileClasspath", "releaseRuntimeClasspath",
        "debugAndroidTestCompileClasspath", "debugAndroidTestRuntimeClasspath",
    ),
    ":core:model" to setOf("testRuntimeClasspath"),
    ":core:import" to setOf("debugUnitTestRuntimeClasspath"),
)

subprojects {
    val scopes = lockedScopes[path].orEmpty()
    if (scopes.isNotEmpty()) {
        dependencyLocking {
            lockMode = LockMode.STRICT
            if (path == ":app") {
                lockFile = layout.projectDirectory.file("gradle/dependency-locks/$appLockVariant.lockfile")
            }
        }
        configurations.configureEach {
            if (name in scopes) resolutionStrategy.activateDependencyLocking()
        }
    }
}
