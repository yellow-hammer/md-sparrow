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

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Тип значения в схеме компоновки ({@code valueType} поля набора, вычисляемого поля и параметра):
 * чтение и белый словарь редактора.
 *
 * <p>Состав отдаётся формой {@link MdTypeDescriptionDto} с префиксами, нормализованными по
 * пространству имён: в файле префикс ссылочного типа свой у каждого элемента ({@code d5p1}).
 *
 * <p>Правится только то, что редактор пишет байтами платформы: примитивы, ссылки видов со
 * сгенерированным типом Ref, наборы «любая ссылка», характеристики и один определяемый тип с
 * известным именем. Прочие типы переносятся эхом.
 */
public final class DcsValueTypes {

  /**
   * Виды объектов со ссылочным типом, в порядке словаря.
   *
   * <p>У журнала документов типа Ref нет. Точка маршрута бизнес-процесса в словарь не входит
   * и переносится эхом.
   */
  public static final List<String> REF_KINDS = List.of(
    "Catalog",
    "Document",
    "Enum",
    "ChartOfCharacteristicTypes",
    "ChartOfAccounts",
    "ChartOfCalculationTypes",
    "ExchangePlan",
    "BusinessProcess",
    "Task");

  /** Примитивы словаря и вид их квалификатора; пусто — квалификаторов у типа нет. */
  public static final Map<String, String> PRIMITIVE_QUALIFIERS = primitiveQualifiers();

  /** «Любая ссылка» выгрузки конфигуратора. */
  public static final String ANY_REF_DESIGNER = "cfg:AnyIBRef";

  /** «Любая ссылка» проекта 1С:EDT и выгрузки конфигуратора до формата 2.16. */
  public static final String ANY_REF_EDT = "cfg:AnyRef";

  private static final String CONFIG_PREFIX = "cfg:";

  /** Ссылка конкретного объекта: {@code cfg:CatalogRef.Валюты}. */
  private static final Pattern REF_TYPE = Pattern.compile(
    "cfg:(" + String.join("|", REF_KINDS) + ")Ref\\.[^.\\s]+");

  /** «Любая ссылка вида»: {@code cfg:CatalogRef}. */
  private static final Pattern REF_TYPE_SET = Pattern.compile(
    "cfg:(" + String.join("|", REF_KINDS) + ")Ref");

  /** Характеристики плана видов характеристик: {@code cfg:Characteristic.ВидыСубконто}. */
  private static final Pattern CHARACTERISTIC = Pattern.compile("cfg:Characteristic\\.[^.\\s]+");

  /** Сгенерированный тип определяемого типа в выгрузке конфигуратора и его идентификатор. */
  private static final Pattern DESIGNER_DEFINED_TYPE_ID = Pattern.compile(
    "<(?:[\\w.-]+:)?GeneratedType\\b[^>]*\\bcategory=\"DefinedType\"[^>]*>\\s*"
      + "<(?:[\\w.-]+:)?TypeId>\\s*([^<\\s]+)\\s*</");

  /** Тип-контейнер определяемого типа в проекте 1С:EDT: тот же UUID атрибутом. */
  private static final Pattern EDT_DEFINED_TYPE_ID = Pattern.compile(
    "<containerType\\b[^>]*\\btypeId=\"([^\"]+)\"");

  private static final String DEFINED_TYPES_DIR = "DefinedTypes";

  /** Тип определяемого типа в выгрузке конфигуратора: первый {@code <Type>} его свойств. */
  private static final Pattern DESIGNER_DEFINED_TYPE_BODY = Pattern.compile("(?s)<Properties>.*?<Type>(.*?)</Type>");

  /** Объявление пространства имён в открывающем теге, в том числе пространство по умолчанию. */
  private static final Pattern ANY_NAMESPACE = Pattern.compile("\\sxmlns(?::[\\w.-]+)?=\"[^\"]*\"");

  private DcsValueTypes() {
  }

  private static Map<String, String> primitiveQualifiers() {
    Map<String, String> out = new LinkedHashMap<>();
    out.put("xs:string", "string");
    out.put("xs:decimal", "number");
    out.put("xs:dateTime", "date");
    out.put("xs:boolean", "");
    out.put("v8:StandardPeriod", "");
    out.put("v8:UUID", "");
    return Collections.unmodifiableMap(out);
  }

  /**
   * Контекст чтения одной схемы: формат файла и выгрузка конфигурации над ним.
   *
   * <p>Составы определяемых типов читает {@link TypeCompositions} лениво, с кэшем на контекст.
   * Записи ключа {@code typeCompositions} копит {@link #collectCompositions}.
   */
  public static final class Context {

    private final boolean edt;
    private final Path configuration;
    private final String anyRef;
    private final TypeCompositions compositions;

    private Context(boolean edt, Path configuration, SchemaVersion version) {
      this.edt = edt;
      this.configuration = configuration;
      // До 8.3.23 Конфигуратор хранит AnyRef и при загрузке молча удаляет AnyIBRef.
      this.anyRef = edt || version.compareTo(SchemaVersion.V2_16) < 0 ? ANY_REF_EDT : ANY_REF_DESIGNER;
      this.compositions = TypeCompositions.ofConfiguration(configuration);
    }

    /**
     * @param templateFile файл схемы: {@code Ext/Template.xml} выгрузки или {@code Template.dcs} EDT
     * @param version формат файла схемы
     * @return контекст с найденной над файлом конфигурацией, если она есть
     */
    public static Context of(Path templateFile, SchemaVersion version) {
      return new Context(
        templateFile != null && EdtLayout.isSchemaFile(templateFile.toString()),
        ConfigurationLanguage.configurationFileOf(templateFile), version);
    }

    /** Схема проекта 1С:EDT ({@code Template.dcs}). */
    public boolean edt() {
      return edt;
    }

    /** Описание конфигурации над схемой; {@code null} — схема вне выгрузки. */
    public Path configuration() {
      return configuration;
    }

    /** Текст «любой ссылки» в формате файла схемы. */
    public String anyRef() {
      return anyRef;
    }

    /** Имя определяемого типа по UUID сгенерированного типа; {@code null} — не найден. */
    String definedTypeName(String typeId) {
      return compositions.definedTypeName(typeId);
    }

    /**
     * Состав определяемого типа по UUID сгенерированного типа, теми же текстами, что в
     * {@code valueType} этой схемы.
     *
     * <p>Схема хранит только UUID, а состав нужен для сверки значения параметра.
     *
     * @return {@code null}, если определяемый тип или его описание не найдены
     */
    MdTypeDescriptionDto definedTypeComposition(String typeId) {
      return schemaForm(compositions.ofDefinedTypeId(typeId));
    }

    /**
     * Состав определяемого типа по имени формой {@code valueType} ответа {@code cf-dcs-info}:
     * {@code types} и {@code typeSets} как у {@link #definedTypeComposition}, пустой {@code typeIds}
     * и квалификаторы описания. Без {@code editable}: состав правится в конфигураторе.
     *
     * @param name имя определяемого типа
     * @param version формат выгрузки для чтения квалификаторов; {@code null}: без квалификаторов
     * @return {@code null}, если выгрузки или описания определяемого типа нет
     */
    Map<String, Object> definedTypeValueType(String name, SchemaVersion version) {
      if (configuration == null || name == null) {
        return null;
      }
      MdTypeDescriptionDto composition = schemaForm(compositions.ofTypeSet(TypeCompositions.DEFINED_TYPE_PREFIX + name));
      if (composition == null) {
        return null;
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", new ArrayList<>(composition.types));
      out.put("typeSets", new ArrayList<>(composition.typeSets));
      out.put("typeIds", new ArrayList<>());
      MdTypeDescriptionDto described = described(name, version);
      if (described != null) {
        putQualifiers(out, described);
      }
      return out;
    }

    /**
     * Отмечает для ключа {@code typeCompositions} составы прочитанного типа: {@code cfg:DefinedType.X}
     * и {@code cfg:Characteristic.X} из {@code typeSets} и UUID из {@code typeIds}.
     *
     * <p>{@code valueType} не меняется: его форму принимает писатель типов, лишний ключ сломал бы эхо.
     *
     * @param valueType тип формой {@code cf-dcs-info} ({@link DcsValueTypes#read})
     */
    void collectCompositions(Map<String, Object> valueType) {
      if (configuration == null || valueType == null) {
        return;
      }
      if (valueType.get("typeSets") instanceof List<?> typeSets) {
        for (Object typeSet : typeSets) {
          if (typeSet instanceof String text && (text.startsWith(TypeCompositions.DEFINED_TYPE_PREFIX)
            || text.startsWith(TypeCompositions.CHARACTERISTIC_PREFIX))) {
            compositions.ofTypeSet(text);
          }
        }
      }
      if (valueType.get("typeIds") instanceof List<?> typeIds) {
        for (Object typeId : typeIds) {
          if (typeId instanceof String text) {
            compositions.ofDefinedTypeId(text);
          }
        }
      }
    }

    /**
     * Записи ключа {@code typeCompositions} в порядке первого вхождения и в записи этой схемы:
     * {@code source} (UUID из {@code typeIds} с {@code name} определяемого типа или набор
     * {@code cfg:*} из {@code typeSets}), {@code types}, {@code typeSets}.
     *
     * <p>Записи дают все составы, раскрытые контекстом; {@code cf-dcs-info} раскрывает их только
     * через {@link #collectCompositions}, поэтому лишних записей в ответе нет.
     *
     * @return пусто, если составов не встретилось или они не найдены
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> typeCompositions() {
      List<Map<String, Object>> rows = compositions.collected();
      if (!ANY_REF_DESIGNER.equals(anyRef)) {
        for (Map<String, Object> row : rows) {
          // collected() отдаёт свежие списки: правка записи кэш класса не трогает
          anyRefOfSchema((List<String>) row.get("typeSets"));
        }
      }
      return rows;
    }

    /**
     * Состав в записи схемы: {@link TypeCompositions} отдаёт «любую ссылку» в записи нового
     * конфигуратора, а у проекта EDT и старой выгрузки используется {@code cfg:AnyRef}.
     */
    private MdTypeDescriptionDto schemaForm(MdTypeDescriptionDto composition) {
      if (composition != null && !ANY_REF_DESIGNER.equals(anyRef)) {
        anyRefOfSchema(composition.typeSets);
      }
      return composition;
    }

    private void anyRefOfSchema(List<String> typeSets) {
      typeSets.replaceAll(typeSet -> ANY_REF_DESIGNER.equals(typeSet) ? anyRef : typeSet);
    }

    /** Файл описания определяемого типа: {@code DefinedTypes/<Имя>.xml} выгрузки или {@code .mdo} EDT. */
    private Path definedTypeFile(String name) {
      Path directory = definedTypesDirectory(configuration);
      return EdtLayout.isObjectFile(configuration)
        ? directory.resolve(name).resolve(name + ".mdo")
        : directory.resolve(name + ".xml");
    }

    /**
     * Описание типа определяемого типа с квалификаторами. В EDT читается метамоделью: она знает
     * умолчания квалификаторов, которых 1С:EDT в файл не пишет.
     *
     * @return {@code null}, если описание не читается
     */
    private MdTypeDescriptionDto described(String name, SchemaVersion version) {
      Path file = definedTypeFile(name);
      try {
        if (EdtLayout.isObjectFile(configuration)) {
          return EdtObjectProperties.readType(file, "type", EdtModel.bundled());
        }
        if (version == null) {
          return null;
        }
        return designerTypeDescription(Files.readString(file, StandardCharsets.UTF_8), version);
      } catch (IOException | RuntimeException e) {
        // Квалификаторы необязательны: состав отдаётся и без них
        return null;
      }
    }
  }

  /**
   * Тип определяемого типа из описания конфигуратора ({@code Properties/Type}) моделью формата,
   * как {@code valueType} в {@code cf-dcs-info}: квалификаторы получают те же имена констант.
   *
   * <p>Объявления пространств имён корня переносятся на элемент: тексты типов это QName.
   *
   * @return {@code null}, если элемента нет
   */
  private static MdTypeDescriptionDto designerTypeDescription(String text, SchemaVersion version) {
    Matcher body = DESIGNER_DEFINED_TYPE_BODY.matcher(text);
    int root = text.indexOf("<MetaDataObject");
    if (!body.find() || root < 0) {
      return null;
    }
    StringBuilder declarations = new StringBuilder();
    Matcher declared = ANY_NAMESPACE.matcher(text.substring(root, XmlLines.startTagEnd(text, root) + 1));
    while (declared.find()) {
      declarations.append(declared.group());
    }
    String fragment = "<Type" + declarations + ">" + body.group(1) + "</Type>";
    try {
      Class<?> typeClass = Class.forName("io.github.yellowhammer.designerxml.jaxb."
        + version.name().toLowerCase(Locale.ROOT) + ".v8_1_data_core.TypeDescription");
      JAXBElement<?> parsed = version.jaxbContext().createUnmarshaller()
        .unmarshal(new StreamSource(new StringReader(fragment)), typeClass);
      return MdTypeDescriptionBridge.read(parsed.getValue());
    } catch (ClassNotFoundException | JAXBException e) {
      throw new IllegalStateException("тип определяемого типа не читается моделью: " + e.getMessage(), e);
    }
  }

  /** Квалификаторы описания типа ключами {@code valueType}: только записанные. */
  private static void putQualifiers(Map<String, Object> out, MdTypeDescriptionDto dto) {
    if (dto.stringQualifiers != null) {
      Map<String, Object> q = new LinkedHashMap<>();
      putIfPresent(q, "length", dto.stringQualifiers.length);
      putIfPresent(q, "allowedLength", dto.stringQualifiers.allowedLength);
      out.put("stringQualifiers", q);
    }
    if (dto.numberQualifiers != null) {
      Map<String, Object> q = new LinkedHashMap<>();
      putIfPresent(q, "digits", dto.numberQualifiers.digits);
      putIfPresent(q, "fractionDigits", dto.numberQualifiers.fractionDigits);
      putIfPresent(q, "allowedSign", dto.numberQualifiers.allowedSign);
      out.put("numberQualifiers", q);
    }
    if (dto.dateQualifiers != null) {
      Map<String, Object> q = new LinkedHashMap<>();
      putIfPresent(q, "dateFractions", dto.dateQualifiers.dateFractions);
      out.put("dateQualifiers", q);
    }
    if (dto.binaryDataQualifiers != null) {
      Map<String, Object> q = new LinkedHashMap<>();
      putIfPresent(q, "length", dto.binaryDataQualifiers.length);
      putIfPresent(q, "allowedLength", dto.binaryDataQualifiers.allowedLength);
      out.put("binaryDataQualifiers", q);
    }
  }

  /** Каталог {@code DefinedTypes} выгрузки или проекта EDT для описания конфигурации. */
  private static Path definedTypesDirectory(Path configuration) {
    return TypeCompositions.objectsDirectory(configuration, DEFINED_TYPES_DIR);
  }

  /**
   * Определяемые типы выгрузки по имени: имя → UUID сгенерированного типа.
   *
   * <p>UUID с именем связывает только файл определяемого типа.
   *
   * @param configuration {@code Configuration.xml} выгрузки либо {@code Configuration.mdo} проекта EDT
   * @return имена с идентификаторами; пусто, если определяемых типов нет
   */
  public static Map<String, String> definedTypes(Path configuration) {
    boolean edt = EdtLayout.isObjectFile(configuration);
    Path directory = definedTypesDirectory(configuration);
    Map<String, String> out = new LinkedHashMap<>();
    if (!Files.isDirectory(directory)) {
      return out;
    }
    List<Path> files = new ArrayList<>();
    try (var entries = Files.list(directory)) {
      entries.forEach(entry -> {
        String name = entry.getFileName().toString();
        if (edt && Files.isDirectory(entry)) {
          files.add(entry.resolve(name + ".mdo"));
        } else if (!edt && name.endsWith(".xml")) {
          files.add(entry);
        }
      });
    } catch (IOException e) {
      return out;
    }
    Collections.sort(files);
    for (Path file : files) {
      String typeId = definedTypeId(file, edt);
      if (typeId != null) {
        String name = file.getFileName().toString();
        out.put(name.substring(0, name.lastIndexOf('.')), typeId);
      }
    }
    return out;
  }

