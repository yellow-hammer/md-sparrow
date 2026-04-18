/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactMutations;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.NewExternalArtifactXml;
import jakarta.xml.bind.JAXBException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

final class ExternalArtifactCommands {
  private ExternalArtifactCommands() {
  }

  @Command(name = "external-artifact-add", description = "Создать внешний отчёт или обработку.")
  static final class ExternalArtifactAddCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Корень src/erf или src/epf")
    Path artifactsRoot;

    @Parameters(index = "1", description = "Имя внешнего объекта")
    String name;

    @Option(names = "--kind", required = true, description = "REPORT | DATA_PROCESSOR")
    String kind;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        ExternalArtifactKind parsedKind = ExternalArtifactKind.fromCli(kind);
        NewExternalArtifactXml.create(artifactsRoot, name, parsedKind, version);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "external-artifact-properties-get", description = "Вывести JSON свойств внешнего объекта.")
  static final class ExternalArtifactPropertiesGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к XML внешнего отчёта/обработки")
    Path objectXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(objectXml, version);
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        System.out.println(gson.toJson(dto));
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }

  @Command(name = "external-artifact-properties-set", description = "Применить JSON свойств внешнего объекта.")
  static final class ExternalArtifactPropertiesSetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к XML внешнего отчёта/обработки")
    Path objectXml;

    @Parameters(index = "1", description = "Путь к JSON")
    Path jsonFile;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        ExternalArtifactPropertiesDto dto = gson.fromJson(json, ExternalArtifactPropertiesDto.class);
        if (dto == null) {
          System.err.println("invalid JSON");
          return 2;
        }
        ExternalArtifactPropertiesEdit.write(objectXml, version, dto);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "external-artifact-rename", description = "Переименовать внешний объект.")
  static final class ExternalArtifactRenameCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к XML внешнего отчёта/обработки")
    Path objectXml;

    @Option(names = "--new-name", required = true, description = "Новое имя")
    String newName;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        ExternalArtifactMutations.rename(objectXml, version, newName);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "external-artifact-delete", description = "Удалить внешний объект.")
  static final class ExternalArtifactDeleteCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к XML внешнего отчёта/обработки")
    Path objectXml;

    @Override
    public Integer call() throws Exception {
      try {
        ExternalArtifactMutations.delete(objectXml);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "external-artifact-duplicate", description = "Создать копию внешнего объекта.")
  static final class ExternalArtifactDuplicateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к XML внешнего отчёта/обработки")
    Path objectXml;

    @Option(names = "--new-name", required = true, description = "Имя копии")
    String newName;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        ExternalArtifactMutations.duplicate(objectXml, version, newName);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }
}
