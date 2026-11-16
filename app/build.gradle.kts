import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(property: String, envName: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = signingValue("storeFile", "KEYSTORE_FILE")
    ?.let { path -> if (File(path).isAbsolute) file(path) else rootProject.file(path) }

// README is the single source of truth for release notes. Package its release
// sections into the APK so the About screen follows every documented release.
val generatedChangelogDir = layout.buildDirectory.dir("generated/assets/changelog")
val generateChangelogAsset = tasks.register("generateChangelogAsset") {
    val readme = rootProject.file("README.md")
    inputs.file(readme)
    outputs.dir(generatedChangelogDir)
    doLast {
        val output = generatedChangelogDir.get().file("changelog.md").asFile
        output.parentFile.mkdirs()
        output.writeText(readme.readText())
    }
}

android {
    namespace = "app.samehadaku.dl"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.samehadaku.dl"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "2.6.25"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (releaseKeystoreFile != null && releaseKeystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

android.sourceSets["main"].assets.srcDir(generatedChangelogDir)
tasks.matching { it.name == "preBuild" || Regex("merge.*Assets").matches(it.name) }
    .configureEach { dependsOn(generateChangelogAsset) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
