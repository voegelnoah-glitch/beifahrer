import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
}
// Firebase (gemeinsame Gefahren) nur, wenn app/google-services.json da ist – sonst baut die App ohne Cloud.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// API-Schlüssel aus local.properties lesen (wird nicht ins Git eingecheckt)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: ""
// Versionsnummer: in GitHub Actions automatisch die Build-Nummer, lokal 1
val ciVersion: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
// Woher Updates kommen ("besitzer/repo"): in GitHub Actions automatisch, lokal aus local.properties
val updateRepo: String = System.getenv("GITHUB_REPOSITORY") ?: localProps.getProperty("UPDATE_REPO") ?: ""

android {
    namespace = "at.beifahrer"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.beifahrer"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersion
        versionName = "1.$ciVersion"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // Signatur für die Release-APK (Schlüssel kommt in GitHub aus den "Secrets")
    signingConfigs {
        create("release") {
            val ks = file("release.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: localProps.getProperty("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD") ?: localProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // vom Navigation SDK verlangt
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Das Navigation SDK enthält die Karte schon. Die normale Maps-Bibliothek darf nicht zusätzlich drin sein.
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)

    implementation(libs.navigation.sdk)
    implementation(libs.turnbyturn)
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    implementation(libs.places)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.geofire)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)

    coreLibraryDesugaring(libs.desugar.nio)
    testImplementation(libs.junit)
}
