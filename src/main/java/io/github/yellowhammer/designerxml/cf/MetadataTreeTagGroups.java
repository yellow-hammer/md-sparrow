/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Соответствие тегов {@code ChildObjects} группам дерева конфигуратора (порядок групп — как в UI).
 */
public final class MetadataTreeTagGroups {

  public static final String GROUP_ID_COMMON = "common";

  /** Документы, нумераторы и последовательности — одна группа «Документы» с подгруппами, как в конфигураторе. */
  public static final String GROUP_ID_DOCUMENTS = "documents";

  /**
   * Подгруппы внутри «Общие»: статичный порядок как в дереве конфигуратора 1С (в т.ч. пустые узлы).
   * По одному типу на строку; в конце — типы, не попавшие на скриншот эталона, но есть в XSD.
   */
  private static final List<CommonSubgroupDef> COMMON_SUBGROUPS = List.of(
    new CommonSubgroupDef("common_subsystem", "Подсистемы", "symbol-namespace", List.of("Subsystem")),
    new CommonSubgroupDef("common_commonmodule", "Общие модули", "symbol-method", List.of("CommonModule")),
    new CommonSubgroupDef("common_sessionparam", "Параметры сеанса", "person", List.of("SessionParameter")),
    new CommonSubgroupDef("common_role", "Роли", "person", List.of("Role")),
    new CommonSubgroupDef("common_commonattribute", "Общие реквизиты", "symbol-method", List.of("CommonAttribute")),
    new CommonSubgroupDef("common_exchangeplan", "Планы обмена", "globe", List.of("ExchangePlan")),
    new CommonSubgroupDef("common_filtercriterion", "Критерии отбора", "symbol-misc", List.of("FilterCriterion")),
    new CommonSubgroupDef("common_eventsubscription", "Подписки на события", "globe", List.of("EventSubscription")),
    new CommonSubgroupDef("common_scheduledjob", "Регламентные задания", "globe", List.of("ScheduledJob")),
    new CommonSubgroupDef("common_bot", "Боты", "plug", List.of("Bot")),
    new CommonSubgroupDef("common_functionaloption", "Функциональные опции", "symbol-misc", List.of("FunctionalOption")),
    new CommonSubgroupDef(
      "common_functionaloptionsparam",
      "Параметры функциональных опций",
      "symbol-misc",
      List.of("FunctionalOptionsParameter")),
    new CommonSubgroupDef("common_definedtype", "Определяемые типы", "symbol-misc", List.of("DefinedType")),
    new CommonSubgroupDef("common_settingsstorage", "Хранилища настроек", "database", List.of("SettingsStorage")),
    new CommonSubgroupDef("common_commoncommand", "Общие команды", "terminal", List.of("CommonCommand")),
    new CommonSubgroupDef("common_commandgroup", "Группы команд", "terminal", List.of("CommandGroup")),
    new CommonSubgroupDef("common_commonform", "Общие формы", "layout", List.of("CommonForm")),
    new CommonSubgroupDef("common_commontemplate", "Общие макеты", "layout", List.of("CommonTemplate")),
    new CommonSubgroupDef("common_commonpicture", "Общие картинки", "symbol-color", List.of("CommonPicture")),
    new CommonSubgroupDef("common_xdtopackage", "XDTO-пакеты", "globe", List.of("XDTOPackage")),
    new CommonSubgroupDef("common_webservice", "Web-сервисы", "globe", List.of("WebService")),
    new CommonSubgroupDef("common_httpservice", "HTTP-сервисы", "globe", List.of("HTTPService")),
    new CommonSubgroupDef("common_interface", "Интерфейсы", "layout", List.of("Interface")),
    new CommonSubgroupDef("common_wsreference", "WS-ссылки", "globe", List.of("WSReference")),
    new CommonSubgroupDef("common_websocketclient", "WebSocket-клиенты", "plug", List.of("WebSocketClient")),
    new CommonSubgroupDef("common_integrationservice", "Сервисы интеграции", "plug", List.of("IntegrationService")),
    new CommonSubgroupDef("common_styleitem", "Элементы стиля", "symbol-color", List.of("StyleItem")),
    new CommonSubgroupDef("common_style", "Стили", "symbol-color", List.of("Style")),
    new CommonSubgroupDef("common_language", "Языки", "symbol-namespace", List.of("Language")),
    new CommonSubgroupDef("common_palettecolor", "Цвета палитры", "symbol-color", List.of("PaletteColor"))
  );

