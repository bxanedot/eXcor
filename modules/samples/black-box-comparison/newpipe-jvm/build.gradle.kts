plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.27.1")
}

application {
    mainClass = "org.excor.comparison.newpipe.NewPipeComparisonAdapter"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}