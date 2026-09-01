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
package io.github.yellowhammer.designerxml.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.yellowhammer.designerxml.cf.EnumValueLabels;
import io.github.yellowhammer.designerxml.cf.ExchangePlanContentFile;
import io.github.yellowhammer.designerxml.cf.SubsystemCommandInterfaceFile;
import io.github.yellowhammer.designerxml.cf.DcsRead;
import io.github.yellowhammer.designerxml.cf.RoleRightsFile;
import io.github.yellowhammer.designerxml.cf.SupportRules;
import io.github.yellowhammer.designerxml.cf.UiLabels;
import io.github.yellowhammer.designerxml.cf.CatalogFormDto;
import io.github.yellowhammer.designerxml.cf.CfDumpValidation;
import io.github.yellowhammer.designerxml.cf.CatalogFormEdit;
import io.github.yellowhammer.designerxml.cf.ConfigurationCatalogLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationChildObjectLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationRefTypeLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormContentRead;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.edt.EdtConfigurationLists;
import io.github.yellowhammer.edt.EdtConfigurationProperties;
import io.github.yellowhammer.edt.EdtExchangePlanContent;
import io.github.yellowhammer.edt.EdtSubsystemCommandInterface;
import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectOpen;
import io.github.yellowhammer.edt.EdtObjectProperties;
import io.github.yellowhammer.edt.EdtObjectStructure;
import io.github.yellowhammer.edt.EdtPropertyEnums;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertyEnums;
import io.github.yellowhammer.designerxml.cf.MdObjectOpen;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphDto;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;
import io.github.yellowhammer.designerxml.cf.ProjectSourceDirs;
import io.github.yellowhammer.designerxml.cf.StandardCommandLabels;
import io.github.yellowhammer.designerxml.cf.SubsystemTreeBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Map;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Read-команды метаданных по параметрам из UTF-8 JSON-файла; результат — JSON в stdout.
 *
 * <p>Назначение и причины — см. {@link CliParams}: канал обходит искажение не-ASCII путей в
 * {@code argv} процесса JVM на Windows, передавая пути через UTF-8 JSON. На stdout печатается
 * тот же JSON, что и у соответствующих одиночных read-подкоманд.
 */
@Command(
  name = "read-json",
  description =
    "Прочитать метаданные (свойства/структура/дерево/граф/списки) по параметрам из UTF-8 JSON-файла; "
      + "результат — JSON в stdout (надёжный обход искажения не-ASCII путей в argv JVM на Windows)."
)
final class ReadJsonCmd implements Callable<Integer> {

  @Option(
    names = "--params",
    required = true,
    description = "Путь к UTF-8 JSON с параметрами (поле op и поля операции). Сам путь должен быть ASCII."
  )
  Path paramsFile;

  @Override
  public Integer call() {
    CliParams p;
    try {
      p = CliParams.read(paramsFile);
    } catch (JsonSyntaxException e) {
      System.err.println("некорректный JSON параметров: " + e.getMessage());
      return 2;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 2;
    } catch (IOException e) {
      System.err.println("не удалось прочитать файл параметров: " + e.getMessage());
      return 2;
    }
    try {
      System.out.println(dispatch(p));
      return 0;
    } catch (Exception e) {
      System.err.println(e.getMessage());
      return 2;
    }
  }

  /**
   * Выполняет read-операцию из {@code p.op}, переиспользуя сервисы чтения.
   *
   * @return JSON для stdout
   */
  /**
   * Что умеем читать в формате 1С:EDT.
   *
   * Остальное - формы, схемы компоновки, правила поставки и внешние обработки:
   * это отдельные форматы, и молчать о них нельзя, иначе вызывающая программа
   * получит пустой ответ вместо объяснения.
   */
  private static final java.util.Set<String> EDT_OPERATIONS = java.util.Set.of(
    "project-metadata-tree",
    "cf-md-graph",
    "cf-md-object-get",
    "cf-md-object-structure-get",
    "cf-md-object-enums",
    "cf-md-object-open-get",
    "cf-md-subsystem-tree",
    "cf-configuration-properties-get",
    "cf-list-catalogs",
    "cf-list-child-objects",
    "cf-list-all-child-objects",
    "cf-list-ref-types",
    "cf-role-rights-get",
    "cf-md-exchange-plan-content-get",
    "cf-md-subsystem-command-interface-get",
    "cf-support-object-get",
    "cf-enum-labels");

  /** Отказывает в чтении того, чего в формате 1С:EDT ещё не умеем. */
  private static void refuseEdtRead(CliParams p) {
    boolean edt = EdtLayout.isObjectFile(p.objectXml) || EdtLayout.isObjectFile(p.configurationXml);
    if (edt && !EDT_OPERATIONS.contains(p.op)) {
      throw new IllegalArgumentException(
        "Чтение \"" + p.op + "\" в формате 1С:EDT пока не поддержано.");
    }
  }

