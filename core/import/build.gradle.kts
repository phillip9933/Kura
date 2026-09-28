plugins { id("com.android.library") }
android { namespace = "app.kura.prototype.importer"; compileSdk { version = release(37) { minorApiLevel = 0 } }
 defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { api(project(":core:storage")); api(project(":core:database"))
 implementation("org.bouncycastle:bcprov-jdk18on:1.86")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20250517")
 androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
tasks.register<Test>("testLowMemory") {
    val regular = tasks.named<Test>("testDebugUnitTest")
    dependsOn("compileDebugUnitTestKotlin")
    testClassesDirs = regular.get().testClassesDirs
    classpath = regular.get().classpath
    maxHeapSize = "128m"
    filter { includeTestsMatching("app.kura.nativecore.LowMemoryBackupTest") }
}
