/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Собирает {@link ProjectMetadataTreeDto} по каталогу проекта без {@code ConfigDumpInfo.xml}.
 */
public final class ProjectMetadataTreeBuilder {

  private static final String MAIN_LABEL = "Основная конфигурация";

  private ProjectMetadataTreeBuilder() {
  }

  /**
   * @param projectRoot корень проекта (где лежат {@code src/cf}, при наличии — {@code src/cfe}, {@code src/epf}, {@code src/erf})
   */
  public static ProjectMetadataTreeDto build(Path projectRoot) throws IOException {
    Path normalized = projectRoot.toAbsolutePath().normalize();
    Path mainCf = normalized.resolve("src").resolve("cf");
    Path mainCfg = mainCf.resolve(CfLayout.CONFIGURATION_XML);
    if (!Files.isRegularFile(mainCfg)) {
      throw new IOException("Не найден файл основной выгрузки: " + mainCfg);
    }
    String ver = MetaDataObjectHeadReader.readMetaDataObjectVersion(mainCfg);
    SupportedSchemaVersions.requireSupported(ver);
    String verFlag = MetaDataObjectHeadReader.toSchemaVersionFlag(ver);
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources = new ArrayList<>();
    sources.add(buildMainSource(normalized, mainCf, mainCfg));
    Path cfeRoot = normalized.resolve("src").resolve("cfe");
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
    appendExternalArtifactSources(normalized, sources);
    return new ProjectMetadataTreeDto(normalized.toString(), ver, verFlag, sources);
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildMainSource(
    Path projectRoot,
    Path cfRoot,
    Path configurationXml
  ) throws IOException {
    List<ChildObjectEntry> entries = loadChildObjects(configurationXml);
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads =
      MetadataTreeTagGroups.buildGroups(entries);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups =
      mapGroups(projectRoot, cfRoot, payloads);
    String cfgRel = projectRoot.relativize(configurationXml).toString().replace('\\', '/');
    String rootRel = projectRoot.relativize(cfRoot).toString().replace('\\', '/');
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "main",
      "main",
      MAIN_LABEL,
      cfgRel,
      rootRel,
      groups
    );
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExtensionSource(
    Path projectRoot,
    Path extensionRoot,
    Path configurationXml
  ) throws IOException {
    String id = extensionRoot.getFileName().toString();
    String label;
    try {
      label = ConfigurationObjectNameReader.readName(configurationXml);
    } catch (XMLStreamException e) {
      throw new IOException("Не удалось прочитать имя выгрузки расширения.", e);
    }
    if (label.isEmpty()) {
      label = id;
    }
    List<ChildObjectEntry> entries = loadChildObjects(configurationXml);
    List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads =
      MetadataTreeTagGroups.buildGroups(entries);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups =
      mapGroups(projectRoot, extensionRoot, payloads);
    String cfgRel = projectRoot.relativize(configurationXml).toString().replace('\\', '/');
    String rootRel = projectRoot.relativize(extensionRoot).toString().replace('\\', '/');
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "extension",
      id,
      label,
      cfgRel,
      rootRel,
      groups
    );
  }

  private static List<ChildObjectEntry> loadChildObjects(Path configurationXml) throws IOException {
    String ver = MetaDataObjectHeadReader.readMetaDataObjectVersion(configurationXml);
    SchemaVersion sv = SupportedSchemaVersions.requireSupported(ver);
    try {
      return ConfigurationChildObjectsExtractor.readChildObjects(configurationXml, sv);
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
    List<ProjectMetadataTreeDto.MetadataGroupDto> out = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeGroupPayload p : payloads) {
      List<ProjectMetadataTreeDto.MetadataSubgroupDto> subgroups = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataSubgroupPayload sp : p.subgroups()) {
        List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
        for (MetadataTreeTagGroups.MetadataTreeItemPayload it : sp.items()) {
          String rel = relativePathForItem(projectRoot, metadataRoot, it.objectType(), it.name());
          items.add(new ProjectMetadataTreeDto.MetadataItemDto(it.objectType(), it.name(), rel));
        }
        subgroups.add(new ProjectMetadataTreeDto.MetadataSubgroupDto(sp.id(), sp.label(), sp.iconHint(), items));
      }
      List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataTreeItemPayload it : p.items()) {
        String rel = relativePathForItem(projectRoot, metadataRoot, it.objectType(), it.name());
        items.add(new ProjectMetadataTreeDto.MetadataItemDto(it.objectType(), it.name(), rel));
      }
      out.add(new ProjectMetadataTreeDto.MetadataGroupDto(p.id(), p.label(), p.iconHint(), items, subgroups));
    }
    return out;
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
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources
  ) throws IOException {
    List<ExternalArtifactLister.ExternalArtifactEntry> erf = ExternalArtifactLister.listErf(projectRoot);
    if (!erf.isEmpty()) {
      sources.add(buildExternalErfSource(erf));
    }
    List<ExternalArtifactLister.ExternalArtifactEntry> epf = ExternalArtifactLister.listEpf(projectRoot);
    if (!epf.isEmpty()) {
      sources.add(buildExternalEpfSource(epf));
    }
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExternalErfSource(
    List<ExternalArtifactLister.ExternalArtifactEntry> entries
  ) {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (ExternalArtifactLister.ExternalArtifactEntry e : entries) {
      items.add(new ProjectMetadataTreeDto.MetadataItemDto("ExternalReport", e.name(), e.relativePath()));
    }
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = List.of(
      new ProjectMetadataTreeDto.MetadataGroupDto("content", "", "report", items, List.of())
    );
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "externalErf",
      "external-erf",
      "Внешние отчёты",
      "",
      "src/erf",
      groups
    );
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto buildExternalEpfSource(
    List<ExternalArtifactLister.ExternalArtifactEntry> entries
  ) {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (ExternalArtifactLister.ExternalArtifactEntry e : entries) {
      items.add(new ProjectMetadataTreeDto.MetadataItemDto("ExternalDataProcessor", e.name(), e.relativePath()));
    }
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = List.of(
      new ProjectMetadataTreeDto.MetadataGroupDto("content", "", "run-below", items, List.of())
    );
    return new ProjectMetadataTreeDto.MetadataSourceDto(
      "externalEpf",
      "external-epf",
      "Внешние обработки",
      "",
      "src/epf",
      groups
    );
  }
}
