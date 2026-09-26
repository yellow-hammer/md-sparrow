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
        // Не 7.x: плагину лицензий нужен конструктор, убранный в Spring 7 (см. build.gradle.kts)
        classpath("org.springframework:spring-core:6.2.19")
        classpath("org.codehaus.plexus:plexus-utils:4.0.3")
        classpath("org.apache.logging.log4j:log4j-core:2.26.1")
        classpath("org.apache.logging.log4j:log4j-api:2.26.1")
    }
    configurations.named("classpath") {
        resolutionStrategy.force(
            "org.springframework:spring-core:6.2.19",
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
                "org.springframework:spring-core:6.2.19",
                "org.codehaus.plexus:plexus-utils:4.0.3",
                "org.apache.logging.log4j:log4j-core:2.26.1",
                "org.apache.logging.log4j:log4j-api:2.26.1",
            )
        }
    }
}