  /** UUID сгенерированного типа из файла определяемого типа; {@code null} — файла или записи нет. */
  private static String definedTypeId(Path file, boolean edt) {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    String text;
    try {
      text = Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
    Matcher matcher = (edt ? EDT_DEFINED_TYPE_ID : DESIGNER_DEFINED_TYPE_ID).matcher(text);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /**
   * Тип значения для JSON {@code cf-dcs-info}.
   *
   * @param typeDescription узел {@code v8:TypeDescription} модели или {@code null}
   * @param context формат файла и выгрузка над ним
   * @param ownerReadOnlyReason причина, по которой сам владелец типа не правится (поле
   *   вложенного набора объединения); {@code null} — владелец правится
   * @return {@code types}, {@code typeSets}, {@code typeIds}, имена {@code definedTypes},
   *   квалификаторы, {@code editable} и {@code readOnlyReason}; {@code null}, если типа нет
   */
  public static Map<String, Object> read(Object typeDescription, Context context, String ownerReadOnlyReason) {
    MdTypeDescriptionDto dto = MdTypeDescriptionBridge.read(typeDescription);
    if (dto == null) {
      return null;
    }
    List<String> typeIds = dto.typeIds == null ? List.of() : dto.typeIds;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("types", new ArrayList<>(dto.types));
    out.put("typeSets", new ArrayList<>(dto.typeSets));
    out.put("typeIds", new ArrayList<>(typeIds));
    List<String> definedNames = definedNames(typeIds, context);
    if (definedNames != null) {
      out.put("definedTypes", definedNames);
    }
    putQualifiers(out, dto);
    String reason = readOnlyReason(dto, typeIds, definedNames, context);
    if (reason == null) {
      reason = ownerReadOnlyReason;
    }
    out.put("editable", reason == null);
    if (reason != null) {
      out.put("readOnlyReason", reason);
    }
    return out;
  }

  /**
   * Имена определяемых типов по порядку идентификаторов.
   *
   * @return {@code null}, если идентификаторов нет, выгрузка не найдена или хоть один
   *   идентификатор неизвестен: частичный список читатель сопоставил бы не с тем UUID
   */
  private static List<String> definedNames(List<String> typeIds, Context context) {
    if (typeIds.isEmpty() || context.configuration() == null) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (String typeId : typeIds) {
      String name = context.definedTypeName(typeId);
      if (name == null) {
        return null;
      }
      names.add(name);
    }
    return names;
  }

  /** Почему тип не правится редактором; {@code null} — тип целиком в белом словаре. */
  private static String readOnlyReason(
    MdTypeDescriptionDto dto, List<String> typeIds, List<String> definedNames, Context context) {
    for (String type : dto.types) {
      if (!isDictionaryType(type)) {
        return "тип «" + type + "» правится в конфигураторе";
      }
    }
    for (String typeSet : dto.typeSets) {
      if (!isDictionaryTypeSet(typeSet, context.anyRef())) {
        return "набор типов «" + typeSet + "» правится в конфигураторе";
      }
    }
    if (!typeIds.isEmpty()) {
      if (context.configuration() == null) {
        return "определяемый тип «" + typeIds.get(0)
          + "» распознаётся только у макета в выгрузке конфигурации";
      }
      if (definedNames == null) {
        return "определяемый тип «" + unknownTypeId(typeIds, context)
          + "» не найден в выгрузке конфигурации";
      }
      // Определяемый тип в составном типе платформа не пишет: байтов платформы для записи нет
      if (typeIds.size() > 1 || !dto.types.isEmpty() || !dto.typeSets.isEmpty()) {
        return "определяемый тип в составном типе правится в конфигураторе";
      }
    }
    if (dto.binaryDataQualifiers != null) {
      return "квалификаторы двоичных данных правятся в конфигураторе";
    }
    return null;
  }

  private static String unknownTypeId(List<String> typeIds, Context context) {
    for (String typeId : typeIds) {
      if (context.definedTypeName(typeId) == null) {
        return typeId;
      }
    }
    return typeIds.get(0);
  }

  /**
   * Тип ({@code v8:Type}) из белого словаря: примитив или ссылка объекта вида со ссылочным типом.
   *
   * @param type текст типа с нормализованным префиксом
   */
  public static boolean isDictionaryType(String type) {
    return type != null && (PRIMITIVE_QUALIFIERS.containsKey(type) || REF_TYPE.matcher(type).matches());
  }

  /**
   * Набор типов ({@code v8:TypeSet}) из белого словаря.
   *
   * @param typeSet текст набора с нормализованным префиксом
   * @param anyRef запись «любой ссылки» для этого формата
   */
  static boolean isDictionaryTypeSet(String typeSet, String anyRef) {
    if (typeSet == null || !typeSet.startsWith(CONFIG_PREFIX)) {
      return false;
    }
    return typeSet.equals(anyRef)
      || REF_TYPE_SET.matcher(typeSet).matches()
      || CHARACTERISTIC.matcher(typeSet).matches();
  }

  private static void putIfPresent(Map<String, Object> row, String key, String value) {
    if (value != null) {
      row.put(key, value);
    }
  }
}
