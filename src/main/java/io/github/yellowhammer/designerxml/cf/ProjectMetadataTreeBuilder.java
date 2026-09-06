/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtProjectMetadataTree;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Собирает {@link ProjectMetadataTreeDto} по каталогу проекта без {@code ConfigDumpInfo.xml}.
 */
public final class ProjectMetadataTreeBuilder {

  private static final String MAIN_LABEL = "Основная конфигурация";

  private ProjectMetadataTreeBuilder() {
  }

  /**
   * @param projectRoot корень проекта со стандартной раскладкой ({@code src/cf}, {@code src/cfe}, {@code src/epf}, {@code src/erf})
   */
  public static ProjectMetadataTreeDto build(Path projectRoot) throws IOException {
    return build(projectRoot, ProjectSourceDirs.DEFAULTS);
  }

  /**
   * @param projectRoot корень проекта
   * @param dirs каталоги исходников (настраиваемые; относительные — от корня проекта)
   */
  public static ProjectMetadataTreeDto build(Path projectRoot, ProjectSourceDirs dirs) throws IOException {
    Path normalized = projectRoot.toAbsolutePath().normalize();
    Path mainCf = dirs.cfPath(normalized);
    Path mainCfg = mainCf.resolve(CfLayout.CONFIGURATION_XML);
    if (!Files.isRegularFile(mainCfg)) {
      // Формат исходников виден по самим файлам: спрашивать его у клиента
      // значило бы просить IDE знать про раскладки обоих форматов
      if (!EdtLayout.projects(normalized).isEmpty()) {
        return EdtProjectMetadataTree.build(normalized);
      }
      throw new IOException("Не найдены исходники конфигурации: ни выгрузка конфигуратора " + mainCfg
        + ", ни проект 1С:EDT в " + normalized);
    }
    String ver = MetaDataObjectHeadReader.readMetaDataObjectVersion(mainCfg);
    SchemaVersion mainSchema = SupportedSchemaVersions.requireSupported(ver);
    String verFlag = MetaDataObjectHeadReader.toSchemaVersionFlag(ver);
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources = new ArrayList<>();
    sources.add(buildMainSource(normalized, mainCf, mainCfg, ver, mainSchema));
    Path cfeRoot = dirs.cfePath(normalized);
    if (Files.isDirectory(cfeRoot)) {
      try (var stream = Files.list(cfeRoot)) {
        List<Path> extDirs = stream.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        for (Path extDir : extDirs) {
          Path extCfg = extDir.resolve(CfLayout.CONFIGURATION_XML);
          if (Files.isRegularFile(extCfg)) {
            sources.add(buildExtensionSource(normalized, extDir, extCfg));
          }
        }
      }
    }
    appendExternalArtifactSources(normalized, dirs, sources);
    return new ProjectMetadataTreeDto(normalized.toString(), ver, verFlag, sources);
  }

