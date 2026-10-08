plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fifa.ocr.feature.capture"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":ocr"))
    implementation(project(":parser"))
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
