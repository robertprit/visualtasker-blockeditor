plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":emscript-language-core"))
    implementation(project(":workflow-core"))
    implementation(project(":workflow-semantics"))
    testImplementation(project(":blockeditor-registry"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
