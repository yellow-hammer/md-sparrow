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
import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectProperties;
import io.github.yellowhammer.edt.EdtObjectReader;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Общие реквизиты, применённые к объекту метаданных, — поля его таблиц языка запросов
 * ({@code cf-md-query-tables}).
 *
 * <p>Реквизит применён к объекту, если в его составе ({@code Content}) у объекта стоит
 * «Использовать» ({@code Use}); «Не использовать» ({@code DontUse}) — не применён; «Авто» или объекта
 * в составе нет — применён, только когда «Автоиспользование» реквизита ({@code AutoUse}) —
 * «Использовать». Условное разделение действует при выполнении и на состав полей не влияет.
 *
 * <p>Реквизиты берутся из каталога {@code CommonAttributes} конфигурации, в которой лежит файл объекта
 * (выгрузка конфигуратора или проект EDT). Свойства читаются моделью свойств объекта ({@code cf-md-object-get}:
 * тип, синоним на языке конфигурации, разделение), состав — из самого файла: модель его не несёт.
 * Реквизит, заимствованный расширением ({@code ObjectBelonging} — {@code Adopted}), не применяется: его тип,
 * разделение и автоиспользование — в основной конфигурации, а файл расширения их не несёт (у EDT подставились бы
 * умолчания модели — разделитель с автоиспользованием); вместо полей — запись в {@code notes}, у обеих раскладок одна.
 *
 * <p>Прочитанный реквизит держится в кэше по пути, размеру и времени изменения файла: в режиме
 * {@code serve} операцию зовут на каждую таблицу, а состав разделителя большой конфигурации — тысячи
 * элементов.
 */
final class CommonAttributeUsage {

  private static final String DIRECTORY = "CommonAttributes";

  /** Сколько прочитанных файлов держать: правка реквизита даёт новую запись, старые чистятся разом. */
  private static final int CACHE_LIMIT = 1024;

  private static final Map<String, Optional<Attribute>> CACHE = new ConcurrentHashMap<>();

  private static final Result NONE = new Result(List.of(), List.of());

  private CommonAttributeUsage() {
  }

  /**
   * Применённый к объекту общий реквизит.
   *
   * @param name имя реквизита — оно же имя поля на обоих языках
   * @param separator реквизит — разделитель («Разделение данных» — «Разделять», {@code Separate})
   * @param separatedDataUse у разделителя — «Использование разделяемых данных» константой модели
   *     ({@code INDEPENDENTLY} или {@code INDEPENDENTLY_AND_SIMULTANEOUSLY}); у прочих {@code null}
   * @param type тип реквизита
   * @param synonym синоним на языке конфигурации
   */
  record Applied(String name, boolean separator, String separatedDataUse, MdTypeDescriptionDto type,
    String synonym) {
  }

  /**
   * @param applied применённые реквизиты по имени
   * @param notes файлы реквизитов, которые не прочитались: их полей в ответе нет
   */
  record Result(List<Applied> applied, List<String> notes) {
  }

  /**
   * Прочитанный реквизит: признаки разделения, автоиспользование и состав {@code Вид.Имя} → использование;
   * {@code adopted} — заимствован расширением, свойства его в основной конфигурации.
   */
  private record Attribute(String name, boolean adopted, boolean separator, String separatedDataUse, boolean autoUse,
    Map<String, String> content, MdTypeDescriptionDto type, String synonym) {

    boolean appliesTo(String objectRef) {
      String use = content.getOrDefault(objectRef, "");
      if ("Use".equalsIgnoreCase(use)) {
        return true;
      }
      if ("DontUse".equalsIgnoreCase(use)) {
        return false;
      }
      return autoUse;
    }
  }

  /**
   * Общие реквизиты, применённые к объекту.
   *
   * @param objectFile файл объекта выгрузки или проекта EDT
   * @param objectRef объект так, как его пишет состав реквизита: {@code Вид.Имя} ({@code Catalog.Валюты})
   * @param version формат выгрузки; у проекта EDT не нужен
   * @return реквизиты по имени; вне выгрузки или без каталога {@code CommonAttributes} — пусто, без ошибки
   */
  static Result of(Path objectFile, String objectRef, SchemaVersion version) {
    Path configuration = ConfigurationLanguage.configurationFileOf(objectFile);
    if (configuration == null) {
      return NONE;
    }
    boolean edt = EdtLayout.isObjectFile(configuration);
    Path directory = TypeCompositions.objectsDirectory(configuration, DIRECTORY);
    List<Path> files = files(directory, edt);
    if (files.isEmpty()) {
      return NONE;
    }
    List<Applied> applied = new ArrayList<>();
    List<String> notes = new ArrayList<>();
    for (Path file : files) {
      Optional<Attribute> attribute;
      try {
        attribute = cached(file, edt, version);
      } catch (IOException | JAXBException | RuntimeException e) {
        notes.add("Общий реквизит " + nameOf(file) + " не прочитан, его поля не перечислены: " + e.getMessage());
        continue;
      }
      if (attribute.isEmpty()) {
        notes.add("Файл " + file.getFileName() + " в каталоге " + DIRECTORY + " — не общий реквизит, пропущен.");
        continue;
      }
      Attribute a = attribute.get();
      if (a.adopted()) {
        notes.add("Общий реквизит " + a.name() + " заимствован расширением: его свойства — в основной конфигурации, "
          + "поля не перечислены.");
        continue;
      }
      if (a.appliesTo(objectRef)) {
        applied.add(new Applied(a.name(), a.separator(), a.separatedDataUse(), a.type(), a.synonym()));
      }
    }
    // По имени, а не по пути: у выгрузки X.xml, у EDT X/X.mdo — порядок обеих раскладок один
    applied.sort(Comparator.comparing(Applied::name));
    return new Result(List.copyOf(applied), List.copyOf(notes));
  }

  /** Файлы реквизитов: у выгрузки {@code <Имя>.xml}, у EDT {@code <Имя>/<Имя>.mdo}. */
  private static List<Path> files(Path directory, boolean edt) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    List<Path> out = new ArrayList<>();
    try (Stream<Path> entries = Files.list(directory)) {
      entries.forEach(entry -> {
        String name = entry.getFileName().toString();
        if (edt && Files.isDirectory(entry)) {
          Path mdo = entry.resolve(name + ".mdo");
          if (Files.isRegularFile(mdo)) {
            out.add(mdo);
          }
        } else if (!edt && name.endsWith(".xml") && Files.isRegularFile(entry)) {
          out.add(entry);
        }
      });
    } catch (IOException e) {
      return List.of();
    }
    return out;
  }

  private static String nameOf(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? name : name.substring(0, dot);
  }

  private static Optional<Attribute> cached(Path file, boolean edt, SchemaVersion version)
    throws IOException, JAXBException {
    Path absolute = file.toAbsolutePath().normalize();
    String key = absolute + "|" + Files.size(absolute) + "|" + Files.getLastModifiedTime(absolute).toMillis()
      + "|" + (edt ? "edt" : String.valueOf(version));
    Optional<Attribute> hit = CACHE.get(key);
    if (hit != null) {
      return hit;
    }
    Optional<Attribute> read = edt ? readEdt(absolute) : readDesigner(absolute, version);
    if (CACHE.size() >= CACHE_LIMIT) {
      CACHE.clear();
    }
    CACHE.put(key, read);
    return read;
  }

  private static Optional<Attribute> readDesigner(Path file, SchemaVersion version) throws IOException, JAXBException {
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(file, version);
    Map<String, String> content = new HashMap<>();
    String xml = Files.readString(file, StandardCharsets.UTF_8);
    for (XmlLines.Node item : XmlLines.children(xml,
      List.of("MetaDataObject", "CommonAttribute", "Properties", "Content"))) {
      if ("Item".equals(item.name())) {
        content.put(item.leaves().getOrDefault("Metadata", "").trim(), item.leaves().getOrDefault("Use", "").trim());
      }
    }
    return attribute(dto, content);
  }

  private static Optional<Attribute> readEdt(Path file) throws IOException {
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, EdtModel.bundled());
    Map<String, String> content = new HashMap<>();
    // Использование без записи — первое значение перечисления модели, «Авто»: правило то же, что у выгрузки
    for (EdtObjectReader.EdtNode item : EdtObjectReader.read(file).list("content")) {
      content.put(item.property("metadata").trim(), item.property("use").trim());
    }
    return attribute(dto, content);
  }

  private static Optional<Attribute> attribute(MdObjectPropertiesDto dto, Map<String, String> content) {
    MdCommonAttributePropertiesDto c = dto.commonAttribute;
    if (c == null || dto.internalName == null || dto.internalName.isBlank()) {
      return Optional.empty();
    }
    // Принадлежность у обеих раскладок пишется одинаково: выгрузка — AdoptedStates.apply, EDT — свойство модели
    if (AdoptedStates.ADOPTED.equals(dto.objectBelonging)) {
      return Optional.of(new Attribute(dto.internalName, true, false, null, false, Map.of(), null, ""));
    }
    boolean separator = "SEPARATE".equals(c.dataSeparation);
    return Optional.of(new Attribute(dto.internalName, false, separator, separator ? c.separatedDataUse : null,
      "USE".equals(c.autoUse), Map.copyOf(content), c.type, dto.synonym == null ? "" : dto.synonym));
  }
}
