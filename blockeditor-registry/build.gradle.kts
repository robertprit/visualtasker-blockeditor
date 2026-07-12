plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":blockeditor-domain"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