  private static final List<GroupDef> ORDERED_GROUPS = List.of(
    new GroupDef(GROUP_ID_COMMON, "Общие", "symbol-namespace", List.of()),
    new GroupDef("constants", "Константы", "symbol-numeric", List.of("Constant")),
    new GroupDef("catalogs", "Справочники", "library", List.of("Catalog")),
    new GroupDef(
      GROUP_ID_DOCUMENTS,
      "Документы",
      "file",
      List.of("Document", "DocumentNumerator", "Sequence")),
    new GroupDef("documentJournals", "Журналы документов", "book", List.of("DocumentJournal")),
    new GroupDef("enums", "Перечисления", "bracket", List.of("Enum")),
    new GroupDef("reports", "Отчёты", "graph", List.of("Report")),
    new GroupDef("dataProcessors", "Обработки", "tools", List.of("DataProcessor")),
    new GroupDef(
      "chartOfCharacteristicTypes",
      "Планы видов характеристик",
      "table",
      List.of("ChartOfCharacteristicTypes")),
    new GroupDef("chartOfAccounts", "Планы счетов", "account", List.of("ChartOfAccounts")),
    new GroupDef(
      "chartOfCalculationTypes",
      "Планы видов расчёта",
      "layers",
      List.of("ChartOfCalculationTypes")),
    new GroupDef(
      "informationRegisters",
      "Регистры сведений",
      "table",
      List.of("InformationRegister")),
    new GroupDef(
      "accumulationRegisters",
      "Регистры накопления",
      "circle-outline",
      List.of("AccumulationRegister")),
    new GroupDef(
      "accountingRegisters",
      "Регистры бухгалтерии",
      "account",
      List.of("AccountingRegister")),
    new GroupDef(
      "calculationRegisters",
      "Регистры расчёта",
      "symbol-operator",
      List.of("CalculationRegister")),
    new GroupDef("businessProcesses", "Бизнес-процессы", "git-branch", List.of("BusinessProcess")),
    new GroupDef("tasks", "Задачи", "tasklist", List.of("Task")),
    new GroupDef(
      "externalDataSources",
      "Внешние источники данных",
      "database",
      List.of("ExternalDataSource"))
  );

  private static final Map<String, String> TAG_TO_GROUP_ID = new LinkedHashMap<>();
  private static final Map<String, String> TAG_TO_COMMON_SUBGROUP_ID = new LinkedHashMap<>();

  static {
    for (CommonSubgroupDef c : COMMON_SUBGROUPS) {
      for (String t : c.tags()) {
        TAG_TO_GROUP_ID.put(t, GROUP_ID_COMMON);
        TAG_TO_COMMON_SUBGROUP_ID.put(t, c.id());
      }
    }
    for (GroupDef g : ORDERED_GROUPS) {
      if (GROUP_ID_COMMON.equals(g.id())) {
        continue;
      }
      for (String t : g.tags()) {
        TAG_TO_GROUP_ID.put(t, g.id());
      }
    }
  }

  private MetadataTreeTagGroups() {
  }

  public static List<GroupDef> orderedGroups() {
    return ORDERED_GROUPS;
  }

