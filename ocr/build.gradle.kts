plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fifa.ocr.ocr"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }
}

dependencies {
    implementation(project(":core"))
}
