plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tupedalada.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tupedalada.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-beta3"
        vectorDrawables { useSupportLibrary = true }
    }

    // Firma fija de las betas: el almacén de claves NO está en el repositorio; GitHub Actions lo
    // reconstruye desde los secretos BETA_KEYSTORE_B64 y BETA_KEYSTORE_PASSWORD. Sin ellos se usa
    // la firma de depuración por defecto (y Android no deja instalar encima de otra versión).
    val betaKeystore = System.getenv("BETA_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (betaKeystore != null) create("beta") {
            storeFile = betaKeystore
            storePassword = System.getenv("BETA_KEYSTORE_PASSWORD")
            keyAlias = "tupedalada-beta"
            keyPassword = System.getenv("BETA_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-debug"
            if (betaKeystore != null) signingConfig = signingConfigs.getByName("beta")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // org.json real para las pruebas (en Android lo aporta el sistema).
    testImplementation("org.json:json:20240303")
}
