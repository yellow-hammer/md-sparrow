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
import io.github.yellowhammer.designerxml.cf.CatalogFormDto;
import io.github.yellowhammer.designerxml.cf.CfDumpValidation;
import io.github.yellowhammer.designerxml.cf.CatalogFormEdit;
import io.github.yellowhammer.designerxml.cf.ConfigurationCatalogLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationChildObjectLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormContentRead;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertyEnums;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphDto;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;
import io.github.yellowhammer.designerxml.cf.ProjectSourceDirs;
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
  private static String dispatch(CliParams p) throws Exception {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    switch (p.op) {
      case "cf-md-object-get": {
        MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-enum-labels": {
        return gson.toJson(Map.of(
          "values", EnumValueLabels.all(),
          "byProperty", EnumValueLabels.byProperty()
        ));
      }
      case "cf-md-object-enums": {
        return gson.toJson(MdObjectPropertyEnums.forVersion(p.version()));
      }
      case "cf-md-object-structure-get": {
        MdObjectStructureDto dto = MdObjectStructureRead.read(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "external-artifact-properties-get": {
        ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-configuration-properties-get": {
        ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(p.reqPath(p.configurationXml, "configurationXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-catalog-form-get": {
        CatalogFormDto dto = CatalogFormEdit.readDto(p.reqPath(p.objectXml, "objectXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-form-item-properties": {
        return gson.toJson(FormItemPropertyDictionary.forVersion(p.version()));
      }
      case "cf-form-content-get": {
        FormContentDto dto = FormContentRead.read(p.reqPath(p.formXml, "formXml"), p.version());
        return gson.toJson(dto);
      }
      case "cf-list-child-objects": {
        var names = ConfigurationChildObjectLister.listNames(
          p.reqPath(p.configurationXml, "configurationXml"), p.version(), p.req(p.tag, "tag"));
        return ConfigurationCatalogLister.toJsonArray(names);
      }
      case "cf-validate-dump": {
        var findings = CfDumpValidation.validate(p.reqPath(p.cfRoot, "cfRoot"));
        return gson.toJson(findings);
      }
      case "cf-list-catalogs": {
        var names = ConfigurationCatalogLister.listCatalogNames(
          p.reqPath(p.configurationXml, "configurationXml"), p.version());
        return ConfigurationCatalogLister.toJsonArray(names);
      }
      case "cf-md-subsystem-tree": {
        var nodes = SubsystemTreeBuilder.build(
          p.reqPath(p.configurationXml, "configurationXml"), p.version());
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
