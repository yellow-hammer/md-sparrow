import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.javadoc.Javadoc
import java.io.File
import java.security.MessageDigest

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
version = "0.4.1"

// Корень XSD: каталог submodule `resources/namespace-forest/` (см. .gitmodules, gradle.properties `xsd.root`).
val xsdRootPath = (findProperty("xsd.root") as String?) ?: "resources/namespace-forest"
val xsdRootDir = layout.projectDirectory.dir(xsdRootPath)
val schemasDir = xsdRootDir.dir("schemas")

// Пространства имён 1С → имя файла xsd и сегмент Java-пакета. Набор одинаков во всех версиях.
data class Ns(val uri: String, val file: String, val pkg: String)

val schemaNamespaces = listOf(
    Ns("http://v8.1c.ru/8.1/data/core", "v8.1c.ru-8.1-data-core.xsd", "v8_1_data_core"),
    Ns("http://v8.1c.ru/8.1/data/enterprise", "v8.1c.ru-8.1-data-enterprise.xsd", "v8_1_data_enterprise"),
    Ns("http://v8.1c.ru/8.1/data/ui", "v8.1c.ru-8.1-data-ui.xsd", "v8_1_data_ui"),
    Ns("http://v8.1c.ru/8.2/managed-application/core", "v8.1c.ru-8.2-managed-application-core.xsd", "v8_2_managed_application_core"),
    Ns("http://v8.1c.ru/8.2/managed-application/cmi", "v8.1c.ru-8.2-managed-application-cmi.xsd", "v8_2_managed_application_cmi"),
    Ns("http://v8.1c.ru/8.2/managed-application/logform", "v8.1c.ru-8.2-managed-application-logform.xsd", "v8_2_managed_application_logform"),
    Ns("http://v8.1c.ru/8.3/xcf/enums", "v8.1c.ru-8.3-xcf-enums.xsd", "v8_3_xcf_enums"),
    Ns("http://v8.1c.ru/8.3/xcf/readable", "v8.1c.ru-8.3-xcf-readable.xsd", "v8_3_xcf_readable"),
    Ns("http://v8.1c.ru/8.3/xcf/predef", "v8.1c.ru-8.3-xcf-predef.xsd", "v8_3_xcf_predef"),
    Ns("http://v8.1c.ru/8.2/data/spreadsheet", "v8.1c.ru-8.2-data-spreadsheet.xsd", "v8_2_data_spreadsheet"),
    Ns("http://v8.1c.ru/8.2/data/bsl", "v8.1c.ru-8.2-data-bsl.xsd", "v8_2_data_bsl"),
    Ns("http://v8.1c.ru/8.2/managed-application/modules", "v8.1c.ru-8.2-managed-application-modules.xsd", "v8_2_managed_application_modules"),
    Ns("http://v8.1c.ru/8.2/uobjects", "v8.1c.ru-8.2-uobjects.xsd", "v8_2_uobjects"),
    Ns("http://v8.1c.ru/8.3/MDClasses", "v8.1c.ru-8.3-MDClasses.xsd", "mdclasses"),
)

val mdClassesFile = "v8.1c.ru-8.3-MDClasses.xsd"

// Версия формата -> сегмент пакета (2.17 -> v2_17).
fun versionToModelId(version: String): String = "v" + version.replace('.', '_')

// SHA-1 набора *.xsd каталога (для дедупа идентичных форматов в одну модель).
fun schemaSetHash(dir: File): String {
    val md = MessageDigest.getInstance("SHA-1")
    dir.listFiles { f -> f.isFile && f.name.endsWith(".xsd") }
        ?.sortedBy { it.name }
        ?.forEach { md.update(it.readBytes()) }
    return md.digest().joinToString("") { "%02x".format(it) }
}

// Каталоги форматов в submodule (только те, где есть MDClasses.xsd).
fun discoverVersions(): List<String> {
    val dir = schemasDir.asFile
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isDirectory && File(f, mdClassesFile).isFile }
        ?.map { it.name }
        ?.sortedWith(compareBy({ it.substringBefore('.').toIntOrNull() ?: 0 },
            { it.substringAfter('.').toIntOrNull() ?: 0 }))
        ?: emptyList()
}

// Дедуп: версия -> modelId (версия-представитель группы с одинаковым набором схем).
fun dedupModels(versions: List<String>): Map<String, String> {
    val hashToRep = LinkedHashMap<String, String>()
    val versionToModel = LinkedHashMap<String, String>()
    for (v in versions) {
        val h = schemaSetHash(schemasDir.dir(v).asFile)
        val rep = hashToRep.getOrPut(h) { v }
        versionToModel[v] = versionToModelId(rep)
    }
    return versionToModel
}

fun bindingsXml(modelId: String): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
    sb.append("<jaxb:bindings version=\"3.0\" xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n")
    for (ns in schemaNamespaces) {
        sb.append("  <jaxb:bindings scd=\"x-schema::tns\" xmlns:tns=\"").append(ns.uri).append("\">\n")
        sb.append("    <jaxb:schemaBindings>\n")
        sb.append("      <jaxb:package name=\"io.github.yellowhammer.designerxml.jaxb.")
            .append(modelId).append('.').append(ns.pkg).append("\"/>\n")
        sb.append("    </jaxb:schemaBindings>\n")
        sb.append("  </jaxb:bindings>\n")
    }
    sb.append("</jaxb:bindings>\n")
    return sb.toString()
}

