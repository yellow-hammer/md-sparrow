import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.javadoc.Javadoc
import java.io.File
import java.security.MessageDigest

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        constraints {
            classpath("org.springframework:spring-core:7.0.8")
            classpath("org.codehaus.plexus:plexus-utils:4.0.3")
            classpath("org.apache.logging.log4j:log4j-core:2.26.1")
            classpath("org.apache.logging.log4j:log4j-api:2.26.1")
        }
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
version = "0.5.7"

// Корень XSD: каталог submodule `resources/namespace-forest/` (см. .gitmodules, gradle.properties `xsd.root`).
val xsdRootPath = (findProperty("xsd.root") as String?) ?: "resources/namespace-forest"
val xsdRootDir = layout.projectDirectory.dir(xsdRootPath)
// XSD выгрузки конфигуратора: schemas/designer/<версия формата>
val schemasDir = xsdRootDir.dir("schemas/designer")

// Метамодель 1С:EDT: schemas/edt/<версия EDT>/ecore
val edtSchemasDir = xsdRootDir.dir("schemas/edt")

/** Самая свежая версия схем EDT в хранилище: свойства только добавляются, поэтому старые проекты она описывает тоже. */
fun latestEdtVersion(): String? =
    edtSchemasDir.asFile.listFiles { file: File -> file.isDirectory && file.name.matches(Regex("[0-9]{4}[.][0-9]+")) }
        ?.maxByOrNull { file ->
            val (year, release) = file.name.split(".").map(String::toInt)
            year * 100 + release
        }
        ?.name

/**
 * Схемы метамодели метаданных: от классов конфигурации по ссылкам между схемами.
 *
 * Метамодель EDT описывает и редактор, и отладчик, и формы - в jar нужна только
 * та её часть, без которой не прочитать метаданные.
 */
fun edtMetadataSchemas(ecoreDir: File): List<File> {
    val files = ecoreDir.listFiles { file: File -> file.name.endsWith(".ecore") }?.toList() ?: emptyList()
    val byName = files.associateBy { it.name }
    val byNamespace = mutableMapOf<String, File>()
    val texts = files.associateWith { it.readText() }
    texts.forEach { (file, text) ->
        Regex("nsURI=\"([^\"]+)\"").findAll(text).forEach { match ->
            byNamespace.putIfAbsent(match.groupValues[1], file)
        }
    }

    val roots = listOf(
        "g5.1c.ru.v8.dt.metadata.mdclass.ecore",
        "g5.1c.ru.v8.dt.metadata.mdclass.extension.ecore",
        // Управляемая форма: по ней панель знает свойства элементов
        "g5.1c.ru.v8.dt.form.ecore",
        // Поставка: режимы поддержки объектов в Configuration.distr
        "g5.1c.ru.v8.dt.distribution.model.ecore",
    )
    val chosen = linkedSetOf<File>()
    val queue = ArrayDeque(roots.mapNotNull { byName[it] })
    while (queue.isNotEmpty()) {
        val file = queue.removeFirst()
        if (!chosen.add(file)) {
            continue
        }
        // Схемы ссылаются друг на друга и по пространству имён, и по имени файла
        Regex("href=\"([^\"#]+)#").findAll(texts.getValue(file)).forEach { match ->
            val target = match.groupValues[1]
            val dependency = byNamespace[target] ?: byName[target]
            if (dependency != null && dependency !in chosen) {
                queue.addLast(dependency)
            }
        }
    }
    return chosen.toList()
}

val prepareEdtSchemas = tasks.register("prepareEdtSchemas") {
    val version = latestEdtVersion()
    val ecoreDir = version?.let { edtSchemasDir.dir("$it/ecore").asFile }
    val target = layout.buildDirectory.dir("generated/edt-schemas")
    inputs.dir(edtSchemasDir).withPropertyName("схемы EDT")
    outputs.dir(target)
    doLast {
        val output = target.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        if (ecoreDir == null || !ecoreDir.isDirectory) {
            throw GradleException("Схемы EDT не найдены: $edtSchemasDir. Обновите submodule namespace-forest.")
        }
        val schemas = edtMetadataSchemas(ecoreDir)
        if (schemas.isEmpty()) {
            throw GradleException("В $ecoreDir нет схем метаданных EDT.")
        }
        schemas.forEach { it.copyTo(output.resolve(it.name), overwrite = true) }
        // Каталог ресурсов из jar не перечислить: список файлов и версия лежат рядом
        output.resolve("index.txt").writeText(schemas.joinToString("\n") { it.name } + "\n")
        output.resolve("version.txt").writeText("$version\n")
    }
}

