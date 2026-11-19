pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// CI already installs its JDKs. Avoid resolving a provisioner when explicitly disabled.
// A plugins { ... apply false } declaration would still resolve its artifacts.
buildscript {
    val provisionJdk = providers.gradleProperty("excor.autoProvisionJdk").getOrElse("true")
    require(provisionJdk == "true" || provisionJdk == "false") {
        "excor.autoProvisionJdk must be true or false"
    }
    if (provisionJdk == "true") {
        repositories { gradlePluginPortal() }
        dependencies {
            classpath("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")
        }
    }
}
if (providers.gradleProperty("excor.autoProvisionJdk").getOrElse("true") == "true") {
    apply(plugin = "org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "eXcor"

// Preview apps for trying the library by hand: an Android APK and a Windows EXE. Not published.
include(":preview-android")
include(":preview-desktop")
project(":preview-android").projectDir = file("modules/platforms/preview/android")
project(":preview-desktop").projectDir = file("modules/platforms/preview/desktop")
