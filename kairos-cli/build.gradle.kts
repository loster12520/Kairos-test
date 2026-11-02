plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("dev.kairos.cli.MainKt")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