// Пространства имён 1С → имя файла xsd и сегмент Java-пакета.
// Состав каталога версии может отличаться: берём только те схемы, что там лежат.
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
    Ns("http://v8.1c.ru/8.3/xcf/logform", "v8.1c.ru-8.3-xcf-logform.xsd", "v8_3_xcf_logform"),
    Ns("http://v8.1c.ru/8.2/managed-application/dynamic-list-data", "v8.1c.ru-8.2-managed-application-dynamic-list-data.xsd", "v8_2_managed_application_dynamic_list_data"),
    Ns("http://v8.1c.ru/8.3/data/pdf", "v8.1c.ru-8.3-data-pdf.xsd", "v8_3_data_pdf"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/core", "v8.1c.ru-8.1-data-composition-system-core.xsd", "v8_1_dcs_core"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/common", "v8.1c.ru-8.1-data-composition-system-common.xsd", "v8_1_dcs_common"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/details", "v8.1c.ru-8.1-data-composition-system-details.xsd", "v8_1_dcs_details"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/schema", "v8.1c.ru-8.1-data-composition-system-schema.xsd", "v8_1_dcs_schema"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/settings", "v8.1c.ru-8.1-data-composition-system-settings.xsd", "v8_1_dcs_settings"),
    Ns("http://v8.1c.ru/8.1/data-composition-system/area-template", "v8.1c.ru-8.1-data-composition-system-area-template.xsd", "v8_1_dcs_area_template"),
)

val mdClassesFile = "v8.1c.ru-8.3-MDClasses.xsd"
val logFormFile = "v8.1c.ru-8.3-xcf-logform.xsd"

// Корни генерации: MDClasses (объекты метаданных) и logform (содержимое управляемой формы).
// Второго корня в старых каталогах ещё нет, поэтому берём только существующие.
val rootSchemaFiles = listOf(mdClassesFile, logFormFile)

fun namespacesIn(dir: File): List<Ns> = schemaNamespaces.filter { File(dir, it.file).isFile }

fun rootsIn(dir: File): List<String> = rootSchemaFiles.filter { File(dir, it).isFile }

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

