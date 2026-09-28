plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "app.kura.prototype.feature.vault"; compileSdk { version = release(37) { minorApiLevel = 0 } }
 defaultConfig { minSdk = 24 }; buildFeatures { compose = true }
 compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 api(project(":core:model"))
 implementation(project(":core:import"))
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 api(platform("androidx.compose:compose-bom:2025.12.01"))
 api("androidx.compose.material3:material3"); api("androidx.compose.foundation:foundation")
 api("androidx.compose.ui:ui")
 implementation("androidx.compose.material:material-icons-extended:1.7.8")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.core:core-ktx:1.16.0")
 implementation("com.google.zxing:core:3.5.3")
 implementation("uk.org.okapibarcode:okapibarcode:0.5.6") {isTransitive=false}
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}