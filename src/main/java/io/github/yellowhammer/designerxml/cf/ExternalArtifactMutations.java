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
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Переименование, копирование и удаление внешнего отчёта или обработки в выгрузке.
 *
 * <p>Внешний объект лежит своим каталогом: описание {@code <корень>/<Имя>/<Имя>.xml}, а модуль,
 * формы, макеты и справка - в каталоге содержимого рядом с описанием, названном так же, как
 * файл описания: {@code <корень>/<Имя>/<Имя>/Ext/ObjectModule.bsl}, {@code <корень>/<Имя>/<Имя>/Forms}.
 * Платформа ищет содержимое по имени файла описания, поэтому каталог содержимого получает новое
 * имя вместе с файлом.
 */
public final class ExternalArtifactMutations {

  /** Приставки ссылок объекта на себя по виду: путь к форме или макету и порождённые типы. */
  private static final Map<String, String> SELF_REFERENCE_PREFIXES = Map.of(
    ExternalArtifactKind.REPORT.name(),
    "ExternalReport|ExternalReportObject|ReportTabularSection|ReportTabularSectionRow",
    ExternalArtifactKind.DATA_PROCESSOR.name(),
    "ExternalDataProcessor|ExternalDataProcessorObject|DataProcessorTabularSection|DataProcessorTabularSectionRow");

  /** Описание объекта, формы или макета: корень {@code MetaDataObject}. */
  private static final Pattern METADATA_OBJECT_ROOT = Pattern.compile(
    "\\A[\\uFEFF\\s]*(?:<\\?[^>]*\\?>\\s*)?<(?:[\\w.-]+:)?MetaDataObject[\\s>]");

  /** Порождённый тип в описании: {@code <xr:GeneratedType name="..." category="...">}. */
  private static final Pattern GENERATED_TYPE = Pattern.compile(
    "<(?:[\\w.-]+:)?GeneratedType\\b[^>]*?\\bname=\"([^\"]+)\"[^>]*?\\bcategory=\"([^\"]+)\"");

  private ExternalArtifactMutations() {
  }

  /**
   * Переименовывает внешний объект: его каталог, файл описания, каталог содержимого и имя.
   *
   * @param objectXml описание объекта
   * @param version формат выгрузки
   * @param newName новое имя
   * @throws IOException если файлы не читаются или не пишутся
   * @throws JAXBException если описание не читается моделью формата
   */
  public static void rename(Path objectXml, SchemaVersion version, String newName) throws IOException, JAXBException {
    Objects.requireNonNull(objectXml, "objectXml");
    String targetName = requireName(newName);
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path srcDir = objectXml.getParent();
    if (srcDir == null) {
      throw new IllegalArgumentException("invalid object xml path: " + objectXml);
    }
    Path rootDir = srcDir.getParent();
    if (rootDir == null) {
      throw new IllegalArgumentException("invalid external artifact directory: " + srcDir);
    }
    String oldStem = stem(objectXml);
    Path dstDir = rootDir.resolve(targetName);
    // Имя файла описания может не совпадать ни с каталогом, ни с именем объекта (ssl31/src/erf,
    // выгрузка external-files/empty-full-objects): каждое меняется, только если отличается
    boolean moveDir = !dstDir.equals(srcDir);
    boolean moveFiles = !oldStem.equals(targetName);
    if (moveDir && Files.exists(dstDir)) {
      throw new IllegalArgumentException("target folder already exists: " + dstDir);
    }
    if (moveFiles) {
      requireFree(srcDir, targetName);
    }

    ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(objectXml, version);
    String kind = dto.kind;
    String oldName = dto.name;
    dto.name = targetName;
    ExternalArtifactPropertiesEdit.write(objectXml, version, dto);
    renameSelfReferences(objectXml, srcDir.resolve(oldStem), kind, oldName, targetName);

    if (moveFiles) {
      Files.move(objectXml, srcDir.resolve(targetName + ".xml"));
      renameContent(srcDir, oldStem, targetName);
    }
    if (moveDir) {
      Files.move(srcDir, dstDir);
    }
  }

  public static void delete(Path objectXml) throws IOException {
    Objects.requireNonNull(objectXml, "objectXml");
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path dir = objectXml.getParent();
    if (dir == null || !Files.isDirectory(dir)) {
      throw new IllegalArgumentException("invalid external artifact directory: " + objectXml);
    }
    deleteRecursively(dir);
  }

