import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.sonar)
}

fun envOrProp(name: String, propName: String = name): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (findProperty(propName) as String?)?.takeIf { it.isNotBlank() }

sonar {
    properties {
        // Credenciais / identidade — via env (CI) ou -Psonar.* (local).
        property("sonar.host.url", envOrProp("SONAR_HOST_URL", "sonar.host.url") ?: "https://sonarcloud.io")
        envOrProp("SONAR_TOKEN", "sonar.token")?.let { property("sonar.token", it) }
        envOrProp("SONAR_ORGANIZATION", "sonar.organization")?.let { property("sonar.organization", it) }
        envOrProp("SONAR_PROJECT_KEY", "sonar.projectKey")?.let { property("sonar.projectKey", it) }
        property(
            "sonar.projectName",
            envOrProp("SONAR_PROJECT_NAME", "sonar.projectName") ?: "Unhas de Que Cor",
        )

        property("sonar.sourceEncoding", "UTF-8")

        val appBuildDir = project(":app").layout.buildDirectory.get().asFile
        property(
            "sonar.java.binaries",
            listOf(
                appBuildDir.resolve("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                appBuildDir.resolve("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
            ).joinToString(","),
        )
        property(
            "sonar.java.test.binaries",
            appBuildDir.resolve("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes")
                .absolutePath,
        )

        // Default true: CI/PR só fica verde com Quality Gate aprovado.
        property("sonar.qualitygate.wait", envOrProp("SONAR_QUALITY_GATE_WAIT") ?: "true")

        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${appBuildDir}/reports/jacoco/jacocoAppReport/jacocoAppReport.xml",
        )
        property("sonar.androidLint.reportPaths", "${appBuildDir}/reports/lint-results-debug.xml")
        property("sonar.kotlin.detekt.reportPaths", "${appBuildDir}/reports/detekt/detekt.xml")
        property("sonar.junit.reportPaths", "${appBuildDir}/test-results/testDebugUnitTest")

        property(
            "sonar.exclusions",
            listOf(
                "**/build/**",
                "**/R.class",
                "**/R\$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/*_Hilt*",
                "**/Hilt_*.*",
                "**/*_Factory*",
                "**/*_MembersInjector*",
                "**/di/**",
                "**/tmp/**",
                "**/*.webp",
                "**/*.ttf",
                "**/*.otf",
                "**/*.task",
                "**/*.png",
                "**/*.jpg",
                "**/*.jpeg",
            ).joinToString(","),
        )
        property(
            "sonar.coverage.exclusions",
            listOf(
                "**/di/**",
                "**/ui/theme/**",
                "**/*Activity*",
                "**/*Application*",
                "**/*Screen*",
                "**/ui/components/Brand*",
                "**/ui/components/AsyncContent*",
                "**/ui/components/HistoryRow*",
                "**/ui/components/NailPolishMark*",
                "**/ui/components/ProgressSteps*",
                "**/ui/components/HandTryOn*",
                "**/ui/hand/HandReferenceContent*",
                "**/ui/hand/HandReferenceEffects*",
                "**/ui/hand/HandReferenceModels*",
                "**/ui/hand/HandReferencePreview*",
                "**/ui/hand/HandReferenceScaffold*",
                "**/ui/hand/HandReferenceSheets*",
                "**/ui/navigation/AppNavHost*",
                "**/ui/navigation/AppBottomBar*",
                "**/ui/navigation/BottomDestination*",
                "**/data/vision/MediaPipe*",
                "**/data/vision/HandInferenceVariants*",
                "**/data/vision/HandInferenceVariant*",
                "**/data/vision/nail/GeometricNailSegmenter*",
                "**/data/vision/nail/DetectedNailPolishApplier*",
                "**/data/vision/nail/NailTracker*",
                "**/data/local/datastore/**",
                "**/data/local/hand/**",
                "**/data/local/db/dao/**",
                "**/data/local/db/AppDatabase*",
                "**/data/repository/HandReferenceRepositoryImpl*",
                "**/data/repository/PreferencesRepositoryImpl*",
                "**/data/repository/ColorCatalogRepositoryImpl*",
                "**/BuildConfig.*",
            ).joinToString(","),
        )
    }
}

// Keep the Sonar JaCoCo scope aligned with the unit-tested try-on metrics.
// The app module intentionally keeps an explicit coverage allow-list; this adds
// the newly introduced metrics class to the report without excluding it from analysis.
project(":app") {
    afterEvaluate {
        tasks.named<JacocoReport>("jacocoAppReport") {
            val metricsClasses = fileTree(
                layout.buildDirectory.get().asFile.resolve(
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
                ),
            ) {
                include("**/br/com/unhasdequecor/data/vision/nail/TryOnPipelineMetrics*")
            }
            classDirectories.from(metricsClasses)
        }
    }
}

tasks.named("sonar") {
    group = "verification"
    description = "Envia análise para SonarCloud/SonarQube (requer SONAR_TOKEN + org/projectKey)."
    dependsOn(
        ":app:detekt",
        ":app:lintDebug",
        ":app:jacocoAppReport",
    )
}

tasks.register("verifyCi") {
    group = "verification"
    description =
        "Roda o mesmo conjunto de verificações do CI (detekt, lint, unit tests, cobertura domain/app, assembleDebug + release)."
    dependsOn(
        ":app:detekt",
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:jacocoDomainCoverageVerification",
        ":app:jacocoAppCoverageVerification",
        ":app:assembleDebug",
        ":app:assembleRelease",
    )
}
