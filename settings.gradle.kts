rootProject.name = "md-sparrow"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.springframework:spring-core:7.0.8")
        classpath("org.codehaus.plexus:plexus-utils:4.0.3")
        classpath("org.apache.logging.log4j:log4j-core:2.26.1")
        classpath("org.apache.logging.log4j:log4j-api:2.26.1")
    }
    configurations.named("classpath") {
        resolutionStrategy.force(
            "org.springframework:spring-core:7.0.8",
            "org.codehaus.plexus:plexus-utils:4.0.3",
            "org.apache.logging.log4j:log4j-core:2.26.1",
            "org.apache.logging.log4j:log4j-api:2.26.1",
        )
    }
}

gradle.beforeProject {
    buildscript.configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy.force(
                "org.springframework:spring-core:7.0.8",
                "org.codehaus.plexus:plexus-utils:4.0.3",
                "org.apache.logging.log4j:log4j-core:2.26.1",
                "org.apache.logging.log4j:log4j-api:2.26.1",
            )
        }
    }
}
