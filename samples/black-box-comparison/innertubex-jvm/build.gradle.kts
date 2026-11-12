import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.MetrolistGroup.innertubex")
            includeGroup("com.github.MetrolistGroup.quackjs")
        }
    }
}

dependencies {
    implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.11.6")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    testImplementation("org.testng:testng:7.5.1")
}

tasks.withType<Test>().configureEach {
    useTestNG()
}

kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }
    sourceSets.main {
        kotlin.srcDir("../common/src/main/kotlin")
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_26
}

application {
    mainClass.set("ComparisonAdapterKt")
}
