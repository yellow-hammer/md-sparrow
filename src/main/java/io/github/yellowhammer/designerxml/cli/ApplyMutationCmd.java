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
import com.google.gson.JsonSyntaxException;
import io.github.yellowhammer.designerxml.cf.CfLayout;
import io.github.yellowhammer.designerxml.cf.CfMdObjectMutations;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.EmptyCfScaffold;
import io.github.yellowhammer.designerxml.cf.CfeBorrow;
import io.github.yellowhammer.designerxml.cf.ExchangePlanContentFile;
import io.github.yellowhammer.designerxml.cf.FormScaffold;
import io.github.yellowhammer.designerxml.cf.SupportRules;
import io.github.yellowhammer.designerxml.cf.DcsRead;
import io.github.yellowhammer.designerxml.cf.RoleRightsFile;
import io.github.yellowhammer.designerxml.cf.SubsystemCommandInterfaceFile;
import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
import io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactMutations;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyChangeDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectAdd;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.MdObjectChildMutations;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.NewExternalArtifactXml;
import jakarta.xml.bind.JAXBException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Команды-изменения метаданных по параметрам из UTF-8 JSON-файла (мутации, set-операции, scaffold).
 *
 * <p>Назначение и причины — см. {@link CliParams}: канал обходит искажение не-ASCII значений в
 * {@code argv} процесса JVM на Windows, передавая пути и имена через UTF-8 JSON.
 */
@Command(
  name = "apply-mutation",
  description =
    "Выполнить изменение метаданных (мутация/set/scaffold) по параметрам из UTF-8 JSON-файла "
      + "(надёжный обход искажения не-ASCII аргументов в argv JVM на Windows)."
)
final class ApplyMutationCmd implements Callable<Integer> {

