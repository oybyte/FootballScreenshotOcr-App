plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fifa.ocr.feature.result"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":parser"))
    implementation(project(":core"))
}
