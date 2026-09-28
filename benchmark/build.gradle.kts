plugins { id("com.android.library") }
android {
    namespace = "app.kura.benchmark"; compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig { minSdk = 24; testApplicationId = "app.kura.prototype.benchmarktest"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
