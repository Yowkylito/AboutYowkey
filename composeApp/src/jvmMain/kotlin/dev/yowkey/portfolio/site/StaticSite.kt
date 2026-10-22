package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import java.io.File

/** Entry point of the `generateStaticSite` Gradle task: `<portfolio.json> <index.template.html> <outputDir>`. */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: StaticSite <portfolio.json> <index.template.html> <outputDir>" }
    val (contentPath, templatePath, outputPath) = args
    val portfolio = PortfolioParser.parse(File(contentPath).readText())
    val output = File(outputPath).apply { mkdirs() }
    File(output, "index.html").writeText(StaticHtml.page(portfolio, File(templatePath).readText()))
    File(output, "sitemap.xml").writeText(StaticHtml.sitemap(portfolio))
    File(output, "robots.txt").writeText(StaticHtml.robots(portfolio))
}
