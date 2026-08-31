plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":blockeditor-domain"))
    implementation(project(":blockeditor-registry"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":blockeditor-emscript"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