  private static String dispatch(CliParams p) throws Exception {
    // Правила поддержки учитываются, пока вызывающая программа не сказала иначе
    SupportRules.setEnforced(!p.ignoreSupport);
    refuseEdtRead(p);
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    switch (p.op) {
      case "cf-md-object-get": {
        java.nio.file.Path objectFile = p.reqPath(p.objectXml, "objectXml");
        // Формат виден по файлу: у проекта EDT свой объект и своя версия схем
        MdObjectPropertiesDto dto = EdtLayout.isObjectFile(objectFile)
          ? EdtObjectProperties.readDto(objectFile, EdtModel.bundled())
          : MdObjectPropertiesEdit.readDto(objectFile, p.version());
        return gson.toJson(dto);
      }
      case "cf-enum-labels": {
        // Значения формата и подписи платформы одним словарём: у потребителя своих копий нет
        java.util.Map<String, Object> labels = new java.util.LinkedHashMap<>();
        labels.put("values", EnumValueLabels.all());
        labels.put("byProperty", EnumValueLabels.byProperty());
        labels.put("rights", UiLabels.rights());
        labels.put("commandGroups", UiLabels.commandGroups());
        labels.put("objectStandardCommands", UiLabels.objectStandardCommands());
        labels.put("objectKinds", UiLabels.objectKinds());
        return gson.toJson(labels);
      }
      case "cf-md-object-enums": {
        // Без версии формата словарь спрашивают для проекта EDT: у него значения свои
        return gson.toJson(p.schemaVersion == null || p.schemaVersion.isBlank()
          ? EdtPropertyEnums.all(EdtModel.bundled())
          : MdObjectPropertyEnums.forVersion(p.version()));
      }
      case "cf-md-object-structure-get": {
        java.nio.file.Path structureFile = p.reqPath(p.objectXml, "objectXml");
        MdObjectStructureDto dto = EdtLayout.isObjectFile(structureFile)
          ? EdtObjectStructure.read(structureFile, EdtModel.bundled())
          : MdObjectStructureRead.read(structureFile, p.version());
        return gson.toJson(dto);
      }
      case "cf-md-object-open-get": {
        java.nio.file.Path root = p.reqPath(p.projectRoot, "projectRoot");
        String objectPath = p.req(p.objectXml, "objectXml");
        MdObjectOpen.Target target = EdtLayout.isObjectFile(objectPath)
          ? EdtObjectOpen.resolve(root, p.req(p.type, "type"), root.resolve(objectPath))
          : MdObjectOpen.resolve(p.req(p.type, "type"), root, objectPath);
        if (target == null) {
          throw new IllegalArgumentException("для этого объекта нечего открывать");
        }
        return gson.toJson(target);
      }
      case "external-artifact-properties-get": {
        ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-configuration-properties-get": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        ConfigurationPropertiesDto dto = EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationProperties.read(configuration, EdtModel.bundled())
          : ConfigurationPropertiesEdit.read(configuration, p.version());
        return gson.toJson(dto);
      }
      case "cf-catalog-form-get": {
        CatalogFormDto dto = CatalogFormEdit.readDto(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-form-item-properties": {
        return gson.toJson(FormItemPropertyDictionary.forVersion(p.version()));
      }
      case "cf-form-standard-commands": {
        return gson.toJson(StandardCommandLabels.dto());
      }
      case "cf-form-content-get": {
        FormContentDto dto = FormContentRead.read(p.reqPath(p.formXml, "formXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-role-rights-get": {
        return gson.toJson(RoleRightsFile.read(p.reqPath(p.objectXml, "objectXml")));
      }
      case "cf-dcs-info": {
        return gson.toJson(DcsRead.info(p.reqPath(p.objectXml, "objectXml"), p.version()));
      }
      case "cf-dcs-validate": {
        return gson.toJson(DcsRead.validate(p.reqPath(p.objectXml, "objectXml"), p.version()));
      }
      case "cf-support-get": {
        java.nio.file.Path root = p.reqPath(p.configurationXml, "configurationXml").toAbsolutePath().getParent();
        SupportRules.Rules rules = SupportRules.read(root);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("vendor", rules.vendor);
        out.put("version", rules.version);
        out.put("name", rules.name);
        out.put("rulesEnabled", rules.rulesEnabled);
        out.put("vendorPayloadPresent", rules.vendorPayloadPresent);
        out.put("editingEnabled", rules.editingEnabled());
        out.put("configurationState", rules.configurationState());
        // правило самого корня: им конфигурация закрыта или открыта для правки
        out.put("rootState", SupportRules.objectState(p.reqPath(p.configurationXml, "configurationXml")));
        out.put("generationId", rules.generationId);
        out.put("objectCount", rules.modeByUuid.size());
        return gson.toJson(out);
      }
      case "cf-support-object-states": {
        // Состояния объекта и его подчинённых одним чтением: дерево красит формы и макеты
        return gson.toJson(SupportRules.statesForObject(p.reqPath(p.objectXml, "objectXml")));
      }
      case "cf-support-object-get": {
        java.nio.file.Path objectXml = p.reqPath(p.objectXml, "objectXml");
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        try {
          SupportRules.ensureEditable(objectXml);
          out.put("editable", true);
        } catch (IllegalStateException e) {
          out.put("editable", false);
          out.put("reason", e.getMessage());
        }
        // Состояние самого объекта: он бывает снят с поддержки в конфигурации на поддержке
        out.put("state", SupportRules.objectState(objectXml));
        SupportRules.Rules rules = SupportRules.rulesFor(objectXml);
        if (rules != null && !rules.isEmpty()) {
          out.put("vendor", rules.vendor);
          out.put("version", rules.version);
          out.put("configurationState", rules.configurationState());
          out.put("generationId", rules.generationId);
        }
        return gson.toJson(out);
      }
      case "cf-md-subsystem-command-interface-get": {
        java.nio.file.Path subsystemXml = p.reqPath(p.objectXml, "objectXml");
        java.util.Map<String, Object> model = SubsystemCommandInterfaceFile.toJsonModel(
          EdtLayout.isObjectFile(subsystemXml)
            ? EdtSubsystemCommandInterface.read(subsystemXml)
            : SubsystemCommandInterfaceFile.read(subsystemXml));
        // Стандартные команды состава видны и без файла настроек, как в конфигураторе
        try {
          MdObjectPropertiesDto subsystem = MdObjectPropertiesEdit.readDto(subsystemXml, p.version());
          model.put("contentCommands", SubsystemCommandInterfaceFile.contentCommands(subsystem.contentRefs));
        } catch (RuntimeException e) {
          model.put("contentCommands", java.util.List.of());
        }
        return gson.toJson(model);
      }
      case "cf-md-exchange-plan-content-get": {
        java.nio.file.Path plan = p.reqPath(p.objectXml, "objectXml");
        return gson.toJson(EdtLayout.isObjectFile(plan)
          ? EdtExchangePlanContent.read(plan)
          : ExchangePlanContentFile.read(plan));
      }
      case "cf-list-ref-types": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        return gson.toJson(EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationLists.refTypes(configuration, EdtModel.bundled())
          : ConfigurationRefTypeLister.listRefTypes(configuration, p.version()));
      }
      case "cf-list-all-child-objects": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        return gson.toJson(EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationLists.all(configuration, EdtModel.bundled())
          : ConfigurationChildObjectLister.listAll(configuration, p.version()));
      }
      case "cf-list-child-objects": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        String childTag = p.req(p.tag, "tag");
        var names = EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationLists.names(configuration, EdtModel.bundled(), childTag)
          : ConfigurationChildObjectLister.listNames(configuration, p.version(), childTag);
        return ConfigurationCatalogLister.toJsonArray(names);
      }
      case "cf-validate-dump": {
        var findings = CfDumpValidation.validate(p.reqPath(p.cfRoot, "cfRoot"));
        return gson.toJson(findings);
      }
      case "cf-list-catalogs": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        var names = EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationLists.names(configuration, EdtModel.bundled(), "Catalog")
          : ConfigurationCatalogLister.listCatalogNames(configuration, p.version());
        return ConfigurationCatalogLister.toJsonArray(names);
      }
      case "cf-md-subsystem-tree": {
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        var nodes = EdtLayout.isObjectFile(configuration)
          ? EdtConfigurationLists.subsystems(configuration, EdtModel.bundled())
          : SubsystemTreeBuilder.build(configuration, p.version());
        return gson.toJson(nodes);
      }
      case "project-metadata-tree": {
        ProjectMetadataTreeDto dto = ProjectMetadataTreeBuilder.build(
          p.reqPath(p.projectRoot, "projectRoot"),
          ProjectSourceDirs.fromNullable(p.cfDir, p.cfeDir, p.epfDir, p.erfDir));
        return gson.toJson(dto);
      }
      case "cf-md-graph": {
        ProjectMetadataGraphDto dto = ProjectMetadataGraphBuilder.build(
          p.reqPath(p.projectRoot, "projectRoot"),
          ProjectSourceDirs.fromNullable(p.cfDir, p.cfeDir, p.epfDir, p.erfDir));
        return gson.toJson(dto);
      }
      default:
        throw new IllegalArgumentException("неизвестный read op: " + p.op);
    }
  }
}
