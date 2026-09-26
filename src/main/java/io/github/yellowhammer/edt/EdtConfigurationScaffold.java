/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.ctc.wstx.stax.WstxInputFactory;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.CfLayout;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.GoldenScaffold;

/**
 * Новый проект конфигурации 1С:EDT.
 *
 * Заготовкой служит проект, который сама 1С:EDT записала при импорте эталона
 * голых объектов платформы; состав конфигурации в нём обрезан до языка. У
 * заготовки меняются имена проекта и конфигурации и идентификаторы, а от
 * формата выгрузки зависят версия платформы в манифесте, набор свойств и
 * значения новой конфигурации платформы: режимы совместимости и назначение.
 */
public final class EdtConfigurationScaffold {

  /** Эталон: проект «ЭталонСемя», конфигурация в нём называется так же. */
  private static final String GOLDEN = "Configuration/ЭталонСемя/";
  private static final String PROTO_NAME = "ЭталонСемя";
  /** Свойства, которые формат пишет сверх эталона: {@code rules/<формат>/<свойство>.xml}. */
  private static final String RULES = "Configuration/rules/";
  private static final String MANIFEST = "DT-INF/PROJECT.PMF";
  private static final String CONFIGURATION = EdtLayout.SOURCE_DIR + "/" + EdtLayout.CONFIGURATION_MDO;
  private static final List<String> FILES = List.of(
      EdtLayout.PROJECT_FILE,
      ".settings/org.eclipse.core.resources.prefs",
      MANIFEST,
      CONFIGURATION);

  private static final Pattern RUNTIME_VERSION = Pattern.compile("^Runtime-Version:[ \\t]*(\\S+)", Pattern.MULTILINE);

  /** Символы, недопустимые в имени файла, а значит и в имени проекта. */
  private static final Pattern PROJECT_NAME_FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

  /**
   * Назначение новой конфигурации: платформа пишет {@code PlatformApplication},
   * у 1С:EDT то же значение называется {@code PersonalComputer}.
   */
  private static final String PERSONAL_COMPUTER = "PersonalComputer";

  /** Свойства новой конфигурации конфигуратора по форматам. */
  private static final Map<SchemaVersion, Set<String>> DESIGNER_PROPERTIES = new ConcurrentHashMap<>();

  private EdtConfigurationScaffold() {
  }

  /**
   * Создаёт проект с пустой конфигурацией.
   *
   * @param projectDir каталог проекта; его ещё нет либо он пуст
   * @param projectName имя проекта; пустое - имя каталога
   * @param name имя конфигурации
   * @param synonym синоним; пустой не записывается
   * @param vendor поставщик; пустой не записывается
   * @param appVersion версия конфигурации; пустая не записывается
   * @param version формат выгрузки, для платформы которого создаётся проект
   * @param model метамодель EDT
   * @return описание конфигурации {@code src/Configuration/Configuration.mdo}
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static Path create(Path projectDir, String projectName, String name, String synonym, String vendor,
      String appVersion, SchemaVersion version, EdtModel model) throws IOException {
    CatalogNameConstraints.check(name);
    Path directory = projectDir.toAbsolutePath().normalize();
    String project = projectName(directory, projectName);
    ensureEmpty(directory);
    SchemaVersion canonical = canonicalVersion();
    if (version.compareTo(canonical) > 0) {
      throw new IllegalArgumentException("Эталон проекта EDT снят для формата "
          + canonical.metadataObjectVersionAttribute() + ", формат "
          + version.metadataObjectVersionAttribute() + " новее.");
    }

    String golden = EdtObjectScaffold.golden(GOLDEN + CONFIGURATION);
    String seed = EdtObjectScaffold.seed("configuration|" + version.name() + "|" + project + "|" + name, golden);
    for (String file : FILES) {
      String text = EdtObjectScaffold.golden(GOLDEN + file);
      if (file.equals(EdtLayout.PROJECT_FILE)) {
        text = EdtObjectScaffold.renamed(text, PROTO_NAME, project);
      } else if (file.equals(MANIFEST)) {
        text = RUNTIME_VERSION.matcher(text)
            .replaceFirst(Matcher.quoteReplacement("Runtime-Version: " + version.platformLine()));
      } else if (file.equals(CONFIGURATION)) {
        text = EdtObjectScaffold.freshUuids(forFormat(text, version, canonical, model), seed);
      }
      Path target = directory.resolve(file);
      Files.createDirectories(target.getParent());
      Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    Path configuration = directory.resolve(CONFIGURATION);
    EdtConfigurationProperties.write(
        configuration, newConfiguration(name, synonym, vendor, appVersion, version, model), model);
    return configuration;
  }

  /**
   * Свойства новой конфигурации платформы.
   *
   * Платформа ставит новой конфигурации режимы совместимости своей версии и
   * назначение для персонального компьютера; в эталоне стоят значения семени.
   * Пишет их общая точечная запись: литералы берутся из схемы, а значение,
   * совпавшее с умолчанием схемы, в файл не попадает. Так же пишет 1С:EDT:
   * режима совместимости расширений 8.5.1 в описании конфигурации нет, а режим
   * совместимости есть всегда - в эталоне он уже записан, и меняется только
   * значение.
   */
  private static ConfigurationPropertiesDto newConfiguration(String name, String synonym, String vendor,
      String appVersion, SchemaVersion version, EdtModel model) {
    EClass configuration = model.classOf("Configuration");
    ConfigurationPropertiesDto dto = new ConfigurationPropertiesDto();
    dto.name = name;
    dto.compatibilityMode = constant(configuration, "compatibilityMode", version.platformLine());
    dto.configurationExtensionCompatibilityMode =
        constant(configuration, "configurationExtensionCompatibilityMode", version.platformLine());
    dto.usePurposes = List.of(constant(configuration, "usePurposes", PERSONAL_COMPUTER));
    dto.synonym = blankToNull(synonym);
    dto.vendor = blankToNull(vendor);
    dto.version = blankToNull(appVersion);
    return dto;
  }

