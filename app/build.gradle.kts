plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("ANIMIKII_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("ANIMIKII_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANIMIKII_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANIMIKII_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "club.animikii.radio"
    compileSdk = 36

    defaultConfig {
        applicationId = "club.animikii.radio"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Set ANIMIKII_RELEASE_KEYSTORE, ANIMIKII_RELEASE_STORE_PASSWORD, " +
                    "ANIMIKII_RELEASE_KEY_ALIAS, and ANIMIKII_RELEASE_KEY_PASSWORD before building a release APK."
            )
        }
        if (!file(releaseKeystorePath!!).isFile) {
            throw GradleException("The configured Android release keystore was not found.")
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease") {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation("junit:junit:4.13.2")
}
