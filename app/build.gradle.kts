import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Lecture des secrets Spotify depuis local.properties (jamais commité).
// Valeurs par défaut vides / redirect standard pour que le build passe même
// avant que tu aies renseigné le Client ID.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val spotifyClientId: String = localProperties.getProperty("SPOTIFY_CLIENT_ID", "")
val spotifyRedirectUri: String =
    localProperties.getProperty("SPOTIFY_REDIRECT_URI", "bluetoothspotifytrigger://auth")

android {
    namespace = "com.bluetoothspotifytrigger"
    compileSdk = 35
    // Forcé explicitement : seules les build-tools 35.x sont installées
    // (la valeur par défaut d'AGP 8.7 est 34.0.0, absente de ce SDK).
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.bluetoothspotifytrigger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Exposés au code via BuildConfig.SPOTIFY_CLIENT_ID / SPOTIFY_REDIRECT_URI.
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"$spotifyRedirectUri\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Helpers Compose liés au cycle de vie (LifecycleEventEffect,
    // collectAsStateWithLifecycle) : re-vérif des permissions au retour au
    // premier plan, collecte de StateFlow consciente du lifecycle.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // viewModel() dans un composable.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Persistance locale des associations MAC -> URI Spotify.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Spotify App Remote SDK : .aar à déposer dans app/libs/ (non disponible sur
    // Maven Central). fileTree => n'importe quelle version *.aar est prise.
    implementation(fileTree("libs") { include("*.aar") })
    // gson est requis au runtime par le protocole App Remote.
    implementation("com.google.code.gson:gson:2.10.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