  /** Значение перечисления в написании контракта по литералу схемы. */
  private static String constant(EClass eClass, String feature, String literal) {
    EStructuralFeature attribute = eClass == null ? null : eClass.getEStructuralFeature(feature);
    if (attribute != null && attribute.getEType() instanceof EEnum type) {
      for (EEnumLiteral candidate : type.getELiterals()) {
        if (candidate.getLiteral().equals(literal)) {
          return EdtPropertyValues.constantName(candidate.getName());
        }
      }
    }
    throw new IllegalArgumentException("Схема EDT не знает значение " + literal + " свойства " + feature);
  }

  /**
   * Описание конфигурации формата.
   *
   * 1С:EDT при импорте выгрузки заполняет только те свойства, что в ней есть:
   * свойство, которого формат не пишет, получает значение по умолчанию и в
   * описание не попадает. Какие свойства пишет формат, видно по новой
   * конфигурации конфигуратора этого формата; свойство EDT называется так же,
   * со строчной буквы. Свойства, которые формат пишет по-своему, а в эталоне
   * их нет, лежат в сборке фрагментами, записанными EDT, и встают по порядку
   * схемы.
   *
   * @param golden описание конфигурации из эталона
   * @param version формат результата
   * @param canonical формат выгрузки, из которой EDT записала эталон
   * @param model метамодель EDT
   * @return описание конфигурации формата
   * @throws IOException если эталон не разбирается
   */
  static String forFormat(String golden, SchemaVersion version, SchemaVersion canonical, EdtModel model)
      throws IOException {
    Set<String> written = designerProperties(version);
    Set<String> canonicalWritten = designerProperties(canonical);
    String text = golden;
    try {
      for (String feature : new LinkedHashSet<>(EdtObjectRegions.propertyNames(text))) {
        String designer = Character.toUpperCase(feature.charAt(0)) + feature.substring(1);
        if (canonicalWritten.contains(designer) && !written.contains(designer)) {
          text = without(text, feature);
        }
      }
      List<String> order = order(model.classOf("Configuration"));
      for (String feature : order) {
        String rule = RULES + version.metadataObjectVersionAttribute() + "/" + feature + ".xml";
        if (EdtObjectScaffold.class.getResource("/edt-golden/" + rule) != null) {
          text = without(text, feature);
          int at = EdtObjectRegions.insertionPoint(text, order, feature);
          text = text.substring(0, at) + EdtObjectScaffold.fragment(rule, eol(text)) + text.substring(at);
        }
      }
    } catch (XMLStreamException error) {
      throw new IOException("Эталон конфигурации EDT не разбирается", error);
    }
    return text;
  }

