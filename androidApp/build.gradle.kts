import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.android.release.signing)
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val appVersionName = (project.findProperty("appVersion") as? String)
    ?: System.getenv("APP_VERSION")
    ?: "1.0"

val isAlphaBuild = (project.findProperty("alphaBuild") as? String)?.toBoolean()
    ?: System.getenv("ALPHA_BUILD")?.toBoolean()
    ?: false

android {
    namespace = "com.hz_apps.foldershare"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.hz_apps.foldershare"
        if (isAlphaBuild) {
            applicationIdSuffix = ".alpha"
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = appVersionName
    }

    if (isAlphaBuild) {
        sourceSets {
            getByName("release") {
                res.directories.add("src/alpha/res")
            }
            getByName("debug") {
                res.directories.add("src/alpha/res")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/native-image/**"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/license/**"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.version"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
        jniLibs {
            keepDebugSymbols += listOf("**/libandroidx.graphics.path.so", "**/libsqliteJni.so")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            versionNameSuffix = ".debug"
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}