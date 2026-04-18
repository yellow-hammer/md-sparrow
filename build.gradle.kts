import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    `java-library`
    application
    id("cloud.rio.license") version "0.18.0"
    id("com.gradleup.shadow") version "8.3.7"
}

repositories {
    mavenCentral()
}

group = "io.github.yellowhammer"
version = "0.1.0"

// Корень XSD: каталог submodule `namespace-forest/` (см. .gitmodules, gradle.properties `xsd.root`).
val xsdRootPath = (findProperty("xsd.root") as String?) ?: "namespace-forest"
val xsdRootDir = layout.projectDirectory.dir(xsdRootPath)

val xjcXsd2_20 = (findProperty("xjc.xsdRelative.2_20") as String?) ?: "schemas/2.20"
val xjcXsd2_21 = (findProperty("xjc.xsdRelative.2_21") as String?) ?: "schemas/2.21"
val xjcBindings2_20 = (findProperty("xjc.bindingsDir.2_20") as String?) ?: "2.20"
val xjcBindings2_21 = (findProperty("xjc.bindingsDir.2_21") as String?) ?: "2.21"

val xjcOutput2_20 = layout.buildDirectory.dir("generated/sources/xjc-2_20")
val xjcOutput2_21 = layout.buildDirectory.dir("generated/sources/xjc-2_21")

val xjc by configurations.creating
dependencies {
    xjc("org.glassfish.jaxb:jaxb-xjc:4.0.5")
    xjc("org.glassfish.jaxb:jaxb-runtime:4.0.5")

    api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.fasterxml.woodstox:woodstox-core:6.6.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

fun registerXjcTask(
    name: String,
    xsdRelative: String,
    bindingsSubdir: String,
    output: Provider<Directory>,
) {
    tasks.register<JavaExec>(name) {
        group = "build"
        description = "Generate JAXB from $xsdRelative (XSD в submodule namespace-forest)"
        classpath = xjc
        mainClass = "com.sun.tools.xjc.Driver"
        val xsdDir = xsdRootDir.dir(xsdRelative)
        val catalogFile = layout.projectDirectory.file("xjb/ns/$bindingsSubdir/catalog.xml")
        val bindingsFile = layout.projectDirectory.file("xjb/ns/$bindingsSubdir/bindings.xjb")
        workingDir = xsdDir.asFile
        inputs.dir(xsdDir)
        inputs.file(catalogFile)
        inputs.file(bindingsFile)
        outputs.dir(output)
        doFirst {
            val out = output.get().asFile
            out.mkdirs()
            args(
                "-extension",
                "-catalog", catalogFile.asFile.absolutePath,
                "-b", bindingsFile.asFile.absolutePath,
                "-d", out.absolutePath,
                "v8.1c.ru-8.3-MDClasses.xsd"
            )
        }
    }
}

registerXjcTask("xjc2_20", xjcXsd2_20, xjcBindings2_20, xjcOutput2_20)
registerXjcTask("xjc2_21", xjcXsd2_21, xjcBindings2_21, xjcOutput2_21)

sourceSets["main"].java.srcDir(xjcOutput2_20)
sourceSets["main"].java.srcDir(xjcOutput2_21)

tasks.compileJava {
    dependsOn("xjc2_20", "xjc2_21")
}

application {
    mainClass = "io.github.yellowhammer.designerxml.cli.DesignerXmlCli"
}

tasks.jar {
    archiveBaseName.set("md-sparrow")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "md-sparrow"
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveBaseName.set("md-sparrow")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "md-sparrow"
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("xsd.root", project.file(xsdRootPath).absolutePath)
    systemProperty(
        "fixtures.ssl31.root",
        layout.projectDirectory.dir("fixtures/ssl31").asFile.absolutePath,
    )
    systemProperty(
        "samples.root",
        layout.projectDirectory.dir("../1c-platform-samples").asFile.absolutePath,
    )
}

tasks.check {
    dependsOn("license")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// HTML API-документация: ./gradlew javadoc → build/docs/javadoc/index.html
tasks.withType<Javadoc>().configureEach {
    dependsOn("xjc2_20", "xjc2_21")
    options.encoding = "UTF-8"
    // Явно отрезаем сгенерированный JAXB (exclude на задаче не всегда отсекает все roots).
    source = sourceSets["main"].java.matching {
        exclude("**/designerxml/jaxb/**")
    }
}

license {
    header = rootProject.file("license/HEADER.txt")
    skipExistingHeaders = false
    strictCheck = true
    ext["year"] = "2026"
    ext["name"] = "Ivan Karlo <i.karlo@outlook.com>"
    ext["project"] = "md-sparrow"
    mapping("java", "SLASHSTAR_STYLE")
    include("src/main/java/**/*.java")
    include("src/test/java/**/*.java")
    exclude("build/generated/**")
}

// Сгенерированный xjc лежит в sourceSets.main; Gradle 8+ требует явной связи задач.
tasks.named("licenseMain") {
    dependsOn("xjc2_20", "xjc2_21")
}
tasks.named("licenseFormatMain") {
    dependsOn("xjc2_20", "xjc2_21")
}
