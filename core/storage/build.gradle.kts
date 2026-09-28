plugins { id("com.android.library") }
android { namespace = "app.kura.prototype.storage"; compileSdk { version = release(37) { minorApiLevel = 0 } }
 defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { api(project(":core:security")); implementation("org.bouncycastle:bcprov-jdk18on:1.86")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1")
}