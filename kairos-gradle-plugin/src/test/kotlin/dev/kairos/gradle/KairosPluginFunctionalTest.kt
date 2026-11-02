package dev.kairos.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Golden and negative tests for the compiler plugin wiring: a clean fixture project must fold
 * styles without any manual `-Xplugin` flags, and a stylesheet-corrupting declaration must stop
 * the build instead of shipping broken CSS.
 */
class KairosPluginFunctionalTest {

    @TempDir
    lateinit var project: File

    private fun write(path: String, text: String) {
        val file = File(project, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun settingsFile() = write(
        "settings.gradle.kts",
        """
        pluginManagement { repositories { mavenLocal(); mavenCentral(); gradlePluginPortal() } }
        dependencyResolutionManagement { repositories { mavenLocal(); mavenCentral() } }
        rootProject.name = "fixture"
        """.trimIndent(),
    )

    private fun buildFile(extra: String = "") = write(
        "build.gradle.kts",
        """
        plugins {
            kotlin("jvm") version "2.2.20"
            id("dev.kairos") version "0.1.0"
        }
        dependencies { implementation("dev.kairos:kairos-core:0.1.0") }
        $extra
        """.trimIndent(),
    )

    // The fixture resolves dev.kairos from mavenLocal exactly like a real consumer. The test
    // task publishes the current plugin (and core) first, so this is the shipped artifact.
    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(project)
        .withArguments(*arguments, "--stacktrace")

    @Test
    fun `folds a style block and writes the manifest`() {
        settingsFile()
        buildFile()
        write(
            "src/main/kotlin/Fixture.kt",
            """
            import dev.kairos.runtime.css
            import dev.kairos.runtime.color

            val hero = css { color("#111111") }
            """.trimIndent(),
        )

        val result = runner("compileKotlin").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        val stylesheet = File(project, "build/kairos/jvm/main/kairos.css")
        val manifest = File(project, "build/kairos/jvm/main/kairos.map.json")
        assertTrue(stylesheet.exists(), "stylesheet was not written")
        assertTrue(manifest.exists(), "manifest was not written")
        assertTrue(manifest.readText().contains("#111111"), "manifest does not record the declaration")
        assertTrue(
            stylesheet.readText().contains("@layer kairos.base"),
            "stylesheet is missing the base layer block",
        )
        assertTrue(
            manifest.readText().contains("\"module\": \"fixture\""),
            "manifest does not carry the project name as module id",
        )
    }

    @Test
    fun `more than eight combinations fold with linear class growth`() {
        settingsFile()
        buildFile()
        write(
            "src/main/kotlin/Fixture.kt",
            """
            import dev.kairos.runtime.color
            import dev.kairos.runtime.css
            import dev.kairos.runtime.fontSize
            import dev.kairos.runtime.fontWeight
            import dev.kairos.runtime.lineHeight

            fun complex(a: Boolean, b: Boolean, c: Boolean, d: Boolean): String = css {
                fontSize(if (a) "12px" else "14px")
                fontWeight(if (b) "600" else "400")
                color(if (c) "#111111" else "#333333")
                lineHeight(if (d) "1.2" else "1.6")
            }
            """.trimIndent(),
        )

        val result = runner("compileKotlin").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        val manifest = File(project, "build/kairos/jvm/main/kairos.map.json")
        assertTrue(manifest.exists(), "manifest was not written")
        val text = manifest.readText()
        val ruleCount = text.split("\"className\"").size - 1
        assertEquals(8, ruleCount, "the axis model must emit one class per arm (4 properties x 2), not 16 combinations")
        assertTrue(
            text.contains("12px") && text.contains("14px") && text.contains("600") && text.contains("400"),
            "arm declarations are missing from the manifest",
        )
    }

    @Test
    fun `rejects a shorthand and its longhand in the same layer`() {
        settingsFile()
        buildFile()
        write(
            "src/main/kotlin/Fixture.kt",
            """
            import dev.kairos.runtime.css
            import dev.kairos.runtime.padding
            import dev.kairos.runtime.paddingTop
            import dev.kairos.runtime.styles

            val boxed = css { padding("8px") }
            val tight = css { paddingTop("4px") }
            val composed = styles(boxed, tight)
            """.trimIndent(),
        )

        val result = runner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("overlap") || result.output.contains("ambiguous composition"),
            "the failure did not explain the shorthand overlap:\n" + result.output,
        )
    }

    @Test
    fun `fails the build on a declaration that would corrupt the stylesheet`() {
        settingsFile()
        buildFile()
        write(
            "src/main/kotlin/Fixture.kt",
            """
            import dev.kairos.runtime.Layer
            import dev.kairos.runtime.css

            val broken = css(Layer.Base) { declaration("color", "red; }") }
            """.trimIndent(),
        )

        val result = runner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("kairos"),
            "the failure did not mention kairos:\n" + result.output,
        )
        assertTrue(
            result.output.contains("color") || result.output.contains("declaration"),
            "the failure did not explain the offending declaration:\n" + result.output,
        )
    }
}
