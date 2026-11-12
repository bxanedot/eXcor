import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

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
