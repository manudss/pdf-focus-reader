plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.pdffocus.reader"
    compileSdk = 35
    defaultConfig {
        applicationId = providers.gradleProperty("releasePackage").orNull ?: "com.pdffocus.reader.test"
        manifestPlaceholders["appLabel"] = providers.gradleProperty("releaseLabel").orNull ?: "PDF Focus Test"
        manifestPlaceholders["appIcon"] = if (providers.gradleProperty("releasePackage").orNull == "com.pdffocus.reader") "@drawable/ic_reader_app" else "@drawable/ic_test_app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("releaseCode").orNull?.toInt() ?: 16
        versionName = providers.gradleProperty("releaseVersion").orNull ?: "0.10.15-test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Standard extraction-on-install packaging keeps the two local OCR runtimes downloadable.
    packaging { jniLibs { useLegacyPackaging = true } }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation("org.carrot2:morfologik-speller:2.1.9")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
