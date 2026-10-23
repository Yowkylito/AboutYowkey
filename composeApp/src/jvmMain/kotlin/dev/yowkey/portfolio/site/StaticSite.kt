package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import java.io.File

/**
 * Entry point of the `generateStaticSite` Gradle task: `<portfolio.json> <index.template.html> <outputDir> <build>`,
 * where `<build>` is the id the task gives this build (the git commit), which versions the app script's URL; see
 * [StaticHtml.page].
 */
fun main(args: Array<String>) {
    require(args.size == 4) { "usage: StaticSite <portfolio.json> <index.template.html> <outputDir> <build>" }
    val (contentPath, templatePath, outputPath, build) = args
    val portfolio = PortfolioParser.parse(File(contentPath).readText())
    val output = File(outputPath).apply { mkdirs() }
    File(output, "index.html").writeText(StaticHtml.page(portfolio, File(templatePath).readText(), build))
    File(output, "sitemap.xml").writeText(StaticHtml.sitemap(portfolio))
    File(output, "robots.txt").writeText(StaticHtml.robots(portfolio))
}
