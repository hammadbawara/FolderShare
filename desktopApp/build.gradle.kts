import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.jna.platform)

    implementation(libs.compose.uiToolingPreview)
}

val appRawVersion = (project.findProperty("appVersion") as? String)
    ?: System.getenv("APP_VERSION")
    ?: "1.0.0"
// jpackage on Windows strictly requires numeric version format (major.minor.build)
val appNumericVersion = Regex("""^(\d+(\.\d+){1,2})""").find(appRawVersion)?.value ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.hz_apps.foldershare.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(
                project.file("proguard-rules.pro"),
                project(":shared").file("proguard-rules.pro")
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Folder Share"
            packageVersion = appNumericVersion
            description = "Folder Share - Zero-touch local network folder sharing & streaming"
            vendor = "HZ Apps"
            copyright = "© 2026 HZ Apps"

            modules(
                "java.instrument",
                "java.management",
                "jdk.unsupported",
                "java.naming",
                "java.net.http",
                "java.security.jgss",
                "java.sql",
                "jdk.security.auth",
                "jdk.jfr"
            )

            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
                menuGroup = "Network"
                debMaintainer = "HZ Apps <contact@hzapps.com>"
                rpmLicenseType = "GPL-3.0"
                shortcut = true
            }

            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                menuGroup = "Folder Share"
                shortcut = true
                dirChooser = true
                // Install under the current user's profile so the MSI does not
                // require administrator privileges or write to Program Files.
                perUserInstall = true
                menu = true
                upgradeUuid = "d7b5f543-9821-48cf-9a99-4c9182390f77"
            }
        }
    }
}

val packageLinuxTarGz = tasks.register<Tar>("packageLinuxTarGz") {
    group = "distribution"
    description = "Packs the standalone Linux release distributable into a tar.gz archive."
    dependsOn("createReleaseDistributable")
    archiveBaseName.set("foldershare")
    archiveVersion.set(appRawVersion)
    archiveClassifier.set("linux-x64")
    archiveExtension.set("tar.gz")
    compression = Compression.GZIP
    destinationDirectory.set(layout.buildDirectory.dir("release"))

    from(layout.buildDirectory.dir("compose/binaries/main-release/app/Folder Share")) {
        into("foldershare")
    }
}

val copyReleaseDeb = tasks.register<Copy>("copyReleaseDeb") {
    group = "distribution"
    description = "Copies the generated Debian installer package to build/release/"
    dependsOn("packageReleaseDeb")
    from(layout.buildDirectory.dir("compose/binaries/main-release/deb")) {
        include("*.deb")
    }
    into(layout.buildDirectory.dir("release"))
}

tasks.register("packageLinux") {
    group = "distribution"
    description = "Builds all optimized Linux release packages (.deb and .tar.gz) into build/release/"
    dependsOn(copyReleaseDeb, packageLinuxTarGz)
}

val packageWindowsZip = tasks.register<Zip>("packageWindowsZip") {
    group = "distribution"
    description = "Packs the standalone Windows release distributable into a zip archive."
    dependsOn("createReleaseDistributable")
    archiveBaseName.set("foldershare")
    archiveVersion.set(appRawVersion)
    archiveClassifier.set("windows-x64")
    archiveExtension.set("zip")
    destinationDirectory.set(layout.buildDirectory.dir("release"))

    from(layout.buildDirectory.dir("compose/binaries/main-release/app/Folder Share")) {
        into("foldershare")
    }
}

val copyReleaseMsi = tasks.register<Copy>("copyReleaseMsi") {
    group = "distribution"
    description = "Copies the generated Windows MSI installer package to build/release/"
    dependsOn("packageReleaseMsi")
    from(layout.buildDirectory.dir("compose/binaries/main-release/msi")) {
        include("*.msi")
    }
    into(layout.buildDirectory.dir("release"))
}

val copyReleaseExe = tasks.register<Copy>("copyReleaseExe") {
    group = "distribution"
    description = "Copies the generated Windows EXE installer package to build/release/"
    dependsOn("packageReleaseExe")
    from(layout.buildDirectory.dir("compose/binaries/main-release/exe")) {
        include("*.exe")
    }
    into(layout.buildDirectory.dir("release"))
}

tasks.register("packageWindows") {
    group = "distribution"
    description = "Builds all optimized Windows release packages (.msi, .exe, and .zip) into build/release/"
    dependsOn(copyReleaseMsi, copyReleaseExe, packageWindowsZip)
}
