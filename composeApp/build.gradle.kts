import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Release signing: precedencia variables de entorno > keystore.properties (raíz,
// gitignored). Nunca se hardcodean credenciales; si falta alguna de las cuatro,
// el signing config de release no se registra y `assembleRelease` produce un APK
// sin firmar (modo degradado) en vez de fallar.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigningValue(fileKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(fileKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "PLAYBOOK_KEYSTORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "PLAYBOOK_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "PLAYBOOK_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "PLAYBOOK_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

// Override de versión sin editar el script; mantiene los defaults actuales.
val playbookVersionCode = (findProperty("playbook.versionCode") as String?)?.toIntOrNull() ?: 1
val playbookVersionName = (findProperty("playbook.versionName") as String?) ?: "1.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.playbook.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.playbook.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = playbookVersionCode
        versionName = playbookVersionName
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile?.let { rootProject.file(it) }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}
