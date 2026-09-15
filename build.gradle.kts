import org.gradle.api.artifacts.dsl.LockMode

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    id("org.cyclonedx.bom") version "3.3.0"
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }
}

tasks.register("resolveAndLockAll") {
    group = "dependency management"
    description = "Resolves lockable configurations and writes Gradle dependency lockfiles."
    notCompatibleWithConfigurationCache("Resolves configurations dynamically to persist dependency locks")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Run this task with --write-locks" }
    }
    doLast {
        allprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved }
                .filterNot { it.name.contains("AndroidTest", ignoreCase = true) }
                .forEach { it.resolve() }
        }
    }
}

tasks.named("sonar") {
    group = "verification"
    description = "Envia análise para SonarCloud/SonarQube (requer SONAR_TOKEN + org/projectKey)."
    dependsOn(":app:detekt", ":app:lintDebug", ":app:jacocoAppReport")
}
