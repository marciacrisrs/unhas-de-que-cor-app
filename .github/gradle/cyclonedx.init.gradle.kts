initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.3.0")
    }
}

rootProject {
    apply(plugin = "org.cyclonedx.bom")
}
