import java.util.Properties
import java.io.File
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "app.kura.nativeapp"; compileSdk { version = release(37) { minorApiLevel = 0 } }
 defaultConfig { applicationId = "app.kura.wallet"; minSdk = 24; targetSdk = 36; versionCode = 118; versionName = "2.0.0"

 testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }

 buildTypes {
    debug { applicationIdSuffix = ".prototype"; versionNameSuffix = "-dev"; buildConfigField("boolean","PROTOTYPE","true") }
    release {
        isMinifyEnabled = true; isShrinkResources = true
        buildConfigField("boolean","PROTOTYPE","false")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
    }
    create("profile") {
        initWith(getByName("release")); applicationIdSuffix = ".prototype.profile"
        isMinifyEnabled = false; isShrinkResources = false; isDebuggable = false
        signingConfig = signingConfigs.getByName("debug")
        buildConfigField("boolean","PROTOTYPE","true")
        matchingFallbacks += listOf("release")
    }
    create("benchmark") {
        initWith(getByName("release")); applicationIdSuffix = ".prototype.benchmark"
        signingConfig = signingConfigs.getByName("debug")
        buildConfigField("boolean","PROTOTYPE","true")

        matchingFallbacks += listOf("release")
    }
 }

    dependenciesInfo { includeInApk = false; includeInBundle = false }
    // Release stays unsigned unless the existing signing identity is explicitly supplied.
    providers.gradleProperty("releaseSigningProperties").orNull?.let { suppliedPath ->
        val configFile = rootProject.file(suppliedPath).canonicalFile
        val properties = Properties().apply { configFile.inputStream().use { load(it) } }
        fun required(name: String) = requireNotNull(properties.getProperty(name)) { "Missing signing property: $name" }
        signingConfigs.create("releaseKey") {
            keyAlias = required("keyAlias")
            keyPassword = required("keyPassword")
            storePassword = required("storePassword")
            val path = providers.gradleProperty("releaseKeystore").orNull ?: required("storeFile")
            storeFile = File(path).let { if (it.isAbsolute) it else configFile.parentFile.resolve(it) }.canonicalFile
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("releaseKey")
    }

 // Include local libraries so lint can find issues beyond the Activity host.
 lint { checkDependencies = true }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 implementation("androidx.profileinstaller:profileinstaller:1.4.1")
 implementation("androidx.camera:camera-camera2:1.4.2")
 implementation("androidx.camera:camera-lifecycle:1.4.2")
 implementation("androidx.camera:camera-view:1.4.2")
 implementation(project(":feature:vault")); implementation(project(":core:import"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.fragment:fragment-ktx:1.8.9")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
 implementation("androidx.core:core-ktx:1.16.0")
 implementation("androidx.biometric:biometric:1.1.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.google.zxing:core:3.5.3")

 androidTestImplementation("androidx.lifecycle:lifecycle-process:2.9.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
