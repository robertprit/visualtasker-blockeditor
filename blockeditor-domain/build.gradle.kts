plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":emscript-language-core"))
    testImplementation(libs.junit)
    testImplementation(kotlin("reflect"))
    testImplementation(project(":blockeditor-registry"))
}

tasks.test {
    useJUnit()
}
