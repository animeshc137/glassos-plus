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

    // One signing key for every APK this project builds. Android refuses to update an installed app with an APK signed
    // by a different key, so a build signed with anything else cannot be installed over the copy on the TV. CI restores
    // the key from secrets; on a developer machine it is read from ~/.glassos-plus-signing (personal.p12 and
    // password.txt). build-personal.sh checks the finished APK against the pinned certificate.
    val signingHome = File(System.getProperty("user.home"), ".glassos-plus-signing")
    val personalKeystore = System.getenv("GLASSOS_DEBUG_KEYSTORE")?.takeIf { it.isNotEmpty() }
        ?: File(signingHome, "personal.p12").takeIf { it.isFile }?.path
    val personalPassword = System.getenv("GLASSOS_DEBUG_PASSWORD")?.takeIf { it.isNotEmpty() }
        ?: File(signingHome, "password.txt").takeIf { it.isFile }?.readText()?.trim()
    if (personalKeystore != null && !personalPassword.isNullOrEmpty()) {
        signingConfigs.create("personal") {
            storeFile = file(personalKeystore)
            storePassword = personalPassword
            keyAlias = "glassos-personal"
            keyPassword = personalPassword
        }
    } else if (System.getenv("GLASSOS_REQUIRE_PERSONAL_KEY") == "true") {
        throw GradleException("The personal signing key is required but was not found. Set GLASSOS_DEBUG_KEYSTORE and GLASSOS_DEBUG_PASSWORD, or put personal.p12 and password.txt in ~/.glassos-plus-signing.")
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
