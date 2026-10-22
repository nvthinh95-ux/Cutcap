plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.miniedit"
    compileSdk = 34
    defaultConfig { applicationId = "com.miniedit"; minSdk = 29; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("com.google.guava:guava:33.0.0-android")
}