  /** Полезная нагрузка cf-role-rights-set: правки прав и флаги по умолчанию. */
  static final class RoleRightsPayload {
    java.util.List<RoleRightsFile.Edit> edits;
    java.util.Map<String, Boolean> flags;
  }

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
    } catch (IllegalArgumentException | IllegalStateException e) {
      // Отказ записи - это сообщение вызывающей программе, а не сбой: стек ей не нужен.
      System.err.println(e.getMessage());
      return 2;
    } catch (IOException | JAXBException e) {
      System.err.println(e.getMessage());
      return 2;
    }
  }

  /**
   * Выполняет операцию из {@code p.op}, переиспользуя сервисы изменения.
   *
   * @return текст для stdout (как у одиночных подкоманд: {@code OK} либо имя созданного объекта)
   */
  private static String dispatch(CliParams p) throws IOException, JAXBException {
    switch (p.op) {
      case "cf-md-object-delete":
        CfMdObjectMutations.delete(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.name, "name"));
        return "OK";
      case "cf-md-object-rename":
        CfMdObjectMutations.rename(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.oldName, "oldName"),
          p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-object-duplicate":
        CfMdObjectMutations.duplicate(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.sourceName, "sourceName"),
          p.req(p.newName, "newName"));
        return "OK";

      case "cf-md-attribute-add":
        MdObjectChildMutations.addAttribute(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-command-add":
        MdObjectChildMutations.addCommand(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-command-rename":
        MdObjectChildMutations.renameCommand(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-command-delete":
        MdObjectChildMutations.deleteCommand(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-command-reorder":
        MdObjectChildMutations.reorderCommands(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";
      case "cf-md-attribute-rename":
        MdObjectChildMutations.renameAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-attribute-delete":
        MdObjectChildMutations.deleteAttribute(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-attribute-duplicate":
        MdObjectChildMutations.duplicateAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";

      case "cf-md-subsystem-command-visibility-set": {
        java.util.List<SubsystemCommandInterfaceFile.CommandEntry> entries = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<
            java.util.List<SubsystemCommandInterfaceFile.CommandEntry>>() { }.getType());
        SubsystemCommandInterfaceFile.writeVisibility(
          p.reqPath(p.objectXml, "objectXml"), p.version(), entries);
        return "OK";
      }
      case "cf-md-exchange-plan-content-set": {
        java.util.List<MdContentMemberDto> members = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<java.util.List<MdContentMemberDto>>() { }.getType());
        ExchangePlanContentFile.write(p.reqPath(p.objectXml, "objectXml"), p.version(), members);
        return "OK";
      }
      case "cf-dcs-set-query":
        DcsRead.setQuery(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.tag, p.req(p.payloadJson, "payloadJson"));
        return "OK";
      case "cf-dcs-add-calculated-field": {
        java.util.Map<String, String> field = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<java.util.Map<String, String>>() { }.getType());
        DcsRead.addCalculatedField(
          p.reqPath(p.objectXml, "objectXml"), p.version(),
          field.get("dataPath"), field.get("expression"), field.get("title"));
        return "OK";
      }
      case "cf-role-rights-set": {
        // payload: {"edits":[{object,right,value}...],"flags":{имяФлага:значение}}
        RoleRightsPayload payload = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"), RoleRightsPayload.class);
        java.nio.file.Path roleXml = p.reqPath(p.objectXml, "objectXml");
        RoleRightsFile.applyFlags(roleXml, payload.flags);
        if (payload.edits != null && !payload.edits.isEmpty()) {
          RoleRightsFile.applyEdits(roleXml, payload.edits);
        }
        return "OK";
      }
      case "cf-support-object-mode-set": {
        // Режим в name: "0" запретить, "1" разрешить, "2" снять с поддержки
        java.nio.file.Path objectXml = p.reqPath(p.objectXml, "objectXml");
        String uuid = io.github.yellowhammer.designerxml.cf.ObjectBelongingReader.readRootUuid(objectXml);
        if (uuid == null) {
          throw new IllegalArgumentException("В шапке файла объекта не найден uuid.");
        }
        java.nio.file.Path root = p.reqPath(p.configurationXml, "configurationXml").toAbsolutePath().getParent();
        SupportRules.setObjectMode(root, uuid, Integer.parseInt(p.req(p.name, "name")));
        return "OK";
      }
      case "cf-support-enable-rules": {
        java.nio.file.Path root = p.reqPath(p.configurationXml, "configurationXml").toAbsolutePath().getParent();
        SupportRules.enableRules(root);
        return "OK";
      }
      case "cfe-borrow-object": {
        java.nio.file.Path created = CfeBorrow.borrowObject(
          p.reqPath(p.objectXml, "objectXml"),
          p.reqPath(p.configurationXml, "configurationXml"),
          p.version());
        return created.toString();
      }
      case "cf-form-add":
        FormScaffold.addForm(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-form-compile":
        FormScaffold.compileForm(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"), p.req(p.payloadJson, "payloadJson"));
        return "OK";
      case "cf-md-form-delete":
        MdObjectChildMutations.deleteForm(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-accounting-flag-add":
        MdObjectChildMutations.addAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-accounting-flag-rename":
        MdObjectChildMutations.renameAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-accounting-flag-delete":
        MdObjectChildMutations.deleteAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-ext-dimension-accounting-flag-add":
        MdObjectChildMutations.addExtDimensionAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-ext-dimension-accounting-flag-rename":
        MdObjectChildMutations.renameExtDimensionAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-ext-dimension-accounting-flag-delete":
        MdObjectChildMutations.deleteExtDimensionAccountingFlag(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-dimension-add":
        MdObjectChildMutations.addDimension(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-dimension-rename":
        MdObjectChildMutations.renameDimension(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-dimension-delete":
        MdObjectChildMutations.deleteDimension(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-dimension-duplicate":
        MdObjectChildMutations.duplicateDimension(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-dimension-reorder":
        MdObjectChildMutations.reorderDimensions(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";

      case "cf-md-resource-add":
        MdObjectChildMutations.addResource(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-resource-rename":
        MdObjectChildMutations.renameResource(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-resource-delete":
        MdObjectChildMutations.deleteResource(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-resource-duplicate":
        MdObjectChildMutations.duplicateResource(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-resource-reorder":
        MdObjectChildMutations.reorderResources(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";

      case "cf-md-enum-value-add":
        MdObjectChildMutations.addEnumValue(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-enum-value-rename":
        MdObjectChildMutations.renameEnumValue(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-enum-value-delete":
        MdObjectChildMutations.deleteEnumValue(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-enum-value-duplicate":
        MdObjectChildMutations.duplicateEnumValue(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-enum-value-reorder":
        MdObjectChildMutations.reorderEnumValues(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";

      case "cf-md-tabular-section-add":
        MdObjectChildMutations.addTabularSection(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-tabular-section-rename":
        MdObjectChildMutations.renameTabularSection(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-tabular-section-delete":
        MdObjectChildMutations.deleteTabularSection(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.name, "name"));
        return "OK";
      case "cf-md-tabular-section-duplicate":
        MdObjectChildMutations.duplicateTabularSection(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";

      case "cf-md-tabular-attribute-add":
        MdObjectChildMutations.addTabularAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.tabularSection, "tabularSection"), p.req(p.name, "name"));
        return "OK";
      case "cf-md-tabular-attribute-rename":
        MdObjectChildMutations.renameTabularAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.tabularSection, "tabularSection"),
          p.req(p.oldName, "oldName"), p.req(p.newName, "newName"));
        return "OK";
      case "cf-md-tabular-attribute-delete":
        MdObjectChildMutations.deleteTabularAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.tabularSection, "tabularSection"), p.req(p.name, "name"));
        return "OK";
      case "cf-md-attribute-reorder":
        MdObjectChildMutations.reorderAttributes(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";
      case "cf-md-tabular-section-reorder":
        MdObjectChildMutations.reorderTabularSections(
          p.reqPath(p.objectXml, "objectXml"), p.version(), parseNameList(p));
        return "OK";
      case "cf-md-tabular-attribute-reorder":
        MdObjectChildMutations.reorderTabularAttributes(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.tabularSection, "tabularSection"), parseNameList(p));
        return "OK";

      case "cf-md-tabular-attribute-duplicate":
        MdObjectChildMutations.duplicateTabularAttribute(
          p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.tabularSection, "tabularSection"),
          p.req(p.sourceName, "sourceName"), p.req(p.newName, "newName"));
        return "OK";

      case "external-artifact-rename":
        ExternalArtifactMutations.rename(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.newName, "newName"));
        return "OK";
      case "external-artifact-delete":
        ExternalArtifactMutations.delete(p.reqPath(p.objectXml, "objectXml"));
        return "OK";
      case "external-artifact-duplicate":
        ExternalArtifactMutations.duplicate(p.reqPath(p.objectXml, "objectXml"), p.version(), p.req(p.newName, "newName"));
        return "OK";
      case "external-artifact-add":
        NewExternalArtifactXml.create(
          p.reqPath(p.artifactsRoot, "artifactsRoot"),
          p.req(p.name, "name"),
          ExternalArtifactKind.fromCli(p.req(p.kind, "kind")),
          p.version());
        return "OK";

      case "cf-md-object-set": {
        MdObjectPropertiesDto dto = parsePayload(p, MdObjectPropertiesDto.class);
        MdObjectPropertiesEdit.writeDto(p.reqPath(p.objectXml, "objectXml"), p.version(), dto);
        return "OK";
      }
      case "external-artifact-properties-set": {
        ExternalArtifactPropertiesDto dto = parsePayload(p, ExternalArtifactPropertiesDto.class);
        ExternalArtifactPropertiesEdit.write(p.reqPath(p.objectXml, "objectXml"), p.version(), dto);
        return "OK";
      }
      case "cf-form-item-properties-set": {
        FormItemPropertyChangeDto[] changes = parsePayload(p, FormItemPropertyChangeDto[].class);
        FormItemPropertyEdit.apply(p.reqPath(p.formXml, "formXml"), p.version(), java.util.Arrays.asList(changes));
        return "OK";
      }
      case "cf-configuration-properties-set": {
        ConfigurationPropertiesDto dto = parsePayload(p, ConfigurationPropertiesDto.class);
        ConfigurationPropertiesEdit.write(p.reqPath(p.configurationXml, "configurationXml"), p.version(), dto);
        return "OK";
      }
      case "init-empty-cf": {
        String cfgName = p.name == null || p.name.isEmpty() ? CfLayout.DEFAULT_CONFIGURATION_NAME : p.name;
        Path target = p.reqPath(p.targetCfRoot, "targetCfRoot");
        EmptyCfScaffold.writeEmptyTree(target, cfgName, p.synonymRu, null, null, p.version());
        return "OK: " + target.toAbsolutePath();
      }

      case "init-empty-cfe": {
        Path target = p.reqPath(p.targetCfeRoot, "targetCfeRoot");
        EmptyCfeScaffold.Purpose purpose = p.purpose == null || p.purpose.isBlank()
          ? EmptyCfeScaffold.Purpose.CUSTOMIZATION
          : EmptyCfeScaffold.Purpose.fromCliName(p.purpose);
        if (p.mainConfigurationXml != null && !p.mainConfigurationXml.isBlank()) {
          EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
            target,
            p.req(p.name, "name"),
            p.synonymRu,
            p.namePrefix,
            purpose,
            Path.of(p.mainConfigurationXml),
            p.version());
          return "OK: " + target.toAbsolutePath();
        }
        EmptyCfeScaffold.writeEmptyTree(
          target,
          p.req(p.name, "name"),
          p.synonymRu,
          p.namePrefix,
          purpose,
          p.compatibilityMode,
          p.interfaceCompatibilityMode,
          p.version());
        return "OK: " + target.toAbsolutePath();
      }

      case "add-md-object": {
        MdObjectAddType k = MdObjectAddType.fromCliName(p.req(p.type, "type"));
        if (k != MdObjectAddType.CATALOG && (p.synonymEmpty || p.synonymRu != null)) {
          throw new IllegalArgumentException("synonymRu/synonymEmpty поддерживаются только для type CATALOG");
        }
        if (p.autoName) {
          if (p.name != null && !p.name.isBlank()) {
            throw new IllegalArgumentException("укажите name или autoName, не оба");
          }
          return MdObjectAdd.addWithNextAvailableName(
            p.reqPath(p.configurationXml, "configurationXml"), p.version(), k, p.synonymRu, p.synonymEmpty);
        }
        MdObjectAdd.add(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.req(p.name, "name"),
          p.version(),
          k,
          p.synonymRu,
          p.synonymEmpty);
        return "OK";
      }

      default:
        throw new IllegalArgumentException("неизвестный op: " + p.op);
    }
  }

  /** Список имён из {@code payloadJson} (JSON-массив строк). */
  private static java.util.List<String> parseNameList(CliParams p) {
    String[] names = parsePayload(p, String[].class);
    return java.util.Arrays.asList(names);
  }

  private static <T> T parsePayload(CliParams p, Class<T> type) {
    String json = p.req(p.payloadJson, "payloadJson");
    T dto;
    try {
      dto = new Gson().fromJson(json, type);
    } catch (JsonSyntaxException e) {
      throw new IllegalArgumentException("некорректный payloadJson: " + e.getMessage());
    }
    if (dto == null) {
      throw new IllegalArgumentException("пустой payloadJson");
    }
    return dto;
  }
}
