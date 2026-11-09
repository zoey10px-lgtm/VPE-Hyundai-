plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.vpe.hyundai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vpe.hyundai"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    testImplementation("junit:junit:4.13.2")
}
