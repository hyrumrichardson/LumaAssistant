plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Google Home APIs SDK has to be downloaded by hand (see README). When it isn't installed,
// Luma still builds, with a stub smart-home backend that explains what's missing.
val homeApisVersion = libs.versions.homeApis.get()
val homeApisInstalled = File(
    System.getProperty("user.home"),
    ".m2/repository/com/google/android/gms/play-services-home-types/$homeApisVersion",
).isDirectory
val homeApisEnabled = providers.gradleProperty("luma.homeApis").orNull?.toBoolean() ?: homeApisInstalled
logger.lifecycle("Luma: Google Home APIs ${if (homeApisEnabled) "enabled" else "not installed, using stub"}")

// One version, set in gradle.properties. Release tags must match it (v0.1.0 for 0.1.0).
val lumaVersion = providers.gradleProperty("luma.versionName").get()
val lumaVersionCode = lumaVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "Minor and patch versions must be below 100: $lumaVersion" }
    major * 10_000 + minor * 100 + patch
}

// Release signing comes from ~/.gradle/gradle.properties locally, or environment variables in CI.
// Without it, release builds come out unsigned. See the README's "Releasing" section.
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orNull ?: providers.environmentVariable(env).orNull
val lumaKeystoreFile = signingValue("luma.keystore.file", "LUMA_KEYSTORE_FILE")
val lumaKeystorePassword = signingValue("luma.keystore.password", "LUMA_KEYSTORE_PASSWORD")
val lumaKeyAlias = signingValue("luma.key.alias", "LUMA_KEY_ALIAS") ?: "luma"

android {
    namespace = "io.github.hyrumrichardson.luma"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hyrumrichardson.luma"
        minSdk = 29 // Required by the Home APIs.
        targetSdk = 36
        versionCode = lumaVersionCode
        versionName = lumaVersion

        buildConfigField("boolean", "HOME_APIS_ENABLED", homeApisEnabled.toString())
    }

    signingConfigs {
        if (lumaKeystoreFile != null && lumaKeystorePassword != null) {
            create("release") {
                storeFile = rootProject.file(lumaKeystoreFile)
                storePassword = lumaKeystorePassword
                keyAlias = lumaKeyAlias
                keyPassword = lumaKeystorePassword // PKCS12 keystores use one password for both
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Google's dependency report is an encrypted blob only Google Play can read; F-Droid rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main") {
        kotlin.directories.add(if (homeApisEnabled) "src/homeapi/java" else "src/homestub/java")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    if (homeApisEnabled) {
        implementation(libs.play.services.home)
        implementation(libs.play.services.home.types)
    }

    testImplementation(libs.junit)
}
