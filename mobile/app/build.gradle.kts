import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Lee claves desde local.properties (que esta en .gitignore) para no
// hardcodearlas en el repo. Si no existen, quedan vacias y la app cae a sus
// alternativas offline (TTS nativo / plantillas).
val secrets = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun secret(name: String): String = secrets.getProperty(name).orEmpty()

android {
    namespace = "com.voxi.captions"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.voxi.captions"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Claves y voces (spec §5/§7). Las keys vienen de local.properties; los
        // voice_id de ElevenLabs son fijos (no son secretos).
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${secret("ELEVENLABS_API_KEY")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
        buildConfigField("String", "ELEVEN_VOICE_MALE", "\"iVUdnmny2hQCx0HoQmTH\"")
        buildConfigField("String", "ELEVEN_VOICE_FEMALE", "\"p5EUznrYaWnafKvUkNiR\"")
        buildConfigField("String", "ELEVEN_VOICE_NEUTRAL", "\"Cq8gMra8w0trADKyB4Hi\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // El modelo de embeddings (.onnx) debe quedar SIN comprimir en el APK para
    // que sherpa-onnx lo pueda leer directo desde assets.
    androidResources {
        noCompress.add("onnx")
    }
}

dependencies {
    // AndroidX base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle + ViewModel para Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Vosk (reconocimiento de voz offline)
    implementation(libs.vosk.android)

    // CameraX + ML Kit Face Mesh (Capa 3 Modo B)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Face Detection: multi-cara + tracking nativo + landmarks (Capa 2 escaneo +
    // Modo B). Reemplaza a Face Mesh, que solo seguia una cara prominente.
    implementation(libs.mlkit.face.detection)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
