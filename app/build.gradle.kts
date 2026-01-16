import java.net.URL
import java.io.InputStream
import java.io.OutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "info.proteo.curtain"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.proteo.curtain"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.mlkit.barcode)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.coil.compose)

    implementation(libs.androidx.datastore)

    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

tasks.register("downloadPlotlyJs") {
    description = "Downloads Plotly.js library to assets folder"
    group = "build setup"

    val plotlyVersion = "3.0.1"
    val plotlyUrl = "https://cdn.plot.ly/plotly-$plotlyVersion.min.js"
    val assetsDir = file("src/main/assets")
    val plotlyFile = file("$assetsDir/plotly.min.js")

    outputs.file(plotlyFile)

    doLast {
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }

        if (plotlyFile.exists() && plotlyFile.length() > 1000000) {
            println("Plotly.js already exists (${plotlyFile.length()} bytes)")
            val content = plotlyFile.readText(Charsets.UTF_8)
            if (content.contains("Plotly", ignoreCase = true)) {
                println("Plotly.js appears to be valid")
                return@doLast
            } else {
                println("Existing plotly.min.js appears to be invalid, redownloading...")
            }
        }

        println("Downloading Plotly.js v$plotlyVersion...")
        try {
            val url = URL(plotlyUrl)
            url.openStream().use { input: InputStream ->
                plotlyFile.outputStream().use { output: OutputStream ->
                    input.copyTo(output)
                }
            }
            println("Successfully downloaded Plotly.js (${plotlyFile.length()} bytes)")

            val content = plotlyFile.readText(Charsets.UTF_8)
            if (content.contains("Plotly", ignoreCase = true)) {
                println("Plotly.js validation successful")
            } else {
                println("Downloaded file may not be valid Plotly.js")
            }
        } catch (e: Exception) {
            println("Failed to download Plotly.js: ${e.message}")
            throw e
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadPlotlyJs")
}