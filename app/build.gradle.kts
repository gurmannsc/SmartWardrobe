import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The Groq API key is read from secrets.properties (git-ignored) so it never
// appears in the source code or on GitHub. Java reads it as BuildConfig.GROQ_API_KEY.
val secrets = Properties()
val secretsFile = rootProject.file("secrets.properties")
if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

android {
    namespace = "com.example.smartwardrobe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.smartwardrobe"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GROQ_API_KEY", "\"${secrets.getProperty("GROQ_API_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