  /** Текст без свойства: строки его элементов уходят целиком. */
  private static String without(String xml, String feature) throws XMLStreamException {
    List<EdtObjectRegions.Region> regions = EdtObjectRegions.properties(xml, feature);
    String text = xml;
    for (int index = regions.size() - 1; index >= 0; index--) {
      EdtObjectRegions.Region region = regions.get(index);
      int start = EdtObjectRegions.lineStart(text, region.start());
      int end = text.indexOf('\n', region.end());
      text = text.substring(0, start) + text.substring(end < 0 ? text.length() : end + 1);
    }
    return text;
  }

  /**
   * Свойства новой конфигурации конфигуратора: элементы её {@code Properties}.
   *
   * Это та же конфигурация, что {@code init-empty-cf} пишет в выгрузку, и та
   * же, что создаёт платформа формата.
   */
  private static Set<String> designerProperties(SchemaVersion version) throws IOException {
    Set<String> known = DESIGNER_PROPERTIES.get(version);
    if (known != null) {
      return known;
    }
    String xml = GoldenScaffold.generateEmptyConfiguration(CfLayout.DEFAULT_CONFIGURATION_NAME, version);
    List<String> properties = List.of("MetaDataObject", "Configuration", "Properties");
    Set<String> names = new LinkedHashSet<>();
    try {
      XMLStreamReader reader = new WstxInputFactory().createXMLStreamReader(new StringReader(xml));
      try {
        List<String> path = new ArrayList<>();
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            if (path.equals(properties)) {
              names.add(reader.getLocalName());
            }
            path.add(reader.getLocalName());
          } else if (event == XMLStreamConstants.END_ELEMENT) {
            path.remove(path.size() - 1);
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException error) {
      throw new IOException("Пустая конфигурация формата " + version.metadataObjectVersionAttribute()
          + " не разбирается", error);
    }
    Set<String> result = Set.copyOf(names);
    DESIGNER_PROPERTIES.put(version, result);
    return result;
  }

  /** Формат выгрузки, которую импортировала EDT: по версии платформы в манифесте эталона. */
  static SchemaVersion canonicalVersion() throws IOException {
    Matcher runtime = RUNTIME_VERSION.matcher(EdtObjectScaffold.golden(GOLDEN + MANIFEST));
    if (runtime.find()) {
      for (SchemaVersion version : SchemaVersion.values()) {
        if (version.platformLine().equals(runtime.group(1))) {
          return version;
        }
      }
    }
    throw new IOException("У эталона проекта EDT нет версии платформы формата выгрузки");
  }

  /**
   * Имя проекта: заданное либо имя каталога.
   *
   * Проект 1С:EDT - проект Eclipse, и имя его подчиняется правилам имён файлов.
   */
  private static String projectName(Path directory, String projectName) {
    Path folder = directory.getFileName();
    String project = projectName == null || projectName.isBlank()
        ? (folder == null ? "" : folder.toString())
        : projectName.trim();
    if (project.isEmpty() || PROJECT_NAME_FORBIDDEN.matcher(project).find() || project.endsWith(".")) {
      throw new IllegalArgumentException("Недопустимое имя проекта EDT: «" + project + "»");
    }
    return project;
  }

  /** Каталог проекта: создать проект можно только там, где ещё ничего нет. */
  private static void ensureEmpty(Path projectDir) throws IOException {
    if (!Files.exists(projectDir)) {
      return;
    }
    if (!Files.isDirectory(projectDir)) {
      throw new IllegalArgumentException("На месте каталога проекта лежит файл: " + projectDir);
    }
    try (Stream<Path> children = Files.list(projectDir)) {
      if (children.findAny().isPresent()) {
        throw new IllegalArgumentException("Каталог уже есть и не пуст: " + projectDir);
      }
    }
  }

  /** Порядок свойств класса по схеме. */
  private static List<String> order(EClass eClass) {
    List<String> order = new ArrayList<>();
    if (eClass != null) {
      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        order.add(feature.getName());
      }
    }
    return order;
  }

  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