  /**
   * Копирует внешний объект в соседний каталог под новым именем.
   *
   * @param objectXml описание объекта
   * @param version формат выгрузки
   * @param newName имя копии
   * @return описание копии
   * @throws IOException если файлы не читаются или не пишутся
   * @throws JAXBException если описание не читается моделью формата
   */
  public static Path duplicate(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    Objects.requireNonNull(objectXml, "objectXml");
    String targetName = requireName(newName);
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path srcDir = objectXml.getParent();
    if (srcDir == null) {
      throw new IllegalArgumentException("invalid object xml path: " + objectXml);
    }
    Path rootDir = srcDir.getParent();
    if (rootDir == null) {
      throw new IllegalArgumentException("invalid external artifact directory: " + srcDir);
    }
    Path dstDir = rootDir.resolve(targetName);
    if (Files.exists(dstDir)) {
      throw new IllegalArgumentException("target folder already exists: " + dstDir);
    }
    String oldName = stem(objectXml);
    if (!oldName.equals(targetName)) {
      requireFree(srcDir, targetName);
    }
    String uuidSeed = GoldenUuid.from("duplicate|external|" + targetName,
      Files.readString(objectXml, StandardCharsets.UTF_8));
    copyRecursively(srcDir, dstDir);
    Path srcXmlInCopy = dstDir.resolve(oldName + ".xml");
    Path dstXml = dstDir.resolve(targetName + ".xml");
    if (!oldName.equals(targetName)) {
      Files.move(srcXmlInCopy, dstXml);
      renameContent(dstDir, oldName, targetName);
    }
    ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(dstXml, version);
    String kind = dto.kind;
    String sourceName = dto.name;
    dto.name = targetName;
    ExternalArtifactPropertiesEdit.write(dstXml, version, dto);
    renameSelfReferences(dstXml, dstDir.resolve(targetName), kind, sourceName, targetName);
    renameOwnTypes(dstXml, dstDir.resolve(targetName), kind, targetName);
    remapUuids(dstXml, dstDir.resolve(targetName), uuidSeed);
    return dstXml;
  }

  private static String requireName(String name) {
    String n = name == null ? "" : name.trim();
    if (n.isEmpty()) {
      throw new IllegalArgumentException("new name required");
    }
    CatalogNameConstraints.check(n);
    return n;
  }

  /**
   * Файл описания и каталог содержимого под новым именем ещё не заняты: иначе содержимое
   * смешалось бы с чужим.
   */
  private static void requireFree(Path dir, String name) {
    for (Path taken : new Path[] {dir.resolve(name), dir.resolve(name + ".xml")}) {
      if (Files.exists(taken)) {
        throw new IllegalArgumentException("target already exists: " + taken);
      }
    }
  }

