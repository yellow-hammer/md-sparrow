/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Создание набора объектов метаданных одним вызовом.
 *
 * <p>Типовой набор для подсистемы - это десяток объектов, то есть десяток вызовов
 * {@code add-md-object} и десяток мест, где серия может оборваться на середине. Здесь набор
 * создаётся целиком или не создаётся вовсе: при ошибке на любом элементе выгрузка возвращается
 * в исходное состояние.
 *
 * <p>Откат делается по снимку: до начала запоминается текст {@code Configuration.xml}, дальше
 * отслеживаются созданные файлы. Если что-то пошло не так, файлы удаляются, состав
 * восстанавливается из снимка.
 */
public final class MdObjectBatchAdd {

  /**
   * Один элемент набора.
   *
   * @param type вид объекта, как в {@code --type} у {@code add-md-object}
   * @param name имя объекта
   * @param synonymRu синоним ru; поддерживается там же, где у одиночного добавления
   */
  public record Item(String type, String name, String synonymRu) {
  }

  private MdObjectBatchAdd() {
  }

  /**
   * Создаёт набор объектов.
   *
   * @param configurationXml {@code Configuration.xml} выгрузки
   * @param items набор; пустой набор считается ошибкой - вызов без работы это опечатка
   * @param version версия формата выгрузки
   * @return имена созданных объектов вида {@code Catalog.Валюты} в порядке набора
   */
  public static List<String> add(Path configurationXml, List<Item> items, SchemaVersion version)
    throws IOException, JAXBException {
    Objects.requireNonNull(configurationXml, "configurationXml");
    Objects.requireNonNull(version, "version");
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("набор объектов пуст");
    }
    List<MdObjectAddType> types = checkBeforeWriting(items);
    checkNotInConfiguration(configurationXml, items, types, version);

    Path cfRoot = configurationXml.getParent();
    String snapshot = Files.readString(configurationXml, StandardCharsets.UTF_8);
    List<Path> created = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    try {
      for (int i = 0; i < items.size(); i++) {
        Item item = items.get(i);
        MdObjectAddType type = types.get(i);
        MdObjectAdd.add(configurationXml, item.name(), version, type, item.synonymRu(), false);
        created.add(CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), item.name()));
        if (type.roleWithExtRights()) {
          created.add(cfRoot.resolve(type.cfSubdir()).resolve(item.name()));
        }
        keys.add(type.configurationXmlTag() + "." + item.name());
      }
      return keys;
    } catch (RuntimeException | IOException | JAXBException e) {
      rollback(configurationXml, snapshot, created);
      throw e;
    }
  }

  /**
   * Всё, что можно проверить до записи: виды, имена и повторы внутри набора.
   *
   * @return виды объектов в порядке набора
   */
  private static List<MdObjectAddType> checkBeforeWriting(List<Item> items) {
    List<MdObjectAddType> types = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Item item : items) {
      if (item == null) {
        throw new IllegalArgumentException("в наборе есть пустой элемент");
      }
      MdObjectAddType type = MdObjectAddType.fromCliName(
        item.type() == null ? "" : item.type());
      if (type != MdObjectAddType.CATALOG && item.synonymRu() != null) {
        throw new IllegalArgumentException(
          "synonymRu поддерживается только для type CATALOG: " + item.name());
      }
      CatalogNameConstraints.check(item.name());
      if (!seen.add(type.configurationXmlTag() + "." + item.name())) {
        throw new IllegalArgumentException("объект встречается в наборе дважды: " + item.name());
      }
      types.add(type);
    }
    return types;
  }

  /**
   * Объекты набора не должны уже быть в составе: одиночное добавление такой объект перезапишет
   * молча, а для набора это половина работы, которую потом не отличить от целой.
   */
  private static void checkNotInConfiguration(
    Path configurationXml, List<Item> items, List<MdObjectAddType> types, SchemaVersion version)
    throws IOException, JAXBException {
    Set<String> declared = new LinkedHashSet<>();
    for (ChildObjectEntry entry : ConfigurationChildObjectsExtractor.readChildObjects(configurationXml, version)) {
      declared.add(entry.objectType() + "." + entry.name());
    }
    for (int i = 0; i < items.size(); i++) {
      String key = types.get(i).configurationXmlTag() + "." + items.get(i).name();
      if (declared.contains(key)) {
        throw new IllegalArgumentException("объект уже есть в выгрузке: " + key);
      }
    }
  }

  /** Возвращает выгрузку в исходное состояние: созданные файлы удаляются, состав - из снимка. */
  private static void rollback(Path configurationXml, String snapshot, List<Path> created) throws IOException {
    for (Path file : created) {
      if (Files.isDirectory(file)) {
        CfTreeDelete.deleteAllContents(file);
        Files.deleteIfExists(file);
      } else {
        Files.deleteIfExists(file);
      }
    }
    Files.writeString(configurationXml, snapshot, StandardCharsets.UTF_8);
  }
}
