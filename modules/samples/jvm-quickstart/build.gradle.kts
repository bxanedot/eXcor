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

// Match the library's Java 17 bytecode baseline.
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../black-box-comparison/common/src/main/kotlin")
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("QuickstartKt")
}
