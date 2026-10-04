plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":workflow-core"))
    implementation(project(":blockeditor-registry"))
    implementation(project(":blockeditor-validation"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