fun catalogXml(absSchemaDir: File): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n")
    for (ns in schemaNamespaces) {
        if (ns.file == mdClassesFile) continue
        val uri = File(absSchemaDir, ns.file).toURI().toString()
        sb.append("  <uri name=\"").append(ns.uri).append("\" uri=\"").append(uri).append("\"/>\n")
    }
    sb.append("</catalog>\n")
    return sb.toString()
}

val discoveredVersions = discoverVersions()
val versionToModel = dedupModels(discoveredVersions)
val modelToVersions = versionToModel.entries.groupBy({ it.value }, { it.key })

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
    testImplementation("org.assertj:assertj-core:3.27.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Регистрируем по одной задаче XJC на КАЖДУЮ РАЗЛИЧНУЮ модель (после дедупа).
// bindings.xjb и catalog.xml генерируются из шаблонов в build/generated/xjb/<modelId>.
val xjcTaskNames = mutableListOf<String>()
val xjcOutputDirs = mutableListOf<Provider<Directory>>()

for ((modelId, versions) in modelToVersions) {
    val representativeVersion = versions.first()
    val xsdDir = schemasDir.dir(representativeVersion)
    val output = layout.buildDirectory.dir("generated/sources/xjc-$modelId")
    val genXjbDir = layout.buildDirectory.dir("generated/xjb/$modelId")
    val taskName = "xjc_$modelId"
    xjcTaskNames += taskName
    xjcOutputDirs += output

    tasks.register<JavaExec>(taskName) {
        group = "build"
        description = "Generate JAXB for model $modelId (версии: ${versions.joinToString(", ")})"
        classpath = xjc
        mainClass = "com.sun.tools.xjc.Driver"
        workingDir = xsdDir.asFile
        inputs.dir(xsdDir)
        outputs.dir(output)
        doFirst {
            val out = output.get().asFile
            out.mkdirs()
            val xjbDir = genXjbDir.get().asFile
            xjbDir.mkdirs()
            val bindingsFile = File(xjbDir, "bindings.xjb")
            val catalogFile = File(xjbDir, "catalog.xml")
            bindingsFile.writeText(bindingsXml(modelId))
            catalogFile.writeText(catalogXml(xsdDir.asFile))
            args(
                "-extension",
                "-catalog", catalogFile.absolutePath,
                "-b", bindingsFile.absolutePath,
                "-d", out.absolutePath,
                mdClassesFile
            )
        }
    }
    sourceSets["main"].java.srcDir(output)
}

tasks.compileJava {
    dependsOn(xjcTaskNames)
}

application {
    mainClass = "io.github.yellowhammer.designerxml.cli.DesignerXmlCli"
}

// Эталоны (submodule samples-1c-platform) → ресурсы jar для scaffold по golden.
tasks.named<Copy>("processResources") {
    // Голые объекты конфигурации: golden/<формат>/<подкаталог>/…
    from("fixtures/samples-1c-platform/snapshots") {
        include("*/cf-bare-objects/**")
        includeEmptyDirs = false
        eachFile {
            val segs = relativePath.segments
            relativePath = RelativePath(true, "golden", segs[0], *segs.drop(2).toTypedArray())
        }
    }
    // Пустое расширение: golden-cfe/<формат>/…
    from("fixtures/samples-1c-platform/snapshots") {
        include("*/cfe-empty/**")
        includeEmptyDirs = false
        eachFile {
            val segs = relativePath.segments
            relativePath = RelativePath(true, "golden-cfe", segs[0], *segs.drop(2).toTypedArray())
        }
    }
    // Голые внешние объекты (отчёт/обработка): golden-ext/<формат>/<Имя>/<Имя>.xml
    from("fixtures/samples-1c-platform/snapshots") {
        include("*/external-files/empty/**")
        includeEmptyDirs = false
        eachFile {
            val segs = relativePath.segments
            relativePath = RelativePath(true, "golden-ext", segs[0], *segs.drop(3).toTypedArray())
        }
    }
}

tasks.jar {
    archiveBaseName.set("md-sparrow")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "md-sparrow"
        // версию CLI берёт отсюда: иначе её пришлось бы дублировать в коде и она бы отставала
        attributes["Implementation-Version"] = project.version
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveBaseName.set("md-sparrow")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "md-sparrow"
        attributes["Implementation-Version"] = project.version
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
        layout.projectDirectory.dir("fixtures/samples-1c-platform").asFile.absolutePath,
    )
}

tasks.check {
    dependsOn("license")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    // Компиляция ~24k сгенерированных JAXB-классов: отдельный процесс с своей памятью.
    options.isFork = true
    options.forkOptions.memoryMaximumSize = "2g"
}

// HTML API-документация: ./gradlew javadoc → build/docs/javadoc/index.html
tasks.withType<Javadoc>().configureEach {
    dependsOn(xjcTaskNames)
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
    dependsOn(xjcTaskNames)
}
tasks.named("licenseFormatMain") {
    dependsOn(xjcTaskNames)
}
