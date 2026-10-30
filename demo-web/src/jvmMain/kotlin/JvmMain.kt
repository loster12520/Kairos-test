import dev.kairos.render.renderToHtml
import java.io.File

fun main(args: Array<String>) {
    // The JVM target is compiled *without* the Kairos plugin: the style blocks below run the
    // runtime fallback, which must produce exactly the class names the folded JS target has.
    val hero = heroClass()
    val theme = themeClass(Theme.Dark)
    val card = copyCardClass()
    println("hero=" + hero)
    println("theme=" + theme)
    println("card=" + card)

    val outDir = File(args.firstOrNull() ?: "build/reference-site")
    outDir.mkdirs()
    File(outDir, "index.html").writeText(renderToHtml(buildPage()))
    File(outDir, "reference.txt").writeText(
        listOf("hero=" + hero, "theme=" + theme, "card=" + card).joinToString("\n")
    )
    println("wrote=" + File(outDir, "index.html").absolutePath)
}
