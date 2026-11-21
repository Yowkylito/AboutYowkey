package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import java.io.File

/**
 * Entry point of the `generateStaticSite` Gradle task: content, both HTML templates, output directory, and build id,
 * where `<build>` is the id the task gives this build (the git commit), which versions the app script's URL; see
 * [StaticHtml.page].
 */
fun main(args: Array<String>) {
    require(args.size == 5) { "usage: StaticSite <portfolio.json> <index.template.html> <project.template.html> <outputDir> <build>" }
    val (contentPath, templatePath, projectTemplatePath, outputPath, build) = args
    val portfolio = PortfolioParser.parse(File(contentPath).readText())
    val output = File(outputPath).apply { mkdirs() }
    val projectTemplate = File(projectTemplatePath).readText()
    // The generator owns this directory. Remove case studies whose IDs have been renamed or removed.
    val projectPages = portfolio.projects.map { project ->
        File(output, "${project.caseStudyPath}index.html") to StaticHtml.projectPage(portfolio, project, projectTemplate)
    }
    val homePage = StaticHtml.page(portfolio, File(templatePath).readText(), build)
    val projectsDir = File(output, "projects")
    if (projectsDir.exists()) check(projectsDir.deleteRecursively()) { "Could not replace $projectsDir" }
    File(output, "index.html").writeText(homePage)
    projectPages.forEach { (file, html) ->
        file.parentFile.mkdirs()
        file.writeText(html)
    }
    File(output, "sitemap.xml").writeText(StaticHtml.sitemap(portfolio))
    File(output, "robots.txt").writeText(StaticHtml.robots(portfolio))
    File(output, "llms.txt").writeText(StaticHtml.llmsTxt(portfolio))
}
