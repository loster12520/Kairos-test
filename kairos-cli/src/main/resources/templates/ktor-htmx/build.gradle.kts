plugins {
    kotlin("jvm") version "__KOTLIN__"
    id("dev.kairos") version "__KAIROS__"
    application
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation("dev.kairos:kairos-core:__KAIROS__")
    implementation("dev.kairos:kairos-htmx:__KAIROS__")
    implementation("io.ktor:ktor-server-core-jvm:3.6.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.1")
}

application { mainClass.set("starter.MainKt") }
