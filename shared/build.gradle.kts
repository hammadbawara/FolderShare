import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    
    android {
       namespace = "com.hz_apps.foldershare.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        
        val desktopMain = create("desktopMain") {
            dependsOn(jvmAndAndroidMain)
        }
        val linuxMain = create("linuxMain") {
            dependsOn(desktopMain)
        }
        jvmMain.get().dependsOn(linuxMain)

        jvmAndAndroidMain.dependencies {
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.network.tls.certificates)
            implementation(libs.ktor.client.okhttp)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
            implementation(libs.jmdns)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(libs.jmdns)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Navigation 3 (JetBrains Compose Multiplatform)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)
            implementation(libs.jetbrains.material3.adaptiveNavigation3)
            implementation(libs.compose.material3.adaptive.navigation.suite)

            // Koin
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            
            // Ktor Server Core
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.partial.content)
            implementation(libs.ktor.server.cors)

            // KSoup XML Parser
            implementation(libs.ksoup)

            // Room KMP
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // FileKit
            api(libs.filekit.core)
            api(libs.filekit.compose)

            // Okio
            api(libs.okio)

            // Logging
            api(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}