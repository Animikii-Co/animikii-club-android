plugins {
    id("com.android.application")
}

android {
    namespace = "club.animikii.radio"
    compileSdk = 36

    defaultConfig {
        applicationId = "club.animikii.radio"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation("junit:junit:4.13.2")
}
