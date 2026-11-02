plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kairos)
    application
}

kotlin {
    jvmToolchain(21)
}

val kairosOut = layout.buildDirectory.dir("kairos/jvm/main")
val cliProject = project(":kairos-cli")
val cliJar = cliProject.layout.buildDirectory.file("libs/" + cliProject.name + "-" + cliProject.version + ".jar")
val frontendProject = project(":blog-frontend")
val frontendMap = frontendProject.layout.buildDirectory.dir("kairos/js/main")

dependencies {
    implementation(project(":kairos-core"))
    implementation(project(":kairos-htmx"))

    implementation(libs.jimmer.sql.kotlin)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    ksp(libs.jimmer.ksp)

    implementation(libs.sqlite.jdbc)
    implementation(libs.hikari)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.kairos.blog.MainKt")
}

ksp {
    arg("jimmer.dto.dirs", "src/main/dto")
}

/** Merges the backend's styles with the client bundle's styles into the sheet the server serves. */
val mergeStyles = tasks.register<JavaExec>("mergeStyles") {
    group = "build"
    description = "Merges the server and client stylesheets."
    dependsOn("compileKotlin", ":blog-frontend:compileKotlinJs", ":blog-frontend:jsProductionExecutableCompileSync", ":kairos-cli:jar")
    classpath = files(cliJar)
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        val out = layout.buildDirectory.dir("kairos").get().asFile.resolve("blog.css")
        args = listOf(
            "merge",
            "--out", out.absolutePath,
            "--map", kairosOut.get().asFile.resolve("kairos.map.json").absolutePath,
            "--map", frontendMap.get().asFile.resolve("kairos.map.json").absolutePath,
        )
    }
}

cliProject.afterEvaluate {
    // Resolve in the configuration phase; see demo-web/build.gradle.kts for the Gradle 9 rule.
    val runtimeClasspath = configurations.getByName("runtimeClasspath").files
    mergeStyles.configure { classpath = files(cliJar, runtimeClasspath) }
}

tasks.named("classes") { dependsOn(mergeStyles) }
