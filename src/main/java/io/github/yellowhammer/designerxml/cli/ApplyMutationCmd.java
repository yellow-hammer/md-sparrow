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

import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtConfigurationProperties;
import io.github.yellowhammer.edt.EdtExchangePlanContent;
import io.github.yellowhammer.edt.EdtExtensionFeatures;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtMutationRouter;
import io.github.yellowhammer.edt.EdtObjectMutations;
import io.github.yellowhammer.edt.EdtBorrow;
import io.github.yellowhammer.edt.EdtExtensionScaffold;
import io.github.yellowhammer.edt.EdtExternalArtifacts;
import io.github.yellowhammer.edt.EdtFormItemPropertyEdit;
import io.github.yellowhammer.edt.EdtObjectScaffold;
import io.github.yellowhammer.edt.EdtSupportRules;
import io.github.yellowhammer.edt.EdtObjectProperties;
import io.github.yellowhammer.edt.EdtSubsystemCommandInterface;
import io.github.yellowhammer.edt.EdtObjectWriter;

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

  /** Правки, которые умеем и вне состава объекта. */
  private static final java.util.Set<String> EDT_WRITES = java.util.Set.of(
    "cf-md-object-set",
    "cf-configuration-properties-set",
    "cf-role-rights-set",
    "cf-md-object-rename",
    "cf-md-object-delete",
    "cf-md-object-duplicate",
    "cf-md-exchange-plan-content-set",
    "cf-md-subsystem-command-visibility-set",
    "cf-md-subsystem-command-placement-set",
    "cf-md-subsystem-command-order-set",
    "cf-md-subsystem-subsystems-order-set",
    "cf-md-subsystem-groups-order-set",
    "external-artifact-properties-set",
    "add-md-object",
    "cf-form-add",
    "cf-md-form-delete",
    "cf-form-item-properties-set",
    "init-empty-cfe",
    "cfe-borrow-object",
    "external-artifact-add",
    "external-artifact-rename",
    "external-artifact-duplicate",
    "external-artifact-delete",
    "cf-support-object-mode-set",
    "cf-support-element-mode-set",
    "cf-support-remove");

  /**
   * Правит проект 1С:EDT: состав конфигурации, объект, его формы и узлы.
   *
   * @return ответ команды либо {@code null}, если файлы не в формате EDT
   */
  private static String applyEdtMutation(CliParams p) throws IOException {
    if ("add-md-object".equals(p.op) && EdtLayout.isObjectFile(p.configurationXml)) {
      MdObjectAddType kind = MdObjectAddType.fromCliName(p.req(p.type, "type"));
      java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
      if (p.autoName) {
        return EdtObjectScaffold.addWithNextAvailableName(configuration, EdtModel.bundled(), kind);
      }
      String name = p.req(p.name, "name");
      EdtObjectScaffold.add(configuration, EdtModel.bundled(), kind, name);
      return name;
    }
    if ("cf-form-item-properties-set".equals(p.op) && EdtLayout.isFormFile(p.formXml)) {
      FormItemPropertyChangeDto[] changes = parsePayload(p, FormItemPropertyChangeDto[].class);
      EdtFormItemPropertyEdit.apply(
        p.reqPath(p.formXml, "formXml"), EdtModel.bundled(), java.util.Arrays.asList(changes));
      return "OK";
    }
    // Расширение и внешний объект заводятся рядом с проектом расширяемой конфигурации
    if ("init-empty-cfe".equals(p.op) && EdtLayout.isObjectFile(p.mainConfigurationXml)) {
      EmptyCfeScaffold.Purpose purpose = p.purpose == null || p.purpose.isBlank()
        ? EmptyCfeScaffold.Purpose.CUSTOMIZATION
        : EmptyCfeScaffold.Purpose.fromCliName(p.purpose);
      EdtExtensionScaffold.create(
        Path.of(p.mainConfigurationXml),
        p.reqPath(p.targetCfeRoot, "targetCfeRoot"),
        p.req(p.name, "name"),
        p.synonymRu,
        p.namePrefix,
        purpose,
        EdtModel.bundled());
      return "OK";
    }
    if ("external-artifact-add".equals(p.op) && EdtLayout.isObjectFile(p.mainConfigurationXml)) {
      return EdtExternalArtifacts.create(
        p.reqPath(p.artifactsRoot, "artifactsRoot"),
        Path.of(p.mainConfigurationXml),
        p.req(p.name, "name"),
        ExternalArtifactKind.fromCli(p.req(p.kind, "kind"))).toString();
    }
    if ("cf-support-remove".equals(p.op) && EdtLayout.isObjectFile(p.configurationXml)) {
      EdtSupportRules.removeSupport(Path.of(p.configurationXml), p.expectedGeneration);
      return "OK";
    }
    // Субъект поддержки бывает и формой, и модулем: формат виден по проекту вокруг файла
    boolean inEdtProject = p.objectXml != null && !p.objectXml.isBlank()
      && EdtSupportRules.sourceRoot(Path.of(p.objectXml)) != null;
    if ("cf-support-object-mode-set".equals(p.op) && inEdtProject) {
      EdtSupportRules.setModeForFile(
        Path.of(p.objectXml), supportMode(p), "children".equals(p.tag), p.expectedGeneration);
      return "OK";
    }
    if ("cf-support-element-mode-set".equals(p.op) && inEdtProject) {
      EdtSupportRules.setModeForElement(
        Path.of(p.objectXml), p.req(p.tag, "tag"), supportMode(p), p.expectedGeneration);
      return "OK";
    }
    if (!EdtLayout.isObjectFile(p.objectXml)) {
      return null;
    }
    refuseLockedEdt(p);
    switch (p.op) {
      case "cfe-borrow-object" -> {
        return EdtBorrow.borrowObject(
          p.reqPath(p.objectXml, "objectXml"),
          p.reqPath(p.configurationXml, "configurationXml"),
          EdtModel.bundled()).toString();
      }
      case "external-artifact-rename" -> {
        return EdtExternalArtifacts.rename(p.reqPath(p.objectXml, "objectXml"), p.req(p.newName, "newName")).toString();
      }
      case "external-artifact-duplicate" -> {
        return EdtExternalArtifacts.duplicate(p.reqPath(p.objectXml, "objectXml"), p.req(p.newName, "newName")).toString();
      }
      case "external-artifact-delete" -> {
        EdtExternalArtifacts.delete(p.reqPath(p.objectXml, "objectXml"));
        return "OK";
      }
      default -> {
        // Остальные операции над объектом разбираются ниже
      }
    }
    // Объект в EDT - это каталог целиком, поэтому у операций над ним свой код
    switch (p.op) {
      case "cf-form-add" -> {
        EdtObjectScaffold.addForm(p.reqPath(p.objectXml, "objectXml"), EdtModel.bundled(), p.req(p.name, "name"));
        return "OK";
      }
      case "cf-md-form-delete" -> {
        EdtObjectScaffold.deleteForm(p.reqPath(p.objectXml, "objectXml"), p.req(p.name, "name"));
        return "OK";
      }
      case "cf-md-object-rename" -> {
        EdtObjectMutations.rename(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.oldName, "oldName"),
          p.req(p.newName, "newName"));
        return "OK";
      }
      case "cf-md-object-delete" -> {
        EdtObjectMutations.delete(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.name, "name"));
        return "OK";
      }
      case "cf-md-object-duplicate" -> {
        EdtObjectMutations.duplicate(
          p.reqPath(p.configurationXml, "configurationXml"),
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          p.req(p.sourceName, "sourceName"),
          p.req(p.newName, "newName"));
        return "OK";
      }
      default -> {
        // Остальное правит общий разбор команд состава
      }
    }
    if (!EdtMutationRouter.handles(p.op)) {
      return null;
    }
    EdtMutationRouter.apply(
      p.op,
      p.reqPath(p.objectXml, "objectXml"),
      EdtModel.bundled(),
      new EdtMutationRouter.Arguments(
        p.name,
        p.oldName,
        p.newName,
        p.sourceName,
        p.tabularSection,
        // Порядок узлов приходит списком имён и нужен только перестановке
        p.op.endsWith("-reorder") ? parseNameList(p) : java.util.List.of()));
    return "OK";
  }

  /**
   * Отказывает в правке объекта или элемента проекта EDT, которые заперты поставщиком.
   *
   * Заимствование читает объект, а не правит его, поэтому запрет его не касается.
   */
  private static void refuseLockedEdt(CliParams p) throws IOException {
    if ("cfe-borrow-object".equals(p.op)) {
      return;
    }
    Path objectMdo = Path.of(p.objectXml);
    EdtSupportRules.ensureEditable(objectMdo);
    if (!p.op.startsWith("cf-md-")) {
      return;
    }
    int lastDash = p.op.lastIndexOf('-');
    String field = lastDash < 0 ? null : ELEMENT_TARGET_BY_MODE.get(p.op.substring(lastDash + 1));
    if (field == null) {
      return;
    }
    String name = switch (field) {
      case "oldName" -> p.oldName;
      case "sourceName" -> p.sourceName;
      default -> p.name;
    };
    if (name == null || name.isBlank()) {
      return;
    }
    String path = p.tabularSection == null || p.tabularSection.isBlank() ? name : p.tabularSection + "/" + name;
    EdtSupportRules.ensureElementEditable(objectMdo, "element:" + p.op.substring(0, lastDash) + ":" + path);
  }

  /**
   * Отказывает в правках, которых для формата 1С:EDT ещё нет.
   *
   * Без внятного отказа правка ушла бы в разбор выгрузки конфигуратора и упала
   * бы там на первом же теге.
   */
  private static void refuseEdtWrite(CliParams p) {
    boolean edt = EdtLayout.isObjectFile(p.objectXml) || EdtLayout.isObjectFile(p.configurationXml);
    if (edt && !EDT_WRITES.contains(p.op) && !EdtMutationRouter.handles(p.op)) {
      throw new IllegalArgumentException(
        "Правка \"" + p.op + "\" в формате 1С:EDT пока не поддержана.");
    }
  }

  /** Режим поддержки из name: 0 запретить, 1 разрешить, 2 снять с поддержки. */
  private static int supportMode(CliParams p) {
    String raw = p.req(p.name, "name");
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Режим поддержки должен быть числом 0, 1 или 2, а не \"" + raw + "\".");
    }
  }

  /** Режимы правки существующего элемента: у цели есть своё правило поддержки. */
  private static final java.util.Map<String, String> ELEMENT_TARGET_BY_MODE = java.util.Map.of(
    "rename", "oldName",
    "delete", "name",
    "duplicate", "sourceName");

  /**
   * Отказывает в правке элемента, которому поставщик запретил изменение.
   *
   * <p>Правило заведено на каждый элемент объекта, поэтому разрешение на объект
   * не открывает его реквизиты: цель правки видна по имени операции.
   */
  private static void refuseLockedElement(CliParams p) throws IOException {
    if (p.op == null || !p.op.startsWith("cf-md-") || p.objectXml == null) {
      return;
    }
    int lastDash = p.op.lastIndexOf('-');
    if (lastDash < 0) {
      return;
    }
    String field = ELEMENT_TARGET_BY_MODE.get(p.op.substring(lastDash + 1));
    if (field == null) {
      return;
    }
    String name = switch (field) {
      case "oldName" -> p.oldName;
      case "sourceName" -> p.sourceName;
      default -> p.name;
    };
    if (name == null || name.isBlank()) {
      return;
    }
    String path = p.tabularSection == null || p.tabularSection.isBlank()
      ? name
      : p.tabularSection + "/" + name;
    SupportRules.ensureElementEditable(
      Path.of(p.objectXml), "element:" + p.op.substring(0, lastDash) + ":" + path);
  }

  /**
   * Выполняет операцию из {@code p.op}, переиспользуя сервисы изменения.
   *
   * @return текст для stdout (как у одиночных подкоманд: {@code OK} либо имя созданного объекта)
   */
  private static String dispatch(CliParams p) throws IOException, JAXBException {
    // Правила поддержки учитываются, пока вызывающая программа не сказала иначе
    SupportRules.setEnforced(!p.ignoreSupport);
    refuseEdtWrite(p);
    String edt = applyEdtMutation(p);
    if (edt != null) {
      return edt;
    }
    refuseLockedElement(p);
    // Остальные правки объекта проекта EDT идут общим разбором: запрет поставщика проверяется здесь
    if (p.objectXml != null && !p.objectXml.isBlank() && EdtSupportRules.sourceRoot(Path.of(p.objectXml)) != null) {
      EdtSupportRules.ensureEditable(Path.of(p.objectXml));
    }
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
        java.nio.file.Path visibilityOwner = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(visibilityOwner)) {
          SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(visibilityOwner);
          dto.visibility = entries;
          EdtSubsystemCommandInterface.write(visibilityOwner, dto);
        } else {
          SubsystemCommandInterfaceFile.writeVisibility(visibilityOwner, p.version(), entries);
        }
        return "OK";
      }
      case "cf-md-exchange-plan-content-set": {
        java.util.List<MdContentMemberDto> members = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<java.util.List<MdContentMemberDto>>() { }.getType());
        java.nio.file.Path plan = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(plan)) {
          EdtExchangePlanContent.write(plan, members);
        } else {
          ExchangePlanContentFile.write(plan, p.version(), members);
        }
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
        // Режим в name: "0" запретить, "1" разрешить, "2" снять с поддержки;
        // tag = "children" распространяет режим на подчинённые объекту субъекты
        SupportRules.setModeForFile(
          p.reqPath(p.objectXml, "objectXml"),
          supportMode(p),
          "children".equals(p.tag),
          p.expectedGeneration);
        return "OK";
      }
      case "cf-support-element-mode-set": {
        // Правило есть у каждого элемента объекта: tag несёт ключ элемента из
        // cf-support-object-states, name - режим
        SupportRules.setModeForElement(
          p.reqPath(p.objectXml, "objectXml"),
          p.req(p.tag, "tag"),
          supportMode(p),
          p.expectedGeneration);
        return "OK";
      }
      case "cf-md-subsystem-command-placement-set": {
        java.util.List<SubsystemCommandInterfaceFile.CommandEntry> placement = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<
            java.util.List<SubsystemCommandInterfaceFile.CommandEntry>>() { }.getType());
        java.nio.file.Path placementOwner = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(placementOwner)) {
          SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(placementOwner);
          dto.placement = placement;
          EdtSubsystemCommandInterface.write(placementOwner, dto);
        } else {
          SubsystemCommandInterfaceFile.writePlacement(placementOwner, p.version(), placement);
        }
        return "OK";
      }
      case "cf-md-subsystem-command-order-set": {
        java.util.List<SubsystemCommandInterfaceFile.CommandEntry> order = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<
            java.util.List<SubsystemCommandInterfaceFile.CommandEntry>>() { }.getType());
        java.nio.file.Path orderOwner = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(orderOwner)) {
          SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(orderOwner);
          dto.order = order;
          EdtSubsystemCommandInterface.write(orderOwner, dto);
        } else {
          SubsystemCommandInterfaceFile.writeOrder(orderOwner, p.version(), order);
        }
        return "OK";
      }
      case "cf-md-subsystem-subsystems-order-set": {
        java.util.List<String> refs = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<java.util.List<String>>() { }.getType());
        java.nio.file.Path subsystemsOwner = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(subsystemsOwner)) {
          SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(subsystemsOwner);
          dto.subsystemsOrder = refs;
          EdtSubsystemCommandInterface.write(subsystemsOwner, dto);
        } else {
          SubsystemCommandInterfaceFile.writeSubsystemsOrder(subsystemsOwner, p.version(), refs);
        }
        return "OK";
      }
      case "cf-md-subsystem-groups-order-set": {
        java.util.List<String> groups = new Gson().fromJson(
          p.req(p.payloadJson, "payloadJson"),
          new com.google.gson.reflect.TypeToken<java.util.List<String>>() { }.getType());
        java.nio.file.Path groupsOwner = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(groupsOwner)) {
          SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(groupsOwner);
          dto.groupsOrder = groups;
          EdtSubsystemCommandInterface.write(groupsOwner, dto);
        } else {
          SubsystemCommandInterfaceFile.writeGroupsOrder(groupsOwner, p.version(), groups);
        }
        return "OK";
      }
      case "cf-support-remove": {
        java.nio.file.Path root = p.reqPath(p.configurationXml, "configurationXml").toAbsolutePath().getParent();
        SupportRules.removeSupport(root, p.expectedGeneration);
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
        java.nio.file.Path objectFile = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(objectFile)) {
          EdtObjectWriter.writeDto(objectFile, dto, EdtModel.bundled());
        } else {
          MdObjectPropertiesEdit.writeDto(
            objectFile, p.version(), dto, EdtExtensionFeatures.byDesignerContainer(EdtModel.bundled(), dto.kind));
        }
        return "OK";
      }
      case "external-artifact-properties-set": {
        ExternalArtifactPropertiesDto dto = parsePayload(p, ExternalArtifactPropertiesDto.class);
        java.nio.file.Path artifact = p.reqPath(p.objectXml, "objectXml");
        if (EdtLayout.isObjectFile(artifact)) {
          EdtObjectProperties.writeExternalDto(artifact, dto, EdtModel.bundled());
        } else {
          ExternalArtifactPropertiesEdit.write(artifact, p.version(), dto);
        }
        return "OK";
      }
      case "cf-form-item-properties-set": {
        FormItemPropertyChangeDto[] changes = parsePayload(p, FormItemPropertyChangeDto[].class);
        FormItemPropertyEdit.apply(p.reqPath(p.formXml, "formXml"), p.version(), java.util.Arrays.asList(changes));
        return "OK";
      }
      case "cf-configuration-properties-set": {
        ConfigurationPropertiesDto dto = parsePayload(p, ConfigurationPropertiesDto.class);
        java.nio.file.Path configuration = p.reqPath(p.configurationXml, "configurationXml");
        if (EdtLayout.isObjectFile(configuration)) {
          EdtConfigurationProperties.write(configuration, dto, EdtModel.bundled());
        } else {
          ConfigurationPropertiesEdit.write(configuration, p.version(), dto);
        }
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
