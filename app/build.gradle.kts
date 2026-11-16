import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clé de signature : keystore.properties (jamais commité) à la racine du projet :
//   storeFile=C:/chemin/vers/cle.jks
//   storePassword=…
//   keyAlias=…
//   keyPassword=…
// Absent (ex. clone frais) → les builds utilisent la clé de debug Android par défaut.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

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
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug signé avec la MÊME clé que la release (si dispo) : les deux
        // versions s'installent l'une par-dessus l'autre sans perdre les réglages.
        debug {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        // En test unitaire JVM, les classes Android (ex. android.util.Log) sont
        // des bouchons qui lèvent une exception : on leur fait renvoyer 0/null.
        unitTests.isReturnDefaultValues = true
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

    // MediaControllerCompat : pilotage de la session média de Spotify (mode
    // aléatoire), y compris les commandes « compat » absentes de l'API framework.
    implementation("androidx.media:media:1.7.0")

    // Persistance locale des associations MAC -> URI Spotify.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests unitaires JVM (logique pure, sans émulateur) : ./gradlew testDebugUnitTest
    testImplementation("junit:junit:4.13.2")
    // Temps virtuel pour tester les délais (attente A2DP, cooldown) instantanément.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
