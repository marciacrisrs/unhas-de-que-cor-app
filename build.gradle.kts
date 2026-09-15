import org.gradle.api.artifacts.dsl.LockMode

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
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

subprojects {
    configurations.configureEach {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

tasks.register("resolveAndLockAll") {
    group = "dependency management"
    description = "Resolves all lockable configurations and writes Gradle dependency lockfiles."
    notCompatibleWithConfigurationCache("Resolves configurations dynamically to persist dependency locks")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Run this task with --write-locks" }
    }
    doLast {
        allprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved }
                .filterNot { configuration ->
                    project.path == ":app" && configuration.name == "debugAndroidTestCompileClasspath"
                }
                .forEach { it.resolve() }
        }
    }
}

tasks.named("sonar") {
    group = "verification"
    description = "Envia análise para SonarCloud/SonarQube (requer SONAR_TOKEN + org/projectKey)."
    dependsOn(":app:detekt", ":app:lintDebug", ":app:jacocoAppReport")
}