  /**
   * Ссылки объекта на самого себя получают новое имя: в описании и в файлах содержимого.
   *
   * <p>По имени объекта платформа находит форму по умолчанию и схему компоновки
   * ({@code ExternalReport.<Имя>.Form.ФормаОтчета}) и тип основного реквизита формы
   * ({@code cfg:ExternalReportObject.<Имя>}). Со старым именем ibcmd {@code config import --out}
   * отказывает: «Неизвестный объект метаданных» у свойства описания и «Несоответствие свойства и
   * элемента данных XDTO: Свойство: 'Type'» у реквизита формы. Типы табличных частей внешнего
   * объекта платформа называет без приставки External ({@code ReportTabularSection.<Имя>.<ТЧ>}) и
   * так же принимает их у реквизита формы. {@code ReportObject.<Имя>} и
   * {@code DataProcessorObject.<Имя>} в этот список не входят: во внешнем объекте так записан тип
   * объекта конфигурации, с которым совпал идентификатор типа (ssl31/src/epf), и переименованный
   * тип платформа уже не находит.
   */
  private static void renameSelfReferences(Path objectXml, Path content, String kind, String oldName, String newName)
    throws IOException {
    String prefixes = SELF_REFERENCE_PREFIXES.get(kind);
    if (prefixes == null || oldName == null || oldName.isEmpty() || oldName.equals(newName)) {
      return;
    }
    Pattern self = Pattern.compile(
      "(?<![\\p{L}\\p{N}_])(" + prefixes + ")\\." + Pattern.quote(oldName) + "(?![\\p{L}\\p{N}_])");
    for (Path file : xmlFiles(objectXml, content)) {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String renamed = self.matcher(text).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "." + newName));
      if (!renamed.equals(text)) {
        Files.writeString(file, renamed, StandardCharsets.UTF_8);
      }
    }
  }

  /**
   * Типы копии называются так, как их называет платформа у внешнего объекта: объект -
   * {@code ExternalReportObject.<Имя>}, табличная часть - {@code ReportTabularSection.<Имя>.<ТЧ>} и
   * {@code ReportTabularSectionRow.<Имя>.<ТЧ>}; ссылки на прежние имена в описании и формах переходят
   * на них.
   *
   * <p>Внешний объект, сохранённый из объекта конфигурации, несёт его идентификаторы типов, и
   * платформа в базе с этой конфигурацией называет его тип типом объекта конфигурации (так записаны
   * внешние обработки ssl31: {@code DataProcessorObject.<Имя>} у описания и у основного реквизита
   * формы). У копии идентификаторы свои, совпадение пропадает: основной реквизит формы копии с
   * прежним именем типа получил бы тип обработки конфигурации, а не самой копии.
   */
  private static void renameOwnTypes(Path objectXml, Path content, String kind, String newName) throws IOException {
    String base = ExternalArtifactKind.REPORT.name().equals(kind) ? "Report" : "DataProcessor";
    Map<String, String> renamed = new LinkedHashMap<>();
    Matcher generated = GENERATED_TYPE.matcher(Files.readString(objectXml, StandardCharsets.UTF_8));
    while (generated.find()) {
      String name = generated.group(1);
      String section = name.substring(name.lastIndexOf('.') + 1);
      String own = switch (generated.group(2)) {
        case "Object" -> "External" + base + "Object." + newName;
        case "TabularSection" -> base + "TabularSection." + newName + "." + section;
        case "TabularSectionRow" -> base + "TabularSectionRow." + newName + "." + section;
        default -> name;
      };
      if (!own.equals(name)) {
        renamed.put(name, own);
      }
    }
    if (renamed.isEmpty()) {
      return;
    }
    StringBuilder names = new StringBuilder();
    for (String name : renamed.keySet()) {
      names.append(names.length() == 0 ? "" : "|").append(Pattern.quote(name));
    }
    Pattern types = Pattern.compile("(?<![\\p{L}\\p{N}_.])(" + names + ")(?![\\p{L}\\p{N}_.])");
    for (Path file : xmlFiles(objectXml, content)) {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String updated = types.matcher(text).replaceAll(match -> Matcher.quoteReplacement(renamed.get(match.group(1))));
      if (!updated.equals(text)) {
        Files.writeString(file, updated, StandardCharsets.UTF_8);
      }
    }
  }

  /**
   * Свои идентификаторы копии: у описания объекта, его реквизитов, табличных частей и порождённых
   * типов и у описаний форм и макетов, иначе копия повторяет исходник. Номер класса платформы
   * ({@code xr:ClassId}) остаётся. Содержимое форм идентификаторов объекта не несёт и не меняется.
   * Новые идентификаторы выводятся из правки и текста исходника: повтор той же копии даёт те же файлы.
   */
  private static void remapUuids(Path objectXml, Path content, String seed) throws IOException {
    for (Path file : xmlFiles(objectXml, content)) {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      if (METADATA_OBJECT_ROOT.matcher(text).find()) {
        Files.writeString(file, DistinctUuidRewrite.remapDeterministic(text, seed), StandardCharsets.UTF_8);
      }
    }
  }

  /** Описание объекта и XML его содержимого. */
  private static List<Path> xmlFiles(Path objectXml, Path content) throws IOException {
    List<Path> files = new ArrayList<>();
    files.add(objectXml);
    if (Files.isDirectory(content)) {
      try (Stream<Path> walk = Files.walk(content)) {
        walk.filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
          .sorted()
          .forEach(files::add);
      }
    }
    return files;
  }

  /** Каталог содержимого называется так же, как файл описания; у объекта без модуля и форм его нет. */
  private static void renameContent(Path dir, String oldStem, String newStem) throws IOException {
    Path content = dir.resolve(oldStem);
    if (Files.isDirectory(content)) {
      Files.move(content, dir.resolve(newStem));
    }
  }

  private static String stem(Path objectXml) {
    String fn = objectXml.getFileName().toString();
    if (!fn.toLowerCase().endsWith(".xml")) {
      throw new IllegalArgumentException("expected .xml file: " + objectXml);
    }
    return fn.substring(0, fn.length() - 4);
  }

  private static void copyRecursively(Path srcDir, Path dstDir) throws IOException {
    try (var walk = Files.walk(srcDir)) {
      for (Path src : walk.toList()) {
        Path rel = srcDir.relativize(src);
        Path dst = dstDir.resolve(rel);
        if (Files.isDirectory(src)) {
          Files.createDirectories(dst);
        } else {
          Path parent = dst.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          Files.copy(src, dst);
        }
      }
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (var walk = Files.walk(dir)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
