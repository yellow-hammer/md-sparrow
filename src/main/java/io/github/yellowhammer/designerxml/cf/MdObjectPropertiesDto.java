/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Свойства объекта метаданных для JSON (cf-md-object-get/set). Поля зависят от {@link #kind}.
 *
 * <p>{@code kind}: {@code catalog} | {@code constant} | {@code enum} | {@code document} | {@code report} |
 * {@code dataProcessor} | {@code task} | {@code chartOfAccounts} | {@code chartOfCharacteristicTypes} |
 * {@code chartOfCalculationTypes} | {@code commonModule} | {@code subsystem} | {@code sessionParameter} |
 * {@code exchangePlan} | {@code commonAttribute} | {@code commonPicture} | {@code documentNumerator} |
 * {@code externalDataSource} | {@code role} | {@code eventSubscription} | {@code scheduledJob} |
 * {@code commonCommand} | {@code informationRegister} | {@code accumulationRegister} |
 * {@code documentJournal} | {@code businessProcess}.
 */
public final class MdObjectPropertiesDto {

  public String kind;
  public String internalName;
  public String synonymRu;
  public String comment;
  /** Принадлежность в расширении: {@code Adopted} у заимствованного объекта, иначе пусто. */
  public String objectBelonging;
  /**
   * Состояния свойств заимствованного объекта по именам метамодели EDT:
   * {@code Checked} контролируется, {@code Extended} изменено расширением.
   */
  public java.util.Map<String, String> propertyStates;
  /**
   * Свойства, которые расширение контролирует или меняет у заимствованного объекта, под
   * именами метамодели EDT; у своего объекта пусто. Остальные свойства принадлежат
   * расширяемой конфигурации и в расширении не правятся.
   */
  public List<String> extendable;
  public List<MdNamedPropertyDto> attributes;
  public List<MdNamedPropertyDto> tabularSections;
  /** Значения перечисления (только для kind=enum). */
  public List<MdNamedPropertyDto> enumValues;
  /** Измерения регистра. */
  public List<MdNamedPropertyDto> dimensions;
  /** Ресурсы регистра. */
  public List<MdNamedPropertyDto> resources;
  /** Команды объекта. */
  public List<MdNamedPropertyDto> commands;
  /** Графы журнала документов. */
  public List<MdNamedPropertyDto> columns;
  /** Признаки учёта плана счетов. */
  public List<MdNamedPropertyDto> accountingFlags;
  /** Признаки учёта субконто плана счетов. */
  public List<MdNamedPropertyDto> extDimensionAccountingFlags;
  /** Реквизиты адресации задачи. */
  public List<MdNamedPropertyDto> addressingAttributes;
  /** Перерасчёты регистра расчёта. */
  public List<MdNamedPropertyDto> recalculations;
  /** Операции Web-сервиса. */
  public List<MdNamedPropertyDto> operations;
  /** Шаблоны URL HTTP-сервиса. */
  public List<MdNamedPropertyDto> urlTemplates;
  /** Каналы сервиса интеграции. */
  public List<MdNamedPropertyDto> channels;
  /** Таблицы внешнего источника данных. */
  public List<MdNamedPropertyDto> tables;
  /** Кубы внешнего источника данных. */
  public List<MdNamedPropertyDto> cubes;
  /** Функции внешнего источника данных. */
  public List<MdNamedPropertyDto> functions;
  /** Подсистемы, вложенные в данную (только для kind=subsystem). */
  /**
   * Скалярные свойства вида без своего моста: имя элемента выгрузки → значение.
   * Перечислимые значения несут имена Java-констант модели, числа - строками.
   */
  public java.util.Map<String, Object> scalars;

  /** Описание скалярных свойств: тип и допустимые значения. */
  public java.util.Map<String, MdScalarPropertyMeta> scalarMeta;

  /** Состав общего реквизита: ссылки с режимом использования. */
  public java.util.List<MdContentMemberDto> contentMembers;

  /** Входящие документы последовательности: ссылки {@code Document.Имя}. */
  public List<String> documents;

  /** Движения регистров последовательности: ссылки на регистры. */
  public List<String> registerRecords;

  public List<String> nestedSubsystems;
  /**
   * Состав подсистемы (ссылки на объекты метаданных) — только чтение; при записи не изменяется.
   */
  public List<String> contentRefs;
  /** Поля {@code CatalogProperties} для {@code kind=catalog}; иначе {@code null}. */
  public MdCatalogPropertiesDto catalog;
  /** Поля {@code DocumentProperties} для {@code kind=document}; иначе {@code null}. */
  public MdDocumentPropertiesDto document;
  /** Поля {@code EnumProperties} для {@code kind=enum}; иначе {@code null}. */
  public MdEnumPropertiesDto enumeration;
  /** Поля {@code ConstantProperties} для {@code kind=constant}; иначе {@code null}. */
  public MdConstantPropertiesDto constant;
  /** Поля {@code CommonModuleProperties} для {@code kind=commonModule}; иначе {@code null}. */
  public MdCommonModulePropertiesDto commonModule;
  /**
   * Поля регистра для {@code kind=informationRegister} и {@code kind=accumulationRegister};
   * иначе {@code null}.
   */
  public MdRegisterPropertiesDto register;
  /** Поля отчёта и обработки для {@code kind=report} и {@code kind=dataProcessor}; иначе {@code null}. */
  public MdReportPropertiesDto report;
  /** Поля журнала документов для {@code kind=documentJournal}; иначе {@code null}. */
  public MdDocumentJournalPropertiesDto documentJournal;
  /** Поля плана обмена для {@code kind=exchangePlan}; иначе {@code null}. */
  public MdExchangePlanPropertiesDto exchangePlan;
  /** Поля плана видов характеристик для {@code kind=chartOfCharacteristicTypes}; иначе {@code null}. */
  public MdChartOfCharacteristicTypesPropertiesDto chartOfCharacteristicTypes;
  /** Поля задачи для {@code kind=task}; иначе {@code null}. */
  public MdTaskPropertiesDto task;
  /** Поля бизнес-процесса для {@code kind=businessProcess}; иначе {@code null}. */
  public MdBusinessProcessPropertiesDto businessProcess;
  /** Поля плана счетов для {@code kind=chartOfAccounts}; иначе {@code null}. */
  public MdChartOfAccountsPropertiesDto chartOfAccounts;
  /** Поля плана видов расчёта для {@code kind=chartOfCalculationTypes}; иначе {@code null}. */
  public MdChartOfCalculationTypesPropertiesDto chartOfCalculationTypes;

  /** Свойства параметра сеанса (kind=sessionParameter). */
  public MdSessionParameterPropertiesDto sessionParameter;

  /** Свойства нумератора документов (kind=documentNumerator). */
  public MdDocumentNumeratorPropertiesDto documentNumerator;

  /** Свойства подписки на событие (kind=eventSubscription). */
  public MdEventSubscriptionPropertiesDto eventSubscription;

  /** Свойства регламентного задания (kind=scheduledJob). */
  public MdScheduledJobPropertiesDto scheduledJob;

  /** Свойства общей команды (kind=commonCommand). */
  public MdCommonCommandPropertiesDto commonCommand;

  /** Свойства общего реквизита (kind=commonAttribute). */
  public MdCommonAttributePropertiesDto commonAttribute;

  /** Свойства общей картинки (kind=commonPicture). */
  public MdCommonPicturePropertiesDto commonPicture;

  /** Свойства роли (kind=role). */
  public MdRolePropertiesDto role;

  /** Свойства внешнего источника данных (kind=externalDataSource). */
  public MdExternalDataSourcePropertiesDto externalDataSource;

  public MdObjectPropertiesDto() {
    this.attributes = new ArrayList<>();
    this.tabularSections = new ArrayList<>();
    this.enumValues = new ArrayList<>();
    this.dimensions = new ArrayList<>();
    this.resources = new ArrayList<>();
    this.nestedSubsystems = new ArrayList<>();
    this.contentRefs = new ArrayList<>();
    this.commands = new ArrayList<>();
    this.columns = new ArrayList<>();
    this.accountingFlags = new ArrayList<>();
    this.extDimensionAccountingFlags = new ArrayList<>();
    this.addressingAttributes = new ArrayList<>();
    this.recalculations = new ArrayList<>();
    this.operations = new ArrayList<>();
    this.urlTemplates = new ArrayList<>();
    this.channels = new ArrayList<>();
    this.tables = new ArrayList<>();
    this.cubes = new ArrayList<>();
    this.functions = new ArrayList<>();
  }
}
