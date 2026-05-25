plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "edu.uic.cs478.s2026.funcenter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "edu.uic.cs478.s2026.funcenter"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        aidl = true
    }

    sourceSets {
        getByName("main") {
            aidl {
                srcDirs("src/main/aidl", "src/main/aidl/edu/uic/cs487/s2026/funcenter/aidl")
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
