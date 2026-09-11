plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

tasks.register("packageLinux") {
    group = "distribution"
    description = "Builds Linux release packages (.deb installer and portable .tar.gz)"
    dependsOn(":desktopApp:packageLinux")
}

tasks.register("packageWindows") {
    group = "distribution"
    description = "Builds Windows release packages (.msi, .exe, and .zip)"
    dependsOn(":desktopApp:packageWindows")
}