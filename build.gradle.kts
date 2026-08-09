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
        property("sonar.qualitygate.wait", envOrProp("SONAR_QUALITY_GATE_WAIT") ?: "false")

        // Relatórios gerados pelo verifyCi / tarefas do módulo :app
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "app/build/reports/jacoco/jacocoDomainReport/jacocoDomainReport.xml",
        )
        property("sonar.androidLint.reportPaths", "app/build/reports/lint-results-debug.xml")
        property("sonar.kotlin.detekt.reportPaths", "app/build/reports/detekt/detekt.xml")
        property("sonar.junit.reportPaths", "app/build/test-results/testDebugUnitTest")

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
            ).joinToString(","),
        )
        property(
            "sonar.coverage.exclusions",
            listOf(
                "**/di/**",
                "**/ui/theme/**",
                "**/*Activity*",
                "**/*Application*",
                "**/BuildConfig.*",
            ).joinToString(","),
        )
    }
}

tasks.named("sonar") {
    group = "verification"
    description = "Envia análise para SonarCloud/SonarQube (requer SONAR_TOKEN + org/projectKey)."
    dependsOn(
        ":app:detekt",
        ":app:lintDebug",
        ":app:jacocoDomainReport",
    )
}

tasks.register("verifyCi") {
    group = "verification"
    description =
        "Roda o mesmo conjunto de verificações do CI (detekt, lint, unit tests, cobertura domain, assembleDebug + release)."
    dependsOn(
        ":app:detekt",
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:jacocoDomainCoverageVerification",
        ":app:assembleDebug",
        ":app:assembleRelease",
    )
}