  /** Относительный путь от корня проекта; вне корня — абсолютный (для DTO). */
  private static String relativeOrAbsolute(Path projectRoot, Path target) {
    Path normalized = target.toAbsolutePath().normalize();
    if (normalized.startsWith(projectRoot)) {
      return projectRoot.relativize(normalized).toString().replace('\\', '/');
    }
    return normalized.toString().replace('\\', '/');
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildMainSource(
    Path projectRoot,
    Path cfRoot,
    Path configurationXml,
    String schemaVersion,
    SchemaVersion schema
  ) throws IOException {
    List<ChildObjectEntry> entries = loadChildObjects(configurationXml, schema);
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads =
      MetadataTreeTagGroups.buildGroups(entries);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups =
      mapGroups(projectRoot, cfRoot, payloads);
    String cfgRel = projectRoot.relativize(configurationXml).toString().replace('\\', '/');
    String rootRel = projectRoot.relativize(cfRoot).toString().replace('\\', '/');
    // Пока поддержку не учитывают, дерево о ней и не рассказывает. Состояние
    // корня - его собственное правило: возможность изменения отдельно от него
    String support = null;
    String supportGeneration = null;
    boolean supportEditingEnabled = false;
    try {
      SupportRules.Rules rules = SupportRules.isEnforced() ? SupportRules.read(cfRoot) : null;
      if (rules != null && !rules.isEmpty()) {
        support = SupportRules.objectState(configurationXml);
        supportEditingEnabled = rules.editingEnabled();
        supportGeneration = rules.generationId;
      }
    } catch (IOException e) {
      support = null;
    }
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "main",
      "main",
      MAIN_LABEL,
      cfgRel,
      rootRel,
      schemaVersion,
      true,
      support,
      supportEditingEnabled,
      supportGeneration,
      groups
    );
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExtensionSource(
    Path projectRoot,
    Path extensionRoot,
    Path configurationXml
  ) throws IOException {
    String id = extensionRoot.getFileName().toString();
    String cfgRel = projectRoot.relativize(configurationXml).toString().replace('\\', '/');
    String rootRel = projectRoot.relativize(extensionRoot).toString().replace('\\', '/');
    String schemaVersion = MetaDataObjectHeadReader.readMetaDataObjectVersion(configurationXml);
    Optional<SchemaVersion> schema = SchemaVersion.byVersionAttribute(schemaVersion);
    if (schema.isEmpty()) {
      // Формат выгрузки не читается: источник без состава
      return new ProjectMetadataTreeDto.MetadataSourceDto(
        "extension",
        id,
        extensionLabel(configurationXml, id, false),
        cfgRel,
        rootRel,
        schemaVersion,
        false,
        null,
        false,
        null,
        List.of()
      );
    }
    List<ChildObjectEntry> entries = loadChildObjects(configurationXml, schema.get());
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads =
      MetadataTreeTagGroups.buildGroups(entries);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups =
      mapGroups(projectRoot, extensionRoot, payloads, true, null);
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "extension",
      id,
      extensionLabel(configurationXml, id, true),
      cfgRel,
      rootRel,
      schemaVersion,
      true,
      null,
      false,
      null,
      groups
    );
  }

  /**
   * Имя расширения из {@code Configuration/Properties/Name}; без имени - каталог.
   *
   * @param strict нечитаемый файл - ошибка; иначе именем остаётся каталог
   */
  private static String extensionLabel(Path configurationXml, String id, boolean strict) throws IOException {
    String label;
    try {
      label = ConfigurationObjectNameReader.readName(configurationXml);
    } catch (XMLStreamException e) {
      if (strict) {
        throw new IOException("Не удалось прочитать имя выгрузки расширения.", e);
      }
      label = "";
    }
    return label.isEmpty() ? id : label;
  }

  /** Принадлежность объекта; пусто у основной конфигурации и у объектов без своего файла. */
  private static String belonging(Path projectRoot, String relativePath, boolean readBelonging) {
    if (!readBelonging || relativePath == null || relativePath.isEmpty()) {
      return null;
    }
    return ObjectBelongingReader.read(projectRoot.resolve(relativePath));
  }

  private static List<ChildObjectEntry> loadChildObjects(Path configurationXml, SchemaVersion schema)
    throws IOException {
    try {
      return ConfigurationChildObjectsExtractor.readChildObjects(configurationXml, schema);
    } catch (JAXBException e) {
      throw new IOException(
        "Не удалось разобрать Configuration.xml для дерева метаданных. Проверьте формат выгрузки.",
        e);
    }
  }

  private static List<ProjectMetadataTreeDto.MetadataGroupDto> mapGroups(
    Path projectRoot,
    Path metadataRoot,
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads
  ) {
    SupportRules.Rules supportRules;
    try {
      supportRules = SupportRules.isEnforced() ? SupportRules.read(metadataRoot) : null;
    } catch (IOException e) {
      supportRules = null;
    }
    return mapGroups(projectRoot, metadataRoot, payloads, false, supportRules);
  }

