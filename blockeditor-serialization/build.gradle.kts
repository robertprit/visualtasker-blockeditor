plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":blockeditor-domain"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":blockeditor-registry"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
