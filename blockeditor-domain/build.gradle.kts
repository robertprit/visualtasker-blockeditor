plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(kotlin("reflect"))
    testImplementation(project(":blockeditor-registry"))
}

tasks.test {
    useJUnit()
}
