import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * The id of a build for the generated page: the short git commit, or the build time (UTC) where there is no git.
 * The page loads the app as `composeApp.js?v=<id>`, so a new deploy's index.html never gets the previous deploy's
 * script out of the GitHub Pages cache. A ValueSource read only at execution time, as the task's input, keeps the
 * configuration cache valid across commits while the task still re-runs for each one.
 */
abstract class BuildId : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String = gitCommit() ?: TIME.format(Instant.now())

    private fun gitCommit(): String? = try {
        val stdout = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        stdout.toString().trim().takeIf { result.exitValue == 0 && it.isNotEmpty() }
    } catch (e: Exception) { // git is not installed
        null
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}

/**
 * Renders portfolio.json into the crawlable HTML the browser shows before (and instead of, on browsers without
 * WasmGC) the Compose app. Runs the same parser as the app, so malformed content fails the build, not the site.
 * The build id ([BuildId]) is an input too, so the page is regenerated for every commit.
 */
val generateStaticSite = tasks.register<JavaExec>("generateStaticSite") {
    group = "build"
    description = "Generates index.html, sitemap.xml and robots.txt from portfolio.json."
    val content = layout.projectDirectory.file("src/commonMain/composeResources/files/portfolio.json")
    val template = layout.projectDirectory.file("web/index.template.html")
    val output = layout.buildDirectory.dir("generated/site")
    val build = providers.of(BuildId::class.java) {}
    inputs.file(content)
    inputs.file(template)
    inputs.property("build", build)
    outputs.dir(output)
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("dev.yowkey.portfolio.site.StaticSiteKt")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(content.asFile.absolutePath, template.asFile.absolutePath, output.get().asFile.absolutePath, build.get())
    })
}

kotlin {
    // skikoMain: what the desktop and the web share and Android has not, Skia (Compose draws with it there).
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withWasmJs()
            }
        }
    }

    jvm()

    // The shared UI as an Android library; the APK itself is :androidApp (AGP 9 keeps the application apart).
    android {
        namespace = "dev.yowkey.portfolio.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Compose resources (portfolio.json, the screenshots, the clips) ship as the library's assets.
        androidResources {
            enable = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // The clips' 60 fps .mp4 twins, hardware-decoded off the UI thread (see ClipVideo.android.kt).
            implementation(libs.androidx.media3.exoplayer)
        }
        wasmJsMain {
            resources.srcDir(generateStaticSite)
        }
    }
}

compose.resources {
    packageOfResClass = "dev.yowkey.portfolio.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "dev.yowkey.portfolio.MainKt"
    }
}
