import com.android.build.api.dsl.ApplicationBuildType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Recursos compartidos (fuentes OFL e icono de cabecera) con un paquete propio
// para no mezclar `Res` con el código de la app.
compose.resources {
    packageOfResClass = "com.playbook.app.resources"
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

// IA real: precedencia -P > env > local.properties (ignorado). Nunca se versiona
// la key; el default es fake y el gate fuerza fake.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun aiValue(propertyKey: String, envKey: String): String? {
    val property = findProperty(propertyKey) as String?
    if (property != null) return property
    return System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
}

val aiMode = aiValue("playbook.ai.mode", "PLAYBOOK_AI_MODE") ?: "fake"
require(aiMode == "fake" || aiMode == "real") {
    "playbook.ai.mode debe ser 'fake' o 'real' (fue '$aiMode')"
}

val aiBaseUrl = aiValue("playbook.ai.baseUrl", "PLAYBOOK_AI_BASE_URL")
    ?: "https://inference.devexpert.io/v1"
val aiApiKey = aiValue("playbook.ai.apiKey", "PLAYBOOK_AI_API_KEY") ?: ""
val aiEmbeddingDimension = aiValue(
    "playbook.ai.embeddingDimension",
    "PLAYBOOK_AI_EMBEDDING_DIMENSION",
)?.toIntOrNull() ?: 1536
val aiModelChat = aiValue("playbook.ai.model.chat", "PLAYBOOK_AI_MODEL_CHAT") ?: "chat"
val aiModelEmbedding = aiValue(
    "playbook.ai.model.embedding",
    "PLAYBOOK_AI_MODEL_EMBEDDING",
) ?: "embedding"
val aiModelTranscribe = aiValue(
    "playbook.ai.model.transcribe",
    "PLAYBOOK_AI_MODEL_TRANSCRIBE",
) ?: "transcribe"
val aiModelTts = aiValue("playbook.ai.model.tts", "PLAYBOOK_AI_MODEL_TTS") ?: "tts"
val aiModelImage = aiValue("playbook.ai.model.image", "PLAYBOOK_AI_MODEL_IMAGE") ?: "image"
val aiModelJev = aiValue("playbook.ai.model.jev", "PLAYBOOK_AI_MODEL_JEV") ?: "jev"
val aiTtsVoice = aiValue("playbook.ai.tts.voice", "PLAYBOOK_AI_TTS_VOICE") ?: "alloy"
val aiImageSize = aiValue("playbook.ai.image.size", "PLAYBOOK_AI_IMAGE_SIZE") ?: "1024x1024"
val aiImageQuality = aiValue("playbook.ai.image.quality", "PLAYBOOK_AI_IMAGE_QUALITY") ?: "low"
val aiImageBackground = aiValue(
    "playbook.ai.image.background",
    "PLAYBOOK_AI_IMAGE_BACKGROUND",
) ?: "transparent"
val aiImageOutputFormat = aiValue(
    "playbook.ai.image.outputFormat",
    "PLAYBOOK_AI_IMAGE_OUTPUT_FORMAT",
) ?: "png"
val aiTranscribeResponseFormat = aiValue(
    "playbook.ai.transcribe.responseFormat",
    "PLAYBOOK_AI_TRANSCRIBE_RESPONSE_FORMAT",
) ?: "json"
val aiConnectTimeoutMs = aiValue(
    "playbook.ai.http.connectTimeoutMs",
    "PLAYBOOK_AI_HTTP_CONNECT_TIMEOUT_MS",
)?.toIntOrNull() ?: 30_000
val aiReadTimeoutMs = aiValue(
    "playbook.ai.http.readTimeoutMs",
    "PLAYBOOK_AI_HTTP_READ_TIMEOUT_MS",
)?.toIntOrNull() ?: 60_000
// Sólo `-P`: una env/local persistente no debe habilitar el embebido de una key
// real en un APK release sin opt-in explícito por build.
val aiAllowReleaseKey = (findProperty("playbook.ai.allowReleaseKey") as String?)
    ?.toBooleanStrictOrNull() == true

fun validateRealAiConfig() {
    val missing = buildList {
        if (aiApiKey.isBlank()) add("playbook.ai.apiKey")
        if (aiBaseUrl.isBlank()) add("playbook.ai.baseUrl")
        if (aiEmbeddingDimension <= 0) add("playbook.ai.embeddingDimension")
        listOf(
            "chat" to aiModelChat,
            "embedding" to aiModelEmbedding,
            "transcribe" to aiModelTranscribe,
            "tts" to aiModelTts,
            "image" to aiModelImage,
            "jev" to aiModelJev,
        ).filter { it.second.isBlank() }.forEach { add("playbook.ai.model.${it.first}") }
    }
    if (missing.isNotEmpty()) {
        error(
            "Modo IA real incompleto. Faltan: ${missing.joinToString(", ")}. " +
                "Configurá local.properties o variables de entorno; nunca commitees la key.",
        )
    }
}

fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""

fun ApplicationBuildType.applyAiBuildConfig(mode: String) {
    val key = if (mode == "real") aiApiKey else ""
    buildConfigField("String", "AI_MODE", buildConfigString(mode))
    buildConfigField("String", "AI_BASE_URL", buildConfigString(aiBaseUrl))
    buildConfigField("String", "AI_API_KEY", buildConfigString(key))
    buildConfigField("int", "AI_EMBEDDING_DIMENSION", aiEmbeddingDimension.toString())
    buildConfigField("String", "AI_MODEL_CHAT", buildConfigString(aiModelChat))
    buildConfigField("String", "AI_MODEL_EMBEDDING", buildConfigString(aiModelEmbedding))
    buildConfigField("String", "AI_MODEL_TRANSCRIBE", buildConfigString(aiModelTranscribe))
    buildConfigField("String", "AI_MODEL_TTS", buildConfigString(aiModelTts))
    buildConfigField("String", "AI_MODEL_IMAGE", buildConfigString(aiModelImage))
    buildConfigField("String", "AI_MODEL_JEV", buildConfigString(aiModelJev))
    buildConfigField("String", "AI_TTS_VOICE", buildConfigString(aiTtsVoice))
    buildConfigField("String", "AI_IMAGE_SIZE", buildConfigString(aiImageSize))
    buildConfigField("String", "AI_IMAGE_QUALITY", buildConfigString(aiImageQuality))
    buildConfigField("String", "AI_IMAGE_BACKGROUND", buildConfigString(aiImageBackground))
    buildConfigField(
        "String",
        "AI_IMAGE_OUTPUT_FORMAT",
        buildConfigString(aiImageOutputFormat),
    )
    buildConfigField(
        "String",
        "AI_TRANSCRIBE_RESPONSE_FORMAT",
        buildConfigString(aiTranscribeResponseFormat),
    )
    buildConfigField("int", "AI_HTTP_CONNECT_TIMEOUT_MS", aiConnectTimeoutMs.toString())
    buildConfigField("int", "AI_HTTP_READ_TIMEOUT_MS", aiReadTimeoutMs.toString())
}

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
            // `Res`, `Font` y `painterResource` para fuentes/icono versionados.
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                // Smoke real de indexado (fuera del gate): base SQLDelight in-memory.
                implementation(libs.sqldelight.sqlite.driver)
            }
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

    buildFeatures {
        buildConfig = true
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
        getByName("debug") {
            applyAiBuildConfig(aiMode)
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Un release con key embebida es peligroso: sólo se permite con
            // opt-in explícito; si no, se degrada a fake con warning.
            val releaseMode = if (aiMode == "real" && aiAllowReleaseKey) "real" else "fake"
            val releaseTaskRequested = gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }
            if (aiMode == "real" && !aiAllowReleaseKey && releaseTaskRequested) {
                logger.warn(
                    "Playbook: modo IA real solicitado para release sin " +
                        "-Pplaybook.ai.allowReleaseKey=true; se usará fake.",
                )
            }
            applyAiBuildConfig(releaseMode)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// El fail-fast se evalúa sólo cuando la tarea del variante real está en el
// grafo: así un `assembleRelease` fake no falla por la config debug incompleta.
if (aiMode == "real") {
    tasks.matching { it.name.contains("Debug") && !it.name.contains("Release") }.configureEach {
        doFirst { validateRealAiConfig() }
    }
}
if (aiMode == "real" && aiAllowReleaseKey) {
    tasks.matching { it.name.contains("Release") }.configureEach {
        doFirst { validateRealAiConfig() }
    }
}

// El smoke real se habilita explícitamente con -Pplaybook.ai.smoke=1 (o env) y
// se reenvía al JVM de tests; nunca corre en el gate.
val aiSmoke = (findProperty("playbook.ai.smoke") as String?) == "1" ||
    System.getenv("PLAYBOOK_AI_SMOKE") == "1"
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("playbook.ai.smoke", if (aiSmoke) "1" else "0")
}

dependencies {
    debugImplementation(compose.uiTooling)
}
