plugins { id("com.android.library"); id("com.google.devtools.ksp") }
android {
    namespace = "app.kura.prototype.database"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core:security"))
    api("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("net.zetetic:sqlcipher-android:4.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
