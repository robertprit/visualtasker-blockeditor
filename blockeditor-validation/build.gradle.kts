plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":workflow-core"))
    implementation(project(":blockeditor-registry"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
