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

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD мутации узлов ChildObjects (реквизиты, ТЧ, реквизиты ТЧ) с гранулярной записью.
 *
 * <p>Новый узел берётся из эталона платформы для этого вида владельца ({@link GoldenNodes}) и
 * встаёт на место, где его пишет платформа: виды узлов идут в порядке схемы
 * ({@link ChildObjectKinds}), новый - последним среди узлов своего вида. Идентификаторы нового
 * узла и копии детерминированы: они выводятся из правки и текста файла владельца.
 */
public final class MdObjectChildMutations {

  private static final String CLOSE_CHILD_OBJECTS = "</ChildObjects>";
  private static final Pattern NAME_TAG = Pattern.compile("<Name>([\\s\\S]*?)</Name>");
  private static final Pattern EMPTY_CHILD_OBJECTS = Pattern.compile("<ChildObjects>\\s*</ChildObjects>");
  private static final String COLLAPSED_CHILD_OBJECTS = "<ChildObjects/>";

  private MdObjectChildMutations() {
  }

  /**
   * Добавляет реквизит в корневой {@code ChildObjects} объекта.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param newName имя нового реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addAttribute(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, "Attribute");
      ensureAllowedInOwner(xml, containerLocal, "Attribute");
      ensureNotBlank(newName, "Введите имя реквизита.");
      ChildNodeNames.ensureFree(xml, containerLocal, "Attribute", newName, null, "Реквизит");
      return insertGoldenNode(xml, containerLocal, version, "Attribute", newName);
    });
  }

  /**
   * Переименовывает реквизит в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param oldName текущее имя реквизита
   * @param newName новое имя реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameAttribute(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> renameAttributeFieldRefs(
      renameNamedChild(xml, containerLocal, "Attribute", oldName, newName, "Реквизит"),
      oldName,
      newName
    ));
    FormDataPathCleanup.afterChildRename(objectXml, oldName, newName);
  }

  /**
   * Удаляет реквизит из корневого {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param name имя удаляемого реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteAttribute(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> removeAttributeFieldRefs(
      deleteNamedChild(xml, containerLocal, "Attribute", name, "Реквизит"),
      name
    ));
    FormDataPathCleanup.afterChildDelete(objectXml, name);
  }

  /**
   * Создаёт копию реквизита в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param sourceName имя исходного реквизита
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateAttribute(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> duplicateNamedChild(
      xml,
      containerLocal,
      "Attribute",
      sourceName,
      newName,
      "Реквизит"
    ));
  }

  /**
   * Добавляет измерение регистра в корневой {@code ChildObjects}.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param newName имя нового измерения
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addDimension(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    addRegisterChild(objectXml, version, "Dimension", "Измерение", newName);
  }

  /**
   * Переименовывает измерение регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param oldName текущее имя
   * @param newName новое имя
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameDimension(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    renameRegisterChild(objectXml, version, "Dimension", "Измерение", oldName, newName);
  }

  /**
   * Удаляет измерение регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param name имя измерения
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteDimension(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    deleteRegisterChild(objectXml, version, "Dimension", "Измерение", name);
  }

  /**
   * Создаёт копию измерения регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param sourceName имя исходного измерения
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateDimension(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      duplicateNamedChild(xml, containerLocal, "Dimension", sourceName, newName, "Измерение"));
  }

  /**
   * Переставляет измерения регистра в заданном порядке.
   */
  public static void reorderDimensions(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    reorderRegisterChildren(objectXml, version, "Dimension", "Измерение", order);
  }

  /**
   * Добавляет ресурс регистра в корневой {@code ChildObjects}.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param newName имя нового ресурса
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addResource(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    addRegisterChild(objectXml, version, "Resource", "Ресурс", newName);
  }

  /**
   * Переименовывает ресурс регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param oldName текущее имя
   * @param newName новое имя
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameResource(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    renameRegisterChild(objectXml, version, "Resource", "Ресурс", oldName, newName);
  }

  /**
   * Удаляет ресурс регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param name имя ресурса
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteResource(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    deleteRegisterChild(objectXml, version, "Resource", "Ресурс", name);
  }

  /**
   * Создаёт копию ресурса регистра.
   *
   * @param objectXml путь к XML регистра
   * @param version версия схемы Designer XML
   * @param sourceName имя исходного ресурса
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateResource(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      duplicateNamedChild(xml, containerLocal, "Resource", sourceName, newName, "Ресурс"));
  }

  /**
   * Переставляет ресурсы регистра в заданном порядке.
   */
  public static void reorderResources(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    reorderRegisterChildren(objectXml, version, "Resource", "Ресурс", order);
  }