fun bindingsXml(modelId: String, namespaces: List<Ns>): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
    sb.append("<jaxb:bindings version=\"3.0\" xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n")
    for (ns in namespaces) {
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

// Схема формы объявляет состав элементов как xs:choice, хотя платформа кладёт в ChildItems
// элементы разных типов вперемешку и их порядок - это порядок на форме. Без этой привязки XJC
// раскладывает элементы по спискам на каждый тип и порядок теряется.
fun formBindingsXml(absSchemaDir: File): String {
    val schemaUri = File(absSchemaDir, logFormFile).toURI().toString()
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <jaxb:bindings version="3.0" xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <jaxb:bindings schemaLocation="$schemaUri">
            <jaxb:bindings node="//xs:complexType[@name='ChildItems']/xs:choice">
              <jaxb:property name="items"/>
            </jaxb:bindings>
            <jaxb:bindings node="//xs:complexType[@name='FormAttributeColumns']/xs:choice">
              <jaxb:property name="columns"/>
            </jaxb:bindings>
            <jaxb:bindings node="//xs:complexType[@name='Field']//xs:element[@name='DisplayImportance']">
              <jaxb:property name="fieldDisplayImportance"/>
            </jaxb:bindings>
          </jaxb:bindings>
        </jaxb:bindings>
    """.trimIndent()
}

fun catalogXml(absSchemaDir: File, namespaces: List<Ns>, roots: List<String>): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n")
    for (ns in namespaces) {
        if (ns.file in roots) continue
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
    xjc("org.glassfish.jaxb:jaxb-runtime:4.0.6")

    api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.fasterxml.woodstox:woodstox-core:7.1.0")

    // Модель формата EDT читается динамически по .ecore из хранилища схем:
    // генерировать классы не нужно, метамодель приходит вместе со схемами
    implementation("org.eclipse.emf:org.eclipse.emf.ecore:2.35.0")
    implementation("org.eclipse.emf:org.eclipse.emf.ecore.xmi:2.36.0")
    implementation("org.eclipse.emf:org.eclipse.emf.common:2.29.0")

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
            val namespaces = namespacesIn(xsdDir.asFile)
            val roots = rootsIn(xsdDir.asFile)
            val bindingsFile = File(xjbDir, "bindings.xjb")
            val catalogFile = File(xjbDir, "catalog.xml")
            bindingsFile.writeText(bindingsXml(modelId, namespaces))
            catalogFile.writeText(catalogXml(xsdDir.asFile, namespaces, roots))
            args(
                "-extension",
                "-catalog", catalogFile.absolutePath,
                "-b", bindingsFile.absolutePath,
                "-d", out.absolutePath,
            )
            if (logFormFile in roots) {
                val formBindingsFile = File(xjbDir, "form-bindings.xjb")
                formBindingsFile.writeText(formBindingsXml(xsdDir.asFile))
                args("-b", formBindingsFile.absolutePath)
            }
            args(roots)
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
/**
 * Схемы выгрузки конфигуратора самой свежей версии: по ним имя типа из файла EDT
 * получает пространство имён записи конфигуратора.
 */
val prepareDesignerTypeSchemas = tasks.register("prepareDesignerTypeSchemas") {
    val version = discoverVersions().lastOrNull()
    val target = layout.buildDirectory.dir("generated/designer-schemas")
    inputs.dir(schemasDir).withPropertyName("схемы конфигуратора")
    outputs.dir(target)
    doLast {
        val output = target.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        if (version == null) {
            throw GradleException("Схемы конфигуратора не найдены: $schemasDir. Обновите submodule namespace-forest.")
        }
        val schemas = schemasDir.dir(version).asFile
            .listFiles { file: File -> file.isFile && file.name.endsWith(".xsd") }
            ?.sortedBy { it.name }
            ?: emptyList()
        schemas.forEach { it.copyTo(output.resolve(it.name), overwrite = true) }
        output.resolve("index.txt").writeText(schemas.joinToString("\n") { it.name } + "\n")
    }
}

tasks.named<Copy>("processResources") {
    // Метамодель EDT: edt-schemas/<файлы схем>
    from(prepareEdtSchemas) {
        into("edt-schemas")
    }
    // Схемы конфигуратора: designer-schemas/<файлы схем>
    from(prepareDesignerTypeSchemas) {
        into("designer-schemas")
    }
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
    // Пустая управляемая форма платформы: golden-form/<формат>/{Форма.xml, Ext.xml}
    from("fixtures/samples-1c-platform/snapshots") {
        include("*/external-files/empty-full-objects/ВнешнийОтчет1/ВнешнийОтчет1/Forms/Форма.xml")
        includeEmptyDirs = false
        eachFile {
            val segs = relativePath.segments
            relativePath = RelativePath(true, "golden-form", segs[0], "Форма.xml")
        }
    }
    from("fixtures/samples-1c-platform/snapshots") {
        include("*/external-files/empty-full-objects/ВнешнийОтчет1/ВнешнийОтчет1/Forms/Форма/Ext/Form.xml")
        includeEmptyDirs = false
        eachFile {
            val segs = relativePath.segments
            relativePath = RelativePath(true, "golden-form", segs[0], "Ext.xml")
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
    // Та же конфигурация в формате EDT: сверка поведения на двух раскладках
    systemProperty(
        "fixtures.ssl31edt.root",
        layout.projectDirectory.dir("fixtures/ssl31-edt").asFile.absolutePath,
    )
    systemProperty(
        "samples.root",
        layout.projectDirectory.dir("fixtures/samples-1c-platform").asFile.absolutePath,
    )
    // Расширение чужого формата рядом с читаемыми: только Configuration.xml
    systemProperty(
        "fixtures.unsupportedExtension.root",
        layout.projectDirectory.dir("fixtures/unsupported-extension").asFile.absolutePath,
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
