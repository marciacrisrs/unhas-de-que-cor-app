plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace = "br.com.unhasdequecor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.com.unhasdequecor"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = (project.findProperty("RELEASE_STORE_FILE") as String?)
                ?: System.getenv("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                // CI/local sem keystore: assina com debug (ver docs/release.md).
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    basePath.set(rootProject.projectDir)
    ignoredBuildTypes = listOf("release")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**")
    reports {
        html.required.set(true)
        sarif.required.set(true)
        checkstyle.required.set(true)
        markdown.required.set(true)
    }
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**")
}

jacoco {
    toolVersion = "0.8.13"
}

val domainCoverageIncludes = listOf(
    "**/br/com/unhasdequecor/domain/**",
)

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Hilt*",
    "**/Hilt_*.*",
    "**/*_Factory*",
    "**/*_MembersInjector*",
    "**/*Module*",
    "**/*Module$*",
    "**/di/**",
)

fun Project.domainClassDirectories(): FileCollection {
    val buildDirPath = layout.buildDirectory.get().asFile
    // AGP 9 (built-in Kotlin compiler): classes live under intermediates/, not tmp/kotlin-classes.
    val kotlinTree = fileTree(
        buildDirPath.resolve("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
    ) {
        include(domainCoverageIncludes)
        exclude(jacocoExcludes)
    }
    val javaTree = fileTree(
        buildDirPath.resolve("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    ) {
        include(domainCoverageIncludes)
        exclude(jacocoExcludes)
    }
    return files(kotlinTree, javaTree)
}

tasks.register<JacocoReport>("jacocoDomainReport") {
    group = "verification"
    description = "Gera relatório JaCoCo focado no pacote domain."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(domainClassDirectories())
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
                "jacoco/testDebugUnitTest.exec",
            )
        },
    )
}

tasks.register<JacocoCoverageVerification>("jacocoDomainCoverageVerification") {
    group = "verification"
    description = "Exige ≥80% de cobertura de linhas no pacote domain."
    dependsOn("jacocoDomainReport")

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(domainClassDirectories())
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
                "jacoco/testDebugUnitTest.exec",
            )
        },
    )

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
