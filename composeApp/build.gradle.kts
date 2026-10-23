import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Renders portfolio.json into the crawlable HTML the browser shows before (and instead of, on browsers without
 * WasmGC) the Compose app. Runs the same parser as the app, so malformed content fails the build, not the site.
 */
val generateStaticSite = tasks.register<JavaExec>("generateStaticSite") {
    group = "build"
    description = "Generates index.html, sitemap.xml and robots.txt from portfolio.json."
    val content = layout.projectDirectory.file("src/commonMain/composeResources/files/portfolio.json")
    val template = layout.projectDirectory.file("web/index.template.html")
    val output = layout.buildDirectory.dir("generated/site")
    inputs.file(content)
    inputs.file(template)
    outputs.dir(output)
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("dev.yowkey.portfolio.site.StaticSiteKt")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(content.asFile.absolutePath, template.asFile.absolutePath, output.get().asFile.absolutePath)
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