  /**
   * Раскладывает записи по группам; внутри группы сохраняется порядок из списка записей.
   * Группа «Общие» отдаётся с {@link MetadataTreeGroupPayload#subgroups() подгруппами}, без общего списка.
   * Все группы из {@link #orderedGroups()} попадают в результат, в том числе с пустыми списками —
   * чтобы в IDE оставались узлы для добавления метаданных.
   *
   * @throws IOException если тип объекта не отнесён ни к одной группе (несогласованность с XSD/UI)
   */
  public static List<MetadataTreeGroupPayload> buildGroups(List<ChildObjectEntry> entries)
    throws IOException {
    Map<String, List<MetadataTreeItemPayload>> byGroup = new LinkedHashMap<>();
    for (GroupDef g : ORDERED_GROUPS) {
      if (GROUP_ID_COMMON.equals(g.id())) {
        continue;
      }
      if (GROUP_ID_DOCUMENTS.equals(g.id())) {
        continue;
      }
      byGroup.put(g.id(), new ArrayList<>());
    }
    Map<String, List<MetadataTreeItemPayload>> byCommonSubgroup = new LinkedHashMap<>();
    for (CommonSubgroupDef c : COMMON_SUBGROUPS) {
      byCommonSubgroup.put(c.id(), new ArrayList<>());
    }
    List<MetadataTreeItemPayload> documentsFlat = new ArrayList<>();
    List<MetadataTreeItemPayload> documentNumerators = new ArrayList<>();
    List<MetadataTreeItemPayload> sequences = new ArrayList<>();
    for (ChildObjectEntry e : entries) {
      String gid = TAG_TO_GROUP_ID.get(e.objectType());
      if (gid == null) {
        throw new IOException(
          "Тип объекта «" + e.objectType() + "» не поддержан в дереве метаданных этой версии md-sparrow.");
      }
      var payload = new MetadataTreeItemPayload(e.objectType(), e.name());
      if (GROUP_ID_COMMON.equals(gid)) {
        String sid = TAG_TO_COMMON_SUBGROUP_ID.get(e.objectType());
        byCommonSubgroup.get(sid).add(payload);
      } else if (GROUP_ID_DOCUMENTS.equals(gid)) {
        switch (e.objectType()) {
          case "Document" -> documentsFlat.add(payload);
          case "DocumentNumerator" -> documentNumerators.add(payload);
          case "Sequence" -> sequences.add(payload);
          default -> throw new IllegalStateException("Неожиданный тип в группе документов: " + e.objectType());
        }
      } else {
        byGroup.get(gid).add(payload);
      }
    }
    List<MetadataTreeGroupPayload> out = new ArrayList<>();
    for (GroupDef g : ORDERED_GROUPS) {
      if (GROUP_ID_COMMON.equals(g.id())) {
        List<MetadataSubgroupPayload> subgroups = new ArrayList<>();
        for (CommonSubgroupDef c : COMMON_SUBGROUPS) {
          subgroups.add(new MetadataSubgroupPayload(
            c.id(),
            c.label(),
            c.iconHint(),
            List.copyOf(byCommonSubgroup.get(c.id()))));
        }
        out.add(new MetadataTreeGroupPayload(
          g.id(),
          g.label(),
          g.iconHint(),
          List.of(),
          subgroups));
      } else if (GROUP_ID_DOCUMENTS.equals(g.id())) {
        List<MetadataSubgroupPayload> docSubgroups = List.of(
          new MetadataSubgroupPayload(
            "documentNumerators",
            "Нумераторы",
            "symbol-numeric",
            List.copyOf(documentNumerators)),
          new MetadataSubgroupPayload(
            "sequences",
            "Последовательности",
            "layers",
            List.copyOf(sequences)));
        out.add(new MetadataTreeGroupPayload(
          g.id(),
          g.label(),
          g.iconHint(),
          List.copyOf(documentsFlat),
          docSubgroups));
      } else {
        List<MetadataTreeItemPayload> items = byGroup.get(g.id());
        out.add(new MetadataTreeGroupPayload(
          g.id(),
          g.label(),
          g.iconHint(),
          List.copyOf(items),
          List.of()));
      }
    }
    return out;
  }

  public record GroupDef(String id, String label, String iconHint, List<String> tags) {
  }

  private record CommonSubgroupDef(String id, String label, String iconHint, List<String> tags) {
  }

  public record MetadataTreeItemPayload(String objectType, String name) {
  }

  public record MetadataSubgroupPayload(
    String id,
    String label,
    String iconHint,
    List<MetadataTreeItemPayload> items
  ) {
  }

  public record MetadataTreeGroupPayload(
    String id,
    String label,
    String iconHint,
    List<MetadataTreeItemPayload> items,
    List<MetadataSubgroupPayload> subgroups
  ) {
  }
}
