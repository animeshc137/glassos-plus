import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The TMDB API key (v3) comes from local.properties (tmdb.apiKey=...) or the TMDB_API_KEY environment variable.
// It is never committed. Without one, Glass shows its built-in scenes (a key can also be entered in Settings).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbKey: String = (localProps.getProperty("tmdb.apiKey") ?: System.getenv("TMDB_API_KEY") ?: "").trim()

val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace = "io.github.abhishekkaisar.glassos.personal"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.abhishekkaisar.glassos.personal"
        minSdk = 22
        targetSdk = 28
        versionCode = (project.findProperty("releaseVersionCode")?.toString() ?: releaseVersion.getProperty("versionCode")).toInt()
        versionName = project.findProperty("releaseVersionName")?.toString() ?: releaseVersion.getProperty("versionName")
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKey.replace("\"", "")}\"")
    }

    // Optional stable key for debug builds (CI restores it from a secret): without one every build gets a fresh
    // debug key, and the TV refuses to update the installed copy.
    val personalKeystore = System.getenv("GLASSOS_DEBUG_KEYSTORE")
    if (!personalKeystore.isNullOrEmpty()) {
        signingConfigs.create("personal") {
            storeFile = file(personalKeystore)
            storePassword = System.getenv("GLASSOS_DEBUG_PASSWORD")
            keyAlias = "glassos-personal"
            keyPassword = System.getenv("GLASSOS_DEBUG_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("personal")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
