plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

group = "de.visualtasker.workflow"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":emscript-language-core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
