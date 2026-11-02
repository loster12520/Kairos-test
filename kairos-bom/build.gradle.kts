plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":kairos-core"))
        api(project(":kairos-browser"))
        api(project(":kairos-htmx"))
        api(project(":kairos-cli"))
        api(project(":kairos-compiler-plugin"))
        api(project(":kairos-gradle-plugin"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = "kairos-bom"
        }
    }
}
