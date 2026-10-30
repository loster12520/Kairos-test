plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    application
}

kotlin {
    jvmToolchain(21)
}

val kairosPluginJar = project(":kairos-compiler-plugin").layout.buildDirectory.file("libs/kairos-compiler-plugin.jar")
val kairosOut = layout.buildDirectory.dir("kairos")
val cliProject = project(":kairos-cli")
val cliJar = cliProject.layout.buildDirectory.file("libs/kairos-cli.jar")
val frontendProject = project(":blog-frontend")
val frontendMap = frontendProject.layout.buildDirectory.dir("kairos")

dependencies {
    implementation(project(":kairos-core"))
    implementation(project(":kairos-htmx"))

    implementation("org.babyfish.jimmer:jimmer-sql-kotlin:0.12.4")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    ksp("org.babyfish.jimmer:jimmer-ksp:0.12.4")

    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation("io.ktor:ktor-server-core-jvm:3.6.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.1")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.6.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.6.1")
    implementation("ch.qos.logback:logback-classic:1.5.16")
}

application {
    mainClass.set("dev.kairos.blog.MainKt")
}

ksp {
    arg("jimmer.dto.dirs", "src/main/dto")
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlin") {
    dependsOn(":kairos-compiler-plugin:jar")
    inputs.file(kairosPluginJar)
    compilerOptions.freeCompilerArgs.addAll(
        "-Xplugin=" + kairosPluginJar.get().asFile.absolutePath,
        "-P", "plugin:dev.kairos:outputDir=" + kairosOut.get().asFile.absolutePath,
        "-P", "plugin:dev.kairos:module=blog-backend",
    )
    outputs.dir(kairosOut)
    outputs.cacheIf { false }
}

/** Merges the backend's styles with the client bundle's styles into the sheet the server serves. */
val mergeStyles by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Merges the server and client stylesheets."
    dependsOn("compileKotlin", ":blog-frontend:compileKotlinJs", ":blog-frontend:jsProductionExecutableCompileSync", ":kairos-cli:jar")
    classpath = files(cliJar)
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        classpath = classpath + cliProject.configurations.getByName("runtimeClasspath")
        val out = layout.buildDirectory.dir("kairos").get().asFile.resolve("blog.css")
        args = listOf(
            "merge",
            "--out", out.absolutePath,
            "--map", kairosOut.get().asFile.resolve("kairos.map.json").absolutePath,
            "--map", frontendMap.get().asFile.resolve("kairos.map.json").absolutePath,
        )
    }
}

tasks.named("classes") { dependsOn(mergeStyles) }
