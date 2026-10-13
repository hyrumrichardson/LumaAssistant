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

android {
    namespace = "io.github.hyrumrichardson.luma"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hyrumrichardson.luma"
        minSdk = 29 // Required by the Home APIs.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("boolean", "HOME_APIS_ENABLED", homeApisEnabled.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