  /**
   * @param readBelonging Читать принадлежность объектов: она бывает только у расширений, а чтение
   *   файла на каждый объект основной конфигурации ничего бы не дало.
   */
  private static List<ProjectMetadataTreeDto.MetadataGroupDto> mapGroups(
    Path projectRoot,
    Path metadataRoot,
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads,
    boolean readBelonging,
    SupportRules.Rules supportRules
  ) {
    List<ProjectMetadataTreeDto.MetadataGroupDto> out = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeGroupPayload p : payloads) {
      List<ProjectMetadataTreeDto.MetadataSubgroupDto> subgroups = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataSubgroupPayload sp : p.subgroups()) {
        List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
        for (MetadataTreeTagGroups.MetadataTreeItemPayload it : sp.items()) {
          String rel = relativePathForItem(projectRoot, metadataRoot, it.objectType(), it.name());
          items.add(itemDto(projectRoot, it.objectType(), it.name(), rel, readBelonging, supportRules));
        }
        subgroups.add(new ProjectMetadataTreeDto.MetadataSubgroupDto(sp.id(), sp.label(), sp.iconHint(), items));
      }
      List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataTreeItemPayload it : p.items()) {
        String rel = relativePathForItem(projectRoot, metadataRoot, it.objectType(), it.name());
        items.add(itemDto(projectRoot, it.objectType(), it.name(), rel, readBelonging, supportRules));
      }
      out.add(new ProjectMetadataTreeDto.MetadataGroupDto(p.id(), p.label(), p.iconHint(), items, subgroups));
    }
    return out;
  }

  private static ProjectMetadataTreeDto.MetadataItemDto itemDto(
    Path projectRoot,
    String objectType,
    String name,
    String relativePath,
    boolean readBelonging,
    SupportRules.Rules supportRules
  ) {
    return new ProjectMetadataTreeDto.MetadataItemDto(
      objectType,
      name,
      relativePath,
      belonging(projectRoot, relativePath, readBelonging),
      supportState(projectRoot, relativePath, supportRules),
      MdObjectOpen.resolve(objectType, projectRoot, relativePath));
  }

  /** Режим поддержки объекта по правилам поставщика; пусто без правил или записи. */
  private static String supportState(Path projectRoot, String relativePath, SupportRules.Rules rules) {
    if (rules == null || rules.isEmpty() || relativePath == null || relativePath.isEmpty()) {
      return null;
    }
    return rules.effectiveState(ObjectBelongingReader.readRootUuid(projectRoot.resolve(relativePath)));
  }

  private static String relativePathForItem(
    Path projectRoot,
    Path metadataRoot,
    String objectType,
    String name
  ) {
    try {
      return CfObjectPathResolver.objectXml(metadataRoot, objectType, name)
        .map(p -> projectRoot.relativize(p).toString().replace('\\', '/'))
        .orElse("");
    } catch (IOException e) {
      return "";
    }
  }

  private static void appendExternalArtifactSources(
    Path projectRoot,
    ProjectSourceDirs dirs,
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources
  ) throws IOException {
    List<ExternalArtifactLister.ExternalArtifactEntry> erf =
      ExternalArtifactLister.listArtifacts(projectRoot, dirs.erfPath(projectRoot));
    if (!erf.isEmpty()) {
      sources.add(buildExternalErfSource(erf, relativeOrAbsolute(projectRoot, dirs.erfPath(projectRoot))));
    }
    List<ExternalArtifactLister.ExternalArtifactEntry> epf =
      ExternalArtifactLister.listArtifacts(projectRoot, dirs.epfPath(projectRoot));
    if (!epf.isEmpty()) {
      sources.add(buildExternalEpfSource(epf, relativeOrAbsolute(projectRoot, dirs.epfPath(projectRoot))));
    }
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExternalErfSource(
    List<ExternalArtifactLister.ExternalArtifactEntry> entries,
    String rootRelativePath
  ) {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (ExternalArtifactLister.ExternalArtifactEntry e : entries) {
      items.add(new ProjectMetadataTreeDto.MetadataItemDto("ExternalReport", e.name(), e.relativePath(), null, null, null));
    }
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = List.of(
      new ProjectMetadataTreeDto.MetadataGroupDto("content", "", "report", items, List.of())
    );
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "externalErf",
      "external-erf",
      "Внешние отчёты",
      "",
      rootRelativePath,
      "",
      true,
      null,
      false,
      null,
      groups
    );
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExternalEpfSource(
    List<ExternalArtifactLister.ExternalArtifactEntry> entries,
    String rootRelativePath
  ) {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (ExternalArtifactLister.ExternalArtifactEntry e : entries) {
      items.add(new ProjectMetadataTreeDto.MetadataItemDto(
        "ExternalDataProcessor", e.name(), e.relativePath(), null, null, null));
    }
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = List.of(
      new ProjectMetadataTreeDto.MetadataGroupDto("content", "", "run-below", items, List.of())
    );
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "externalEpf",
      "external-epf",
      "Внешние обработки",
      "",
      rootRelativePath,
      "",
      true,
      null,
      false,
      null,
      groups
    );
  }
}
