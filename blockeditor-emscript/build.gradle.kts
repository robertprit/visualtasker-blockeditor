plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":blockeditor-domain"))
    implementation(project(":blockeditor-ir"))
    testImplementation(project(":blockeditor-registry"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
