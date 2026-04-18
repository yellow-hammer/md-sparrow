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
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.CfMdObjectMutations;
import io.github.yellowhammer.designerxml.cf.MdObjectChildMutations;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import jakarta.xml.bind.JAXBException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

final class MdObjectMutationCommands {
  private MdObjectMutationCommands() {
  }

  @Command(name = "cf-md-object-set", description = "Применить JSON к объекту метаданных.")
  static final class CfMdObjectSetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
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
        MdObjectPropertiesDto dto = gson.fromJson(json, MdObjectPropertiesDto.class);
        if (dto == null) {
          System.err.println("invalid JSON");
          return 2;
        }
        MdObjectPropertiesEdit.writeDto(objectXml, version, dto);
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

  @Command(name = "cf-md-attribute-add", description = "Добавить реквизит.")
  static final class CfMdAttributeAddCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--name", required = true, description = "Имя реквизита")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.addAttribute(objectXml, version, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-attribute-rename", description = "Переименовать реквизит.")
  static final class CfMdAttributeRenameCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--old-name", required = true, description = "Текущее имя реквизита")
    String oldName;
    @Option(names = "--new-name", required = true, description = "Новое имя реквизита")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.renameAttribute(objectXml, version, oldName, newName);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-attribute-delete", description = "Удалить реквизит.")
  static final class CfMdAttributeDeleteCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--name", required = true, description = "Имя реквизита")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.deleteAttribute(objectXml, version, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-attribute-duplicate", description = "Дублировать реквизит.")
  static final class CfMdAttributeDuplicateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--source-name", required = true, description = "Имя исходного реквизита")
    String sourceName;
    @Option(names = "--new-name", required = true, description = "Имя копии")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.duplicateAttribute(objectXml, version, sourceName, newName);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-section-add", description = "Добавить табличную часть.")
  static final class CfMdTabularSectionAddCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--name", required = true, description = "Имя табличной части")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.addTabularSection(objectXml, version, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-section-rename", description = "Переименовать табличную часть.")
  static final class CfMdTabularSectionRenameCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--old-name", required = true, description = "Текущее имя табличной части")
    String oldName;
    @Option(names = "--new-name", required = true, description = "Новое имя табличной части")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.renameTabularSection(objectXml, version, oldName, newName);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-section-delete", description = "Удалить табличную часть.")
  static final class CfMdTabularSectionDeleteCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--name", required = true, description = "Имя табличной части")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.deleteTabularSection(objectXml, version, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-section-duplicate", description = "Дублировать табличную часть.")
  static final class CfMdTabularSectionDuplicateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--source-name", required = true, description = "Имя исходной табличной части")
    String sourceName;
    @Option(names = "--new-name", required = true, description = "Имя копии")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.duplicateTabularSection(objectXml, version, sourceName, newName);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-attribute-add", description = "Добавить реквизит табличной части.")
  static final class CfMdTabularAttributeAddCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--tabular-section", required = true, description = "Имя табличной части")
    String tabularSectionName;
    @Option(names = "--name", required = true, description = "Имя реквизита табличной части")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.addTabularAttribute(objectXml, version, tabularSectionName, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-attribute-rename", description = "Переименовать реквизит табличной части.")
  static final class CfMdTabularAttributeRenameCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--tabular-section", required = true, description = "Имя табличной части")
    String tabularSectionName;
    @Option(names = "--old-name", required = true, description = "Текущее имя реквизита")
    String oldName;
    @Option(names = "--new-name", required = true, description = "Новое имя реквизита")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.renameTabularAttribute(objectXml, version, tabularSectionName, oldName, newName);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-attribute-delete", description = "Удалить реквизит табличной части.")
  static final class CfMdTabularAttributeDeleteCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--tabular-section", required = true, description = "Имя табличной части")
    String tabularSectionName;
    @Option(names = "--name", required = true, description = "Имя реквизита")
    String name;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.deleteTabularAttribute(objectXml, version, tabularSectionName, name);
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-tabular-attribute-duplicate", description = "Дублировать реквизит табличной части.")
  static final class CfMdTabularAttributeDuplicateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;
    @Option(names = "--tabular-section", required = true, description = "Имя табличной части")
    String tabularSectionName;
    @Option(names = "--source-name", required = true, description = "Имя исходного реквизита")
    String sourceName;
    @Option(names = "--new-name", required = true, description = "Имя копии")
    String newName;
    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectChildMutations.duplicateTabularAttribute(
          objectXml,
          version,
          tabularSectionName,
          sourceName,
          newName
        );
      } catch (IllegalArgumentException | IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "cf-md-object-delete", description = "Удалить объект метаданных.")
  static final class CfMdObjectDeleteCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Parameters(index = "1", description = "Путь к XML объекта")
    Path objectXml;

    @Option(names = "--tag", required = true, description = "Тег ChildObjects (Catalog, Document, Role, ...)")
    String tag;

    @Option(names = "--name", required = true, description = "Имя объекта в Configuration/ChildObjects")
    String name;

    @Override
    public Integer call() throws Exception {
      try {
        CfMdObjectMutations.delete(configurationXml, objectXml, tag, name);
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

  @Command(name = "cf-md-object-rename", description = "Переименовать объект метаданных.")
  static final class CfMdObjectRenameCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Parameters(index = "1", description = "Путь к XML объекта")
    Path objectXml;

    @Option(names = "--tag", required = true, description = "Тег ChildObjects (Catalog, Document, Role, ...)")
    String tag;

    @Option(names = "--old-name", required = true, description = "Текущее имя объекта")
    String oldName;

    @Option(names = "--new-name", required = true, description = "Новое имя объекта")
    String newName;

    @Override
    public Integer call() throws Exception {
      try {
        CfMdObjectMutations.rename(configurationXml, objectXml, tag, oldName, newName);
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

  @Command(name = "cf-md-object-duplicate", description = "Создать копию объекта метаданных.")
  static final class CfMdObjectDuplicateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Parameters(index = "1", description = "Путь к XML исходного объекта")
    Path objectXml;

    @Option(names = "--tag", required = true, description = "Тег ChildObjects (Catalog, Document, Role, ...)")
    String tag;

    @Option(names = "--source-name", required = true, description = "Имя исходного объекта")
    String sourceName;

    @Option(names = "--new-name", required = true, description = "Имя нового объекта")
    String newName;

    @Override
    public Integer call() throws Exception {
      try {
        CfMdObjectMutations.duplicate(configurationXml, objectXml, tag, sourceName, newName);
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
}
