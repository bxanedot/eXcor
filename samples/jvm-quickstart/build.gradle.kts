import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

// As in the README's Installation section.
repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.excor:eXcor:0.2.0")
    testImplementation("org.testng:testng:7.5.1")
}

tasks.withType<Test>().configureEach {
    useTestNG()
}

// The library's JVM artifact is JDK 26 bytecode (see the README), so the app targets 26 as well.
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }
}

java {
    targetCompatibility = JavaVersion.VERSION_26
}

application {
    mainClass.set("QuickstartKt")
}
