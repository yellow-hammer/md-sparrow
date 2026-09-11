/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Структура объекта метаданных для дерева IDE.
 */
public final class MdObjectStructureDto {

  public String kind;
  public String internalName;
  public List<MdNodeDto> attributes;
  public List<MdTabularSectionDto> tabularSections;
  /** Формы объекта: вид и файл содержимого нужны, чтобы знать, чем форму открывать. */
  public List<MdFormDto> forms;
  public List<String> commands;
  /** Макеты объекта: вид и файл содержимого нужны, чтобы знать, чем макет открывать. */
  public List<MdTemplateDto> templates;
  public List<String> values;
  public List<String> columns;
  public List<String> accountingFlags;
  public List<String> extDimensionAccountingFlags;
  public List<String> dimensions;
  public List<String> resources;
  public List<String> recalculations;
  public List<String> addressingAttributes;
  /** Стандартные реквизиты объекта: платформа задаёт их сама, файл перечисляет с настройками. */
  public List<String> standardAttributes;
  /**
   * Синонимы стандартных реквизитов: имя -> синоним на языке конфигурации.
   *
   * <p>Платформа показывает на форме синоним, а не имя, и у стандартного реквизита его переопределяют
   * не реже, чем у обычного: {@code Code} у справочника валют - «Цифровой код». Без переопределения
   * берётся подпись самой платформы из {@link StandardAttributeLabels}.
   */
  public java.util.Map<String, String> standardAttributeSynonyms;
  /**
   * Синонимы команд объекта: имя -> синоним на языке конфигурации.
   *
   * <p>Кнопку без заголовка платформа подписывает синонимом команды, на которую та ссылается.
   */
  public java.util.Map<String, String> commandSynonyms;
  /**
   * Синонимы полей данных: имя -> синоним на языке конфигурации.
   *
   * <p>Измерения, ресурсы, признаки учёта и прочие узлы, которые в списках лежат одними именами.
   * Нужны колонкам динамического списка: его поля идут по именам основной таблицы.
   */
  public java.util.Map<String, String> childSynonyms;
  public List<String> operations;
  public List<String> urlTemplates;
  public List<String> channels;
  public List<String> tables;
  public List<String> cubes;
  public List<String> functions;

  public MdObjectStructureDto() {
    this.attributes = new ArrayList<>();
    this.tabularSections = new ArrayList<>();
    this.forms = new ArrayList<>();
    this.commands = new ArrayList<>();
    this.templates = new ArrayList<>();
    this.values = new ArrayList<>();
    this.columns = new ArrayList<>();
    this.accountingFlags = new ArrayList<>();
    this.extDimensionAccountingFlags = new ArrayList<>();
    this.dimensions = new ArrayList<>();
    this.resources = new ArrayList<>();
    this.recalculations = new ArrayList<>();
    this.addressingAttributes = new ArrayList<>();
    this.standardAttributes = new ArrayList<>();
    this.standardAttributeSynonyms = new java.util.LinkedHashMap<>();
    this.commandSynonyms = new java.util.LinkedHashMap<>();
    this.childSynonyms = new java.util.LinkedHashMap<>();
    this.operations = new ArrayList<>();
    this.urlTemplates = new ArrayList<>();
    this.channels = new ArrayList<>();
    this.tables = new ArrayList<>();
    this.cubes = new ArrayList<>();
    this.functions = new ArrayList<>();
  }

  /**
   * Форма объекта.
   *
   * <p>Управляемую форму расширение показывает и правит, обычная хранится в
   * своём файле, который читает только платформа. Вид отличает одно от другого,
   * а имя файла берётся с диска.
   */
  public static final class MdFormDto {
    public String name;
    /** Вид формы именем константы модели: {@code MANAGED} либо {@code ORDINARY}. */
    public String formType;
    /** Файл содержимого от каталога объекта; пусто, если файла рядом нет. */
    public String contentFile;

    public MdFormDto() {
    }

    public MdFormDto(String name, String formType, String contentFile) {
      this.name = name;
      this.formType = formType;
      this.contentFile = contentFile;
    }
  }

  /**
   * Макет объекта.
   *
   * <p>Содержимое лежит своим файлом рядом с макетом, и его имя зависит от вида:
   * табличный документ, схема компоновки и текст пишутся в разные файлы, а часть
   * видов хранится двоично. Имя файла не вычисляется по виду, а берётся с диска:
   * так работают и виды, которых мы ещё не видели.
   */
  public static final class MdTemplateDto {
    public String name;
    /** Вид макета именем константы модели: {@code SPREADSHEET_DOCUMENT}, {@code DATA_COMPOSITION_SCHEMA}. */
    public String templateType;
    /** Файл содержимого от каталога объекта; пусто, если файла рядом нет. */
    public String contentFile;
    /** Содержимое двоичное: показывать его нечем. */
    public boolean binaryContent;

    public MdTemplateDto() {
    }

    public MdTemplateDto(String name, String templateType, String contentFile, boolean binaryContent) {
      this.name = name;
      this.templateType = templateType;
      this.contentFile = contentFile;
      this.binaryContent = binaryContent;
    }
  }

  public static final class MdNodeDto {
    public String name;
    @LocalString
    public String synonym;
    public String comment;
    /**
     * Тип значения реквизита.
     *
     * <p>Поле формы по пути к данным объекта платформа рисует по типу реквизита: ссылочному она
     * даёт кнопку выбора, дате - календарь, а ширину поля берёт из квалификаторов.
     */
    public MdTypeDescriptionDto type;

    public MdNodeDto() {
    }

    public MdNodeDto(String name, String synonym, String comment) {
      this.name = name;
      this.synonym = synonym;
      this.comment = comment;
    }
  }

  public static final class MdTabularSectionDto {
    public String name;
    @LocalString
    public String synonym;
    public String comment;
    public List<MdNodeDto> attributes;
    /** Стандартные реквизиты табличной части: у любой из них есть {@code LineNumber}. */
    public List<String> standardAttributes;
    /** Синонимы стандартных реквизитов табличной части: имя -> синоним на языке конфигурации. */
    public java.util.Map<String, String> standardAttributeSynonyms;

    public MdTabularSectionDto() {
      this.attributes = new ArrayList<>();
      this.standardAttributes = new ArrayList<>();
      this.standardAttributeSynonyms = new java.util.LinkedHashMap<>();
    }
  }
}