  /** Добавляет признак учёта плана счетов: тип Булево обязателен формату. */
  public static void addAccountingFlag(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    addBooleanChild(objectXml, version, "AccountingFlag", "Признак учёта", newName);
  }

  public static void renameAccountingFlag(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    renameRegisterChild(objectXml, version, "AccountingFlag", "Признак учёта", oldName, newName);
  }

  public static void deleteAccountingFlag(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    deleteRegisterChild(objectXml, version, "AccountingFlag", "Признак учёта", name);
  }

  public static void addExtDimensionAccountingFlag(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    addBooleanChild(objectXml, version, "ExtDimensionAccountingFlag", "Признак учёта субконто", newName);
  }

  public static void renameExtDimensionAccountingFlag(
    Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    renameRegisterChild(objectXml, version, "ExtDimensionAccountingFlag", "Признак учёта субконто", oldName, newName);
  }

  public static void deleteExtDimensionAccountingFlag(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    deleteRegisterChild(objectXml, version, "ExtDimensionAccountingFlag", "Признак учёта субконто", name);
  }

  /**
   * Удаляет форму объекта: запись в ChildObjects, файл описания и каталог
   * содержимого. Ссылки на форму в основных и вспомогательных слотах
   * очищаются, чтобы объект не указывал на несуществующую форму.
   */
  public static void deleteForm(Path objectXml, SchemaVersion version, String formName)
    throws IOException, JAXBException {
    ensureNotBlank(formName, "Введите имя формы.");
    // У формы своё правило поддержки: разрешение на объект её не открывает
    Path formDescriptor = objectXml.toAbsolutePath().normalize().getParent()
      .resolve(stemOf(objectXml))
      .resolve("Forms")
      .resolve(formName + ".xml");
    if (Files.isRegularFile(formDescriptor)) {
      SupportRules.ensureEditable(formDescriptor);
    }
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      String entry = "<Form>" + escapeXml(formName) + "</Form>";
      int at = xml.indexOf(entry);
      if (at < 0) {
        throw new IllegalArgumentException("Форма не найдена: " + formName);
      }
      // Запись занимает свою строку: уходит строка целиком, с отступом и переводом строки после неё
      String updated = removeRegion(xml, new MdObjectXmlRegions.Region(at, at + entry.length()));
      // Слоты основных и вспомогательных форм, указывающие на удаляемую,
      // становятся пустыми самозакрывающимися элементами
      updated = updated.replaceAll(
        "<([A-Za-z]*Form)>[^<]*[.]Form[.]" + java.util.regex.Pattern.quote(formName) + "</\\1>",
        "<$1/>");
      return collapseRootChildObjects(updated, containerLocal);
    });
    Path formsDir = objectXml.toAbsolutePath().normalize().getParent()
      .resolve(stemOf(objectXml))
      .resolve("Forms");
    Path descriptor = formsDir.resolve(formName + ".xml");
    Files.deleteIfExists(descriptor);
    Path contentDir = formsDir.resolve(formName);
    if (Files.isDirectory(contentDir)) {
      try (var walk = Files.walk(contentDir)) {
        for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  private static String stemOf(Path objectXml) {
    return objectXml.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
  }

  private static void addBooleanChild(
    Path objectXml,
    SchemaVersion version,
    String childLocal,
    String label,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, childLocal);
      ensureAllowedInOwner(xml, containerLocal, childLocal);
      ensureNotBlank(newName, "Введите имя: " + label.toLowerCase(java.util.Locale.ROOT) + ".");
      ChildNodeNames.ensureFree(xml, containerLocal, childLocal, newName, null, label);
      return insertGoldenNode(xml, containerLocal, version, childLocal, newName);
    });
  }

  private static void addRegisterChild(
    Path objectXml,
    SchemaVersion version,
    String childLocal,
    String label,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, childLocal);
      ensureAllowedInOwner(xml, containerLocal, childLocal);
      ensureNotBlank(newName, "Введите имя: " + label.toLowerCase(java.util.Locale.ROOT) + ".");
      ChildNodeNames.ensureFree(xml, containerLocal, childLocal, newName, null, label);
      return insertGoldenNode(xml, containerLocal, version, childLocal, newName);
    });
  }

  private static void renameRegisterChild(
    Path objectXml,
    SchemaVersion version,
    String childLocal,
    String label,
    String oldName,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      renameNamedChild(xml, containerLocal, childLocal, oldName, newName, label));
    FormDataPathCleanup.afterChildRename(objectXml, oldName, newName);
  }

  private static void deleteRegisterChild(
    Path objectXml,
    SchemaVersion version,
    String childLocal,
    String label,
    String name
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      deleteNamedChild(xml, containerLocal, childLocal, name, label));
    FormDataPathCleanup.afterChildDelete(objectXml, name);
  }

  private static void reorderRegisterChildren(
    Path objectXml,
    SchemaVersion version,
    String childLocal,
    String label,
    List<String> order
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, label,
        name -> MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childLocal, name)));
  }

  /**
   * Добавляет команду объекта в корневой {@code ChildObjects}.
   *
   * Модуль команды платформа заводит сама при первом открытии, поэтому здесь только узел команды:
   * группа {@code FormCommandBarImportant}, остальное - умолчания платформы.
   *
   * @param objectXml путь к XML объекта
   * @param version версия схемы Designer XML
   * @param newName имя команды
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addCommand(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, "Command");
      ensureNotBlank(newName, "Введите имя команды.");
      ChildNodeNames.ensureFree(xml, containerLocal, "Command", newName, null, "Команда");
      return insertGoldenNode(xml, containerLocal, version, "Command", newName);
    });
  }

  /**
   * Переименовывает команду объекта.
   *
   * @param objectXml путь к XML объекта
   * @param version версия схемы Designer XML
   * @param oldName текущее имя команды
   * @param newName новое имя команды
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameCommand(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      renameNamedChild(xml, containerLocal, "Command", oldName, newName, "Команда"));
    CommandFiles.renameCommandDirectory(objectXml, oldName, newName);
  }

  /**
   * Удаляет команду объекта вместе с каталогом её модуля.
   *
   * @param objectXml путь к XML объекта
   * @param version версия схемы Designer XML
   * @param name имя удаляемой команды
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteCommand(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      deleteNamedChild(xml, containerLocal, "Command", name, "Команда"));
    CommandFiles.deleteCommandDirectory(objectXml, name);
  }

  /**
   * Добавляет значение перечисления в корневой {@code ChildObjects}.
   *
   * @param objectXml путь к XML перечисления
   * @param version версия схемы Designer XML
   * @param newName имя нового значения
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addEnumValue(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, "EnumValue");
      ensureNotBlank(newName, "Введите имя значения.");
      ChildNodeNames.ensureFree(xml, containerLocal, "EnumValue", newName, null, "Значение");
      return insertGoldenNode(xml, containerLocal, version, "EnumValue", newName);
    });
  }

  /**
   * Переименовывает значение перечисления в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML перечисления
   * @param version версия схемы Designer XML
   * @param oldName текущее имя значения
   * @param newName новое имя значения
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameEnumValue(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      renameNamedChild(xml, containerLocal, "EnumValue", oldName, newName, "Значение"));
  }

  /**
   * Удаляет значение перечисления из корневого {@code ChildObjects}.
   *
   * @param objectXml путь к XML перечисления
   * @param version версия схемы Designer XML
   * @param name имя удаляемого значения
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteEnumValue(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      deleteNamedChild(xml, containerLocal, "EnumValue", name, "Значение"));
  }

  /**
   * Создаёт копию значения перечисления в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML перечисления
   * @param version версия схемы Designer XML
   * @param sourceName имя исходного значения
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateEnumValue(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      duplicateNamedChild(xml, containerLocal, "EnumValue", sourceName, newName, "Значение"));
  }

  /**
   * Добавляет табличную часть в корневой {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param newName имя новой табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addTabularSection(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, "TabularSection");
      ensureAllowedInOwner(xml, containerLocal, "TabularSection");
      ensureNotBlank(newName, "Введите имя табличной части.");
      ChildNodeNames.ensureFree(xml, containerLocal, "TabularSection", newName, null, "Табличная часть");
      return insertGoldenNode(xml, containerLocal, version, "TabularSection", newName);
    });
  }

  /**
   * Переименовывает табличную часть в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param oldName текущее имя табличной части
   * @param newName новое имя табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameTabularSection(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> renameNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      oldName,
      newName,
      "Табличная часть"
    ));
    FormDataPathCleanup.afterChildRename(objectXml, oldName, newName);
  }

  /**
   * Удаляет табличную часть из корневого {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param name имя удаляемой табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteTabularSection(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> deleteNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      name,
      "Табличная часть"
    ));
    FormDataPathCleanup.afterChildDelete(objectXml, name);
  }

  /**
   * Создаёт копию табличной части в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param sourceName имя исходной табличной части
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateTabularSection(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> duplicateNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      sourceName,
      newName,
      "Табличная часть"
    ));
  }

  /**
   * Добавляет реквизит в табличную часть.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param newName имя нового реквизита табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addTabularAttribute(Path objectXml, SchemaVersion version, String tabularSectionName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ChildObjectKinds.ensureAllowed(version, containerLocal, "TabularSection");
      ChildObjectKinds.ensureAllowed(version, "TabularSection", "Attribute");
      ensureNotBlank(newName, "Введите имя реквизита табличной части.");
      MdObjectXmlRegions.Region tsRegion = MdObjectXmlRegions.findNamedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName
      );
      if (!tsRegion.isValid()) {
        throw new IllegalArgumentException("Табличная часть не найдена: " + tabularSectionName);
      }
      ChildNodeNames.ensureFreeInTabularSection(xml, containerLocal, tabularSectionName, newName, null);
      String snippet = GoldenNodes.tabularAttribute(
        version,
        containerLocal,
        rootObjectName(xml),
        newName,
        ConfigurationLanguage.current(),
        nodeSeed("add|" + version.name() + "|" + containerLocal + ".TabularSection." + tabularSectionName
          + ".Attribute|" + newName, xml));
      String tsXml = xml.substring(tsRegion.start(), tsRegion.end());
      String updatedTsXml = insertIntoTabularSectionChildObjects(tsXml, snippet);
      return xml.substring(0, tsRegion.start()) + updatedTsXml + xml.substring(tsRegion.end());
    });
  }

  /**
   * Переименовывает реквизит табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param oldName текущее имя реквизита
   * @param newName новое имя реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameTabularAttribute(
    Path objectXml,
    SchemaVersion version,
    String tabularSectionName,
    String oldName,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        oldName
      );
      if (!target.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + oldName);
      }
      ChildNodeNames.ensureFreeInTabularSection(xml, containerLocal, tabularSectionName, newName, oldName);
      String nodeXml = xml.substring(target.start(), target.end());
      String replaced = replaceName(nodeXml, oldName, newName);
      return xml.substring(0, target.start()) + replaced + xml.substring(target.end());
    });
    FormDataPathCleanup.afterChildRename(
      objectXml, tabularSectionName + "." + oldName, tabularSectionName + "." + newName);
  }

  /**
   * Удаляет реквизит из табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param name имя удаляемого реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteTabularAttribute(Path objectXml, SchemaVersion version, String tabularSectionName, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        name
      );
      if (!target.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + name);
      }
      String updated = removeRegion(xml, target);
      MdObjectXmlRegions.Region section = MdObjectXmlRegions.findNamedChildObjectRegion(
        updated, containerLocal, "TabularSection", tabularSectionName);
      return collapseIfEmpty(updated, section.start(), section.end());
    });
    FormDataPathCleanup.afterChildDelete(objectXml, tabularSectionName + "." + name);
  }

  /**
   * Создаёт копию реквизита табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param sourceName имя исходного реквизита
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateTabularAttribute(
    Path objectXml,
    SchemaVersion version,
    String tabularSectionName,
    String sourceName,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region source = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        sourceName
      );
      if (!source.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + sourceName);
      }
      ChildNodeNames.ensureFreeInTabularSection(xml, containerLocal, tabularSectionName, newName, null);
      return duplicateRegion(xml, source, sourceName, newName,
        nodeSeed("duplicate|" + containerLocal + ".TabularSection." + tabularSectionName + ".Attribute|"
          + sourceName + "|" + newName, xml));
    });
  }

  private static void mutateAndWrite(Path objectXml, SchemaVersion version, XmlMutator mutator)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    SupportRules.ensureEditable(objectXml);
    // Подпись нового узла пишется на языке этой конфигурации
    ConfigurationLanguage.with(objectXml, () -> {
      mutateInLanguage(objectXml, version, mutator);
      return null;
    });
  }

  private static void mutateInLanguage(Path objectXml, SchemaVersion version, XmlMutator mutator)
    throws IOException, JAXBException {
    MdObjectStructureDto structure = MdObjectStructureRead.read(objectXml, version);
    String containerLocal = MdObjectPropertiesGranularPatch.containerLocalForKind(structure.kind);
    if (containerLocal == null || containerLocal.isBlank()) {
      throw new IllegalArgumentException("Тип объекта не поддерживает ChildObjects.");
    }
    String original = Files.readString(objectXml, StandardCharsets.UTF_8);
    String updated;
    try {
      updated = mutator.apply(original, containerLocal);
    } catch (XMLStreamException e) {
      throw new IOException("Не удалось разобрать XML для мутации: " + e.getMessage(), e);
    }
    // Вставки собираются с LF: приводим весь результат к переводу строки исходного файла.
    if (original.contains("\r\n")) {
      updated = updated.replace("\r\n", "\n").replace("\n", "\r\n");
    }
    MdObjectStructureRead.read(updated.getBytes(StandardCharsets.UTF_8), version);
    Files.writeString(objectXml, updated, StandardCharsets.UTF_8);
  }

  private static String renameNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String oldName,
    String newName,
    String label
  ) throws XMLStreamException {
    ensureNotBlank(newName, "Введите новое имя.");
    MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, oldName);
    if (!target.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + oldName);
    }
    ChildNodeNames.ensureFree(xml, containerLocal, childTag, newName, oldName, label);
    String nodeXml = xml.substring(target.start(), target.end());
    String replaced = replaceName(nodeXml, oldName, newName);
    replaced = renameGeneratedTypeTail(replaced, oldName, newName);
    return xml.substring(0, target.start()) + replaced + xml.substring(target.end());
  }

  /**
   * Обновляет хвост имени в {@code xr:GeneratedType name="...Тип.Объект.Старое"} блока
   * (актуально для табличных частей; у реквизитов GeneratedType нет).
   */
  private static String renameGeneratedTypeTail(String nodeXml, String oldName, String newName) {
    Pattern generated = Pattern.compile("(<xr:GeneratedType name=\"[^\"]*\\.)" + Pattern.quote(oldName) + "\"");
    Matcher m = generated.matcher(nodeXml);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + escapeXml(newName) + "\""));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** Имя корневого объекта: первый тег Name в файле (Properties объекта). */
  private static String rootObjectName(String xml) {
    Matcher matcher = NAME_TAG.matcher(xml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Не найдено имя объекта.");
    }
    return unescapeXml(matcher.group(1));
  }

  private static String deleteNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String name,
    String label
  ) throws XMLStreamException {
    MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, name);
    if (!target.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + name);
    }
    return collapseRootChildObjects(removeRegion(xml, target), containerLocal);
  }

  private static String collapseRootChildObjects(String xml, String containerLocal) throws XMLStreamException {
    MdObjectXmlRegions.Region childObjects = MdObjectXmlRegions.findChildObjectsRegion(xml, containerLocal);
    return collapseIfEmpty(xml, childObjects.start(), childObjects.end());
  }

  /**
   * Опустевший {@code ChildObjects} в {@code [from, to)} сворачивается в {@code <ChildObjects/>}: так
   * его пишет платформа, и удаление последнего узла возвращает файл, каким он был до добавления.
   */
  private static String collapseIfEmpty(String xml, int from, int to) {
    int open = from < 0 ? -1 : xml.indexOf("<ChildObjects>", from);
    if (open < 0 || open >= to) {
      return xml;
    }
    Matcher empty = EMPTY_CHILD_OBJECTS.matcher(xml).region(open, to);
    if (!empty.lookingAt()) {
      return xml;
    }
    return xml.substring(0, open) + COLLAPSED_CHILD_OBJECTS + xml.substring(empty.end());
  }

  private static String duplicateNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String sourceName,
    String newName,
    String label
  ) throws XMLStreamException {
    ensureNotBlank(newName, "Введите имя копии.");
    ensureAllowedInOwner(xml, containerLocal, childTag);
    MdObjectXmlRegions.Region source = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, sourceName);
    if (!source.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + sourceName);
    }
    ChildNodeNames.ensureFree(xml, containerLocal, childTag, newName, null, label);
    return duplicateRegion(xml, source, sourceName, newName,
      nodeSeed("duplicate|" + containerLocal + "." + childTag + "|" + sourceName + "|" + newName, xml));
  }

  private static String duplicateRegion(
    String xml, MdObjectXmlRegions.Region source, String oldName, String newName, String uuidSeed) {
    String sourceXml = xml.substring(source.start(), source.end());
    String copy = replaceName(DistinctUuidRewrite.remapDeterministic(sourceXml, uuidSeed), oldName, newName);
    copy = renameGeneratedTypeTail(copy, oldName, newName);
    String indent = currentLineIndent(xml, source.start());
    // Узел вырезан от своего тега: у первой строки отступа нет, у вложенных он из файла. Его
    // снимаем, иначе выравнивание прибавит вложенным строкам отступ узла ещё раз.
    String dedented = indent.isEmpty() ? copy : copy.replace("\n" + indent, "\n");
    String normalizedCopy = normalizeBlockIndent(dedented, indent);
    return xml.substring(0, source.end()) + "\n" + normalizedCopy + xml.substring(source.end());
  }

  /** Отказ, если к владельцу, заимствованному в расширении, такой узел не добавить. */
  private static void ensureAllowedInOwner(String xml, String containerLocal, String nodeLocal)
    throws XMLStreamException {
    MdObjectXmlRegions.Region belonging =
      MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xml, containerLocal, "ObjectBelonging");
    boolean adopted = belonging.isValid()
      && xml.substring(belonging.start(), belonging.end()).contains(">Adopted<");
    ChildObjectKinds.ensureAllowedInAdopted(containerLocal, nodeLocal, adopted);
  }

  /**
   * Новый узел из эталона платформы в корневой {@code ChildObjects}.
   *
   * <p>Зерно идентификаторов - как у add-md-object: правка (вид, имя) и текст файла владельца до неё.
   */
  private static String insertGoldenNode(
    String xml,
    String containerLocal,
    SchemaVersion version,
    String nodeLocal,
    String name
  ) throws XMLStreamException, IOException {
    String snippet = GoldenNodes.node(
      version,
      containerLocal,
      nodeLocal,
      rootObjectName(xml),
      name,
      ConfigurationLanguage.current(),
      nodeSeed("add|" + version.name() + "|" + containerLocal + "." + nodeLocal + "|" + name, xml));
    return insertIntoRootChildObjects(xml, containerLocal, version, nodeLocal, snippet);
  }

  /** Зерно идентификаторов правки узла: что создаётся и текст файла, в который. */
  private static String nodeSeed(String what, String ownerXml) {
    return GoldenUuid.from(what, ownerXml);
  }

  /**
   * Вставка в корневой {@code ChildObjects}: перед первым узлом вида, который по схеме идёт позже,
   * иначе в конец. Так узлы стоят, как их пишет платформа: реквизиты, табличные части, формы,
   * макеты, команды (у документа формы раньше табличных частей, у регистров свой порядок).
   */
  private static String insertIntoRootChildObjects(
    String xml,
    String containerLocal,
    SchemaVersion version,
    String nodeLocal,
    String snippet
  ) throws XMLStreamException {
    MdObjectXmlRegions.Region childObjectsRegion = MdObjectXmlRegions.findChildObjectsRegion(xml, containerLocal);
    if (!childObjectsRegion.isValid()) {
      throw new IllegalArgumentException("В объекте нет узла ChildObjects.");
    }
    // У объекта без вложенных платформа пишет пустой тег: чтобы дописать в него, его надо раскрыть.
    int openTagEnd = xml.indexOf('>', childObjectsRegion.start());
    if (openTagEnd > childObjectsRegion.start() && xml.charAt(openTagEnd - 1) == '/') {
      String indent = currentLineIndent(xml, childObjectsRegion.start());
      return xml.substring(0, childObjectsRegion.start())
        + "<ChildObjects>\n"
        + normalizeBlockIndent(snippet, indent + "\t")
        + "\n"
        + indent
        + CLOSE_CHILD_OBJECTS
        + xml.substring(childObjectsRegion.end());
    }
    List<String> order = ChildObjectKinds.of(version, containerLocal);
    int rank = order.indexOf(nodeLocal);
    for (XmlLines.Node child : XmlLines.children(xml, List.of("MetaDataObject", containerLocal, "ChildObjects"))) {
      if (order.indexOf(child.name()) > rank) {
        int lineStart = lineStartBeforeIndent(xml, child.start());
        return xml.substring(0, lineStart)
          + normalizeBlockIndent(snippet, currentLineIndent(xml, child.start()))
          + "\n"
          + xml.substring(lineStart);
      }
    }
    int insertAt = xml.lastIndexOf(CLOSE_CHILD_OBJECTS, childObjectsRegion.end());
    if (insertAt < childObjectsRegion.start()) {
      throw new IllegalArgumentException("Не найден закрывающий тег ChildObjects.");
    }
    String parentIndent = currentLineIndent(xml, insertAt);
    String childIndent = parentIndent + "\t";
    String normalized = normalizeBlockIndent(snippet, childIndent);
    int prefixEnd = lineStartBeforeIndent(xml, insertAt);
    String lead = prefixEnd == insertAt ? "\n" : "";
    return xml.substring(0, prefixEnd) + lead + normalized + "\n" + parentIndent + xml.substring(insertAt);
  }

  private static String insertIntoTabularSectionChildObjects(String tabularSectionXml, String snippet)
    throws XMLStreamException {
    int childObjectsOpen = tabularSectionXml.indexOf("<ChildObjects");
    if (childObjectsOpen >= 0) {
      int childObjectsTagEnd = tabularSectionXml.indexOf('>', childObjectsOpen);
      if (childObjectsTagEnd < 0) {
        throw new IllegalArgumentException("Не найден закрывающий символ тега ChildObjects табличной части.");
      }
      String openTag = tabularSectionXml.substring(childObjectsOpen, childObjectsTagEnd + 1);
      if (openTag.endsWith("/>")) {
        String parentIndent = currentLineIndent(tabularSectionXml, childObjectsOpen);
        String childIndent = parentIndent + "\t";
        String normalized = normalizeBlockIndent(snippet, childIndent);
        String replacement = "<ChildObjects>\n"
          + normalized
          + "\n"
          + parentIndent
          + "</ChildObjects>";
        return tabularSectionXml.substring(0, childObjectsOpen)
          + replacement
          + tabularSectionXml.substring(childObjectsTagEnd + 1);
      }
      int childObjectsClose = tabularSectionXml.indexOf(CLOSE_CHILD_OBJECTS, childObjectsOpen);
      if (childObjectsClose < 0) {
        throw new IllegalArgumentException("Не найден закрывающий тег ChildObjects табличной части.");
      }
      int insertAt = childObjectsClose;
      String parentIndent = currentLineIndent(tabularSectionXml, insertAt);
      String childIndent = parentIndent + "\t";
      String normalized = normalizeBlockIndent(snippet, childIndent);
      int prefixEnd = lineStartBeforeIndent(tabularSectionXml, insertAt);
      String lead = prefixEnd == insertAt ? "\n" : "";
      return tabularSectionXml.substring(0, prefixEnd)
        + lead
        + normalized
        + "\n"
        + parentIndent
        + tabularSectionXml.substring(insertAt);
    }
    int closeTs = tabularSectionXml.lastIndexOf("</TabularSection>");
    if (closeTs < 0) {
      throw new IllegalArgumentException("Не найден закрывающий тег TabularSection.");
    }
    String tsIndent = currentLineIndent(tabularSectionXml, closeTs);
    String childObjectsIndent = tsIndent + "\t";
    String itemIndent = childObjectsIndent + "\t";
    String normalized = normalizeBlockIndent(snippet, itemIndent);
    String block = "\n"
      + childObjectsIndent
      + "<ChildObjects>\n"
      + normalized
      + "\n"
      + childObjectsIndent
      + "</ChildObjects>\n"
      + tsIndent;
    return tabularSectionXml.substring(0, closeTs) + block + tabularSectionXml.substring(closeTs);
  }

  private static String replaceName(String nodeXml, String oldName, String newName) {
    Matcher matcher = NAME_TAG.matcher(nodeXml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Не найден тег Name.");
    }
    String current = unescapeXml(matcher.group(1));
    if (!current.equals(oldName)) {
      throw new IllegalArgumentException("Ожидалось имя " + oldName + ", найдено " + current + ".");
    }
    String escaped = escapeXml(newName);
    return nodeXml.substring(0, matcher.start(1)) + escaped + nodeXml.substring(matcher.end(1));
  }

  /**
   * Переставляет реквизиты корневого {@code ChildObjects} в заданном порядке.
   * Перечисленные блоки меняются местами между собой; не перечисленные остаются на своих местах.
   */
  public static void reorderAttributes(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, "Реквизит",
        name -> MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, "Attribute", name)));
  }

  /**
   * Переставляет команды корневого {@code ChildObjects} в заданном порядке.
   *
   * @param objectXml путь к XML объекта
   * @param version версия схемы Designer XML
   * @param order имена команд в нужном порядке
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void reorderCommands(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, "Команда",
        name -> MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, "Command", name)));
  }

  /**
   * Переставляет табличные части корневого {@code ChildObjects} в заданном порядке.
   */
  public static void reorderTabularSections(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, "Табличная часть",
        name -> MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, "TabularSection", name)));
  }

  /**
   * Переставляет значения перечисления корневого {@code ChildObjects} в заданном порядке.
   */
  public static void reorderEnumValues(Path objectXml, SchemaVersion version, List<String> order)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, "Значение",
        name -> MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, "EnumValue", name)));
  }

  /**
   * Переставляет реквизиты табличной части в заданном порядке.
   */
  public static void reorderTabularAttributes(
    Path objectXml,
    SchemaVersion version,
    String tabularSectionName,
    List<String> order
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) ->
      reorderNamedRegions(xml, order, "Реквизит ТЧ",
        name -> MdObjectXmlRegions.findNamedNestedChildObjectRegion(
          xml, containerLocal, "TabularSection", tabularSectionName, "Attribute", name)));
  }

  private interface RegionByName {
    MdObjectXmlRegions.Region find(String name) throws XMLStreamException;
  }

  private static String reorderNamedRegions(
    String xml,
    List<String> order,
    String label,
    RegionByName finder
  ) throws XMLStreamException {
    if (order == null || order.size() < 2) {
      return xml;
    }
    List<MdObjectXmlRegions.Region> regions = new ArrayList<>();
    List<String> blocks = new ArrayList<>();
    for (String name : order) {
      ensureNotBlank(name, "Пустое имя в порядке сортировки.");
      MdObjectXmlRegions.Region region = finder.find(name);
      if (!region.isValid()) {
        throw new IllegalArgumentException(label + " не найден: " + name);
      }
      regions.add(region);
      blocks.add(xml.substring(region.start(), region.end()));
    }
    List<Integer> byStart = new ArrayList<>();
    for (int i = 0; i < regions.size(); i++) {
      byStart.add(i);
    }
    byStart.sort((a, b) -> Integer.compare(regions.get(a).start(), regions.get(b).start()));
    for (int i = 1; i < byStart.size(); i++) {
      if (regions.get(byStart.get(i)).start() < regions.get(byStart.get(i - 1)).end()) {
        throw new IllegalArgumentException("Пересечение блоков при сортировке (дубль имени?).");
      }
    }
    StringBuilder out = new StringBuilder(xml.length());
    int cursor = 0;
    for (int k = 0; k < byStart.size(); k++) {
      MdObjectXmlRegions.Region slot = regions.get(byStart.get(k));
      out.append(xml, cursor, slot.start());
      // k-я позиция по тексту получает k-й блок из желаемого порядка
      out.append(blocks.get(k));
      cursor = slot.end();
    }
    out.append(xml, cursor, xml.length());
    return out.toString();
  }

  /** Убирает из свойств объекта ссылки на удалённый реквизит ({@code xr:Field ...Attribute.Имя}). */
  private static String removeAttributeFieldRefs(String xml, String attrName) {
    Pattern refPattern = Pattern.compile("<xr:Field>[^<]*\\.Attribute\\." + Pattern.quote(attrName) + "</xr:Field>");
    Matcher m = refPattern.matcher(xml);
    List<MdObjectXmlRegions.Region> regions = new ArrayList<>();
    while (m.find()) {
      regions.add(new MdObjectXmlRegions.Region(m.start(), m.end()));
    }
    String out = xml;
    for (int i = regions.size() - 1; i >= 0; i--) {
      out = removeRegion(out, regions.get(i));
    }
    return out;
  }

  /** Обновляет ссылки на переименованный реквизит в свойствах объекта. */
  private static String renameAttributeFieldRefs(String xml, String oldName, String newName) {
    return xml.replace(".Attribute." + oldName + "</xr:Field>", ".Attribute." + newName + "</xr:Field>");
  }

  /** Начало строки перед позицией: срезает хвостовые пробелы/табы, если до них перевод строки. */
  private static int lineStartBeforeIndent(String xml, int pos) {
    int cut = pos;
    while (cut > 0 && (xml.charAt(cut - 1) == '\t' || xml.charAt(cut - 1) == ' ')) {
      cut--;
    }
    if (cut == 0 || xml.charAt(cut - 1) == '\n') {
      return cut;
    }
    return pos;
  }

  private static String removeRegion(String xml, MdObjectXmlRegions.Region region) {
    String left = xml.substring(0, region.start());
    String right = xml.substring(region.end());
    // Блок занимал свои строки: убираем и отступ его первой строки, и перевод строки после него,
    // иначе остаётся строка из одних табов.
    int cut = left.length();
    while (cut > 0 && (left.charAt(cut - 1) == '\t' || left.charAt(cut - 1) == ' ')) {
      cut--;
    }
    boolean leftAtLineStart = cut == 0 || left.charAt(cut - 1) == '\n';
    if (leftAtLineStart && (right.startsWith("\r\n") || right.startsWith("\n"))) {
      left = left.substring(0, cut);
      right = right.startsWith("\r\n") ? right.substring(2) : right.substring(1);
    } else if (left.endsWith("\n") && right.startsWith("\n")) {
      right = right.substring(1);
    }
    return left + right;
  }

  private static String normalizeBlockIndent(String block, String indent) {
    String normalized = block.replace("\r\n", "\n").replace('\r', '\n').trim();
    String[] lines = normalized.split("\n");
    int minLead = Integer.MAX_VALUE;
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      int lead = 0;
      while (lead < line.length() && (line.charAt(lead) == ' ' || line.charAt(lead) == '\t')) {
        lead++;
      }
      minLead = Math.min(minLead, lead);
    }
    if (minLead == Integer.MAX_VALUE) {
      minLead = 0;
    }
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      String core = line.length() >= minLead ? line.substring(minLead) : line.trim();
      out.add(indent + core);
    }
    return String.join("\n", out);
  }

  private static String currentLineIndent(String xml, int offset) {
    int start = offset - 1;
    while (start >= 0 && xml.charAt(start) != '\n' && xml.charAt(start) != '\r') {
      start--;
    }
    start++;
    int i = start;
    while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
      i++;
    }
    return xml.substring(start, i);
  }

  private static void ensureNotBlank(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static String escapeXml(String value) {
    String v = value == null ? "" : value;
    return v.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }

  private static String unescapeXml(String value) {
    return value.replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&");
  }

  private interface XmlMutator {
    String apply(String xml, String containerLocal) throws XMLStreamException, IOException;
  }
}
