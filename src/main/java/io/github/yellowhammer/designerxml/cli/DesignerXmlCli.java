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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import io.github.yellowhammer.designerxml.XmlValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.yellowhammer.designerxml.cf.CatalogFormDto;
import io.github.yellowhammer.designerxml.cf.CatalogFormEdit;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormContentRead;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyChangeDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertyEnums;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;
import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectProperties;
import io.github.yellowhammer.edt.EdtObjectStructure;
import io.github.yellowhammer.designerxml.cf.ConfigurationCatalogLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationChildObjectLister;
import io.github.yellowhammer.designerxml.cf.CfLayout;
import io.github.yellowhammer.designerxml.cf.MdObjectAdd;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataGraphDto;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;
import io.github.yellowhammer.designerxml.cf.StandardCommandLabels;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Задел CLI для вызова из расширения VS Code или скриптов: валидация и round-trip.
 */
@Command(
  name = "md-sparrow",
  mixinStandardHelpOptions = true,
  versionProvider = DesignerXmlCli.ManifestVersion.class,
  subcommands = {
    DesignerXmlCli.ValidateCmd.class,
    DesignerXmlCli.RoundTripCmd.class,
    DesignerXmlCli.TranscodeCmd.class,
    DesignerXmlCli.CfListCatalogsCmd.class,
    DesignerXmlCli.CfListChildObjectsCmd.class,
    DesignerXmlCli.AddMdObjectCmd.class,
    DesignerXmlCli.CfCatalogFormGetCmd.class,
    DesignerXmlCli.CfCatalogFormSetCmd.class,
    DesignerXmlCli.CfConfigurationPropertiesGetCmd.class,
    DesignerXmlCli.CfConfigurationPropertiesSetCmd.class,
    ExternalArtifactCommands.ExternalArtifactAddCmd.class,
    ExternalArtifactCommands.ExternalArtifactPropertiesGetCmd.class,
    ExternalArtifactCommands.ExternalArtifactPropertiesSetCmd.class,
    ExternalArtifactCommands.ExternalArtifactRenameCmd.class,
    ExternalArtifactCommands.ExternalArtifactDeleteCmd.class,
    ExternalArtifactCommands.ExternalArtifactDuplicateCmd.class,
    DesignerXmlCli.CfMdObjectGetCmd.class,
    DesignerXmlCli.CfMdObjectEnumsCmd.class,
    DesignerXmlCli.CfMdObjectStructureGetCmd.class,
    DesignerXmlCli.CfFormContentGetCmd.class,
    DesignerXmlCli.CfFormItemPropertiesCmd.class,
    DesignerXmlCli.CfFormStandardCommandsCmd.class,
    DesignerXmlCli.CfFormItemPropertiesSetCmd.class,
    MdObjectMutationCommands.CfMdObjectSetCmd.class,
    MdObjectMutationCommands.CfMdAttributeAddCmd.class,
    MdObjectMutationCommands.CfMdAttributeRenameCmd.class,
    MdObjectMutationCommands.CfMdAttributeDeleteCmd.class,
    MdObjectMutationCommands.CfMdAttributeDuplicateCmd.class,
    MdObjectMutationCommands.CfMdTabularSectionAddCmd.class,
    MdObjectMutationCommands.CfMdTabularSectionRenameCmd.class,
    MdObjectMutationCommands.CfMdTabularSectionDeleteCmd.class,
    MdObjectMutationCommands.CfMdTabularSectionDuplicateCmd.class,
    MdObjectMutationCommands.CfMdTabularAttributeAddCmd.class,
    MdObjectMutationCommands.CfMdTabularAttributeRenameCmd.class,
    MdObjectMutationCommands.CfMdTabularAttributeDeleteCmd.class,
    MdObjectMutationCommands.CfMdTabularAttributeDuplicateCmd.class,
    MdObjectMutationCommands.CfMdObjectDeleteCmd.class,
    MdObjectMutationCommands.CfMdObjectRenameCmd.class,
    MdObjectMutationCommands.CfMdObjectDuplicateCmd.class,
    ApplyMutationCmd.class,
    ReadJsonCmd.class,
    DesignerXmlCli.InitEmptyCfCmd.class,
    DesignerXmlCli.InitEmptyCfeCmd.class,
    DesignerXmlCli.CfValidateDumpCmd.class,
    DesignerXmlCli.ProjectMetadataTreeCmd.class,
    DesignerXmlCli.CfMdGraphCmd.class
  },
  description = "Чтение/запись Designer XML по XSD (JAXB)."
)
public final class DesignerXmlCli implements Callable<Integer> {

  /** Создаёт корневую команду для picocli. */
  public DesignerXmlCli() {
  }

  /**
   * Версия для {@code --version} из манифеста jar: в коде её держать незачем, она бы отставала
   * от сборки. Вне jar (запуск из классов) версия неизвестна.
   */
  static final class ManifestVersion implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
      String version = DesignerXmlCli.class.getPackage().getImplementationVersion();
      return new String[] {"md-sparrow " + (version == null ? "dev" : version)};
    }
  }

  @Override
  public Integer call() {
    CommandLine.usage(this, System.out);
    return 0;
  }

  /**
   * Точка входа (в т.ч. {@code gradlew run}).
   *
   * @param args аргументы командной строки
   */
  public static void main(String[] args) {
    int exit = new CommandLine(new DesignerXmlCli()).execute(args);
    System.exit(exit);
  }

  @Command(name = "validate", description = "Проверить XML по XSD (корень resources/namespace-forest + каталог schemas/designer/…).")
  static final class ValidateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к .xml")
    Path xml;

    @Parameters(index = "1", description = "Корень submodule resources/namespace-forest")
    Path xsdRoot;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        XmlValidator.validate(xml, version, xsdRoot);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
      return 0;
    }
  }

  @Command(name = "round-trip", description = "Прочитать XML, записать во временный файл (проверка JAXB).")
  static final class RoundTripCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Входной .xml")
    Path input;

    @Parameters(index = "1", description = "Выходной .xml")
    Path output;

    @Option(names = {"-v", "--schema-version"}, required = true)
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      Object root = DesignerXml.read(input, version);
      DesignerXml.write(output, root, version, WriteOptions.defaults());
      System.out.println("Written: " + output.toAbsolutePath());
      return 0;
    }
  }

  @Command(name = "transcode", description = "Пересобрать XML объекта метаданных из одной версии формата в другую.")
  static final class TranscodeCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Входной .xml")
    Path input;

    @Parameters(index = "1", description = "Выходной .xml")
    Path output;

    @Option(names = "--from", required = true, description = "Исходная версия, например V2_20")
    SchemaVersion from;

    @Option(names = "--to", required = true, description = "Целевая версия, например V2_10")
    SchemaVersion to;

    @Override
    public Integer call() throws Exception {
      String xml = io.github.yellowhammer.designerxml.VersionTranscoder.transcode(input, from, to);
      Path parent = output.getParent();
      if (parent != null) {
        java.nio.file.Files.createDirectories(parent);
      }
      java.nio.file.Files.writeString(output, xml, java.nio.charset.StandardCharsets.UTF_8);
      System.out.println("Transcoded " + from + "->" + to + ": " + output.toAbsolutePath());
      return 0;
    }
  }

  @Command(
    name = "cf-list-catalogs",
    description = "Вывести JSON-массив имён справочников из Configuration.xml (ChildObjects/Catalog)."
  )
  static final class CfListCatalogsCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        var names = ConfigurationCatalogLister.listCatalogNames(configurationXml, version);
        System.out.println(ConfigurationCatalogLister.toJsonArray(names));
      } catch (IllegalArgumentException | IllegalStateException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }

  @Command(
    name = "cf-list-child-objects",
    description =
      "Вывести JSON-массив имён из Configuration.xml по тегу ChildObjects (например Catalog, Document, Enum, Constant)."
  )
  static final class CfListChildObjectsCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Option(names = "--tag", required = true, description = "Тег XML: Catalog, Document, Enum, Constant, …")
    String tag;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        var names = ConfigurationChildObjectLister.listNames(configurationXml, version, tag);
        System.out.println(ConfigurationCatalogLister.toJsonArray(names));
      } catch (IllegalArgumentException | IllegalStateException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }

  @Command(
    name = "add-md-object",
    description = "Создать XML объекта метаданных и добавить ссылку в Configuration.xml."
  )
  static final class AddMdObjectCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Parameters(index = "1", arity = "0..1", description = "Имя объекта (идентификатор 1С); не указывать с --auto-name")
    String objectName;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Option(names = "--type", required = true, description = "CATALOG, ENUM, CONSTANT, DOCUMENT, REPORT, DATA_PROCESSOR, TASK, CHART_OF_ACCOUNTS, …")
    String type;

    @Option(names = "--auto-name", description = "Подобрать имя вида ПрефиксN на стороне md-sparrow (без кириллицы в argv)")
    boolean autoName;

    @Option(names = "--synonym-ru", description = "Синоним ru (только для --type CATALOG)")
    String catalogSynonymRu;

    @Option(
      names = "--synonym-empty",
      description = "Пустой синоним ru (только для --type CATALOG; приоритетнее --synonym-ru)")
    boolean catalogSynonymEmpty;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectAddType k = MdObjectAddType.fromCliName(type);
        if (k != MdObjectAddType.CATALOG && (catalogSynonymEmpty || catalogSynonymRu != null)) {
          throw new IllegalArgumentException("--synonym-ru/--synonym-empty поддерживаются только для --type CATALOG");
        }
        if (autoName) {
          if (objectName != null && !objectName.isBlank()) {
            throw new IllegalArgumentException("укажите имя или --auto-name, не оба");
          }
          String name = MdObjectAdd.addWithNextAvailableName(
            configurationXml, version, k, catalogSynonymRu, catalogSynonymEmpty);
          System.out.println(name);
        } else {
          if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("имя объекта обязательно (или --auto-name)");
          }
          MdObjectAdd.add(configurationXml, objectName, version, k, catalogSynonymRu, catalogSynonymEmpty);
          System.out.println("OK");
        }
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

  @Command(
    name = "cf-catalog-form-get",
    description = "Вывести JSON полей справочника."
  )
  static final class CfCatalogFormGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Catalogs/&lt;имя&gt;.xml")
    Path catalogXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        CatalogFormDto dto = CatalogFormEdit.readDto(catalogXml, version);
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

  @Command(
    name = "cf-catalog-form-set",
    description = "Применить JSON полей к справочнику."
  )
  static final class CfCatalogFormSetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Catalogs/&lt;имя&gt;.xml")
    Path catalogXml;

    @Parameters(index = "1", description = "Путь к JSON (как у cf-catalog-form-get)")
    Path jsonFile;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        CatalogFormDto dto = gson.fromJson(json, CatalogFormDto.class);
        if (dto == null) {
          System.err.println("invalid JSON");
          return 2;
        }
        CatalogFormEdit.writeDto(catalogXml, version, dto);
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

  @Command(
    name = "cf-md-object-get",
    description = "Вывести JSON свойств объекта метаданных."
  )
  static final class CfMdObjectGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml или .mdo")
    Path objectXml;

    @Option(names = {"-v", "--schema-version"}, description = "Версия формата выгрузки конфигуратора, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectPropertiesDto dto = EdtLayout.isObjectFile(objectXml)
          ? EdtObjectProperties.readDto(objectXml, EdtModel.bundled())
          : MdObjectPropertiesEdit.readDto(objectXml, requireSchemaVersion(version));
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

  /** Версия нужна только выгрузке конфигуратора: у проекта EDT её место занимают схемы метамодели. */
  static SchemaVersion requireSchemaVersion(SchemaVersion version) {
    if (version == null) {
      throw new IllegalArgumentException("Для выгрузки конфигуратора укажите версию формата: --schema-version");
    }
    return version;
  }

  @Command(
    name = "cf-md-object-enums",
    description = "Вывести JSON допустимых значений перечислимых свойств объектов метаданных."
  )
  static final class CfMdObjectEnumsCmd implements Callable<Integer> {
    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() {
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      System.out.println(gson.toJson(MdObjectPropertyEnums.forVersion(version)));
      return 0;
    }
  }

  @Command(
    name = "cf-md-object-structure-get",
    description = "Вывести JSON структуры объекта метаданных."
  )
  static final class CfMdObjectStructureGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml или .mdo")
    Path objectXml;

    @Option(names = {"-v", "--schema-version"}, description = "Версия формата выгрузки конфигуратора, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectStructureDto dto = EdtLayout.isObjectFile(objectXml)
          ? EdtObjectStructure.read(objectXml, EdtModel.bundled())
          : MdObjectStructureRead.read(objectXml, requireSchemaVersion(version));
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

  @Command(
    name = "cf-form-item-properties",
    description = "Вывести JSON состава свойств видов элементов формы."
  )
  static final class CfFormItemPropertiesCmd implements Callable<Integer> {
    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() {
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      System.out.println(gson.toJson(FormItemPropertyDictionary.forVersion(version)));
      return 0;
    }
  }

  @Command(
    name = "cf-form-standard-commands",
    description = "Вывести JSON того, что платформа знает про стандартные команды формы."
  )
  static final class CfFormStandardCommandsCmd implements Callable<Integer> {
    @Override
    public Integer call() {
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      System.out.println(gson.toJson(StandardCommandLabels.dto()));
      return 0;
    }
  }

  @Command(
    name = "cf-form-content-get",
    description = "Вывести JSON содержимого управляемой формы (Ext/Form.xml)."
  )
  static final class CfFormContentGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Ext/Form.xml")
    Path formXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        FormContentDto dto = FormContentRead.read(formXml, version);
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

  @Command(
    name = "cf-form-item-properties-set",
    description = "Записать свойства элементов управляемой формы точечно (Ext/Form.xml)."
  )
  static final class CfFormItemPropertiesSetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Ext/Form.xml")
    Path formXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Option(names = "--changes", required = true, description = "Путь к UTF-8 JSON: массив {itemId, property, value}")
    Path changesJson;

    @Override
    public Integer call() throws Exception {
      try {
        String json = Files.readString(changesJson, StandardCharsets.UTF_8);
        FormItemPropertyChangeDto[] changes = new Gson().fromJson(json, FormItemPropertyChangeDto[].class);
        if (changes == null) {
          System.err.println("пустой список изменений");
          return 2;
        }
        FormItemPropertyEdit.apply(formXml, version, java.util.Arrays.asList(changes));
        System.out.println("OK");
      } catch (IllegalArgumentException | IllegalStateException e) {
        System.err.println(e.getMessage());
        return 2;
      } catch (IOException | JAXBException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }

  @Command(
    name = "cf-configuration-properties-get",
    description = "Вывести JSON свойств Configuration.xml (для основной конфигурации и расширения)."
  )
  static final class CfConfigurationPropertiesGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(configurationXml, version);
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

  @Command(
    name = "cf-configuration-properties-set",
    description = "Применить JSON (как у cf-configuration-properties-get) к Configuration.xml."
  )
  static final class CfConfigurationPropertiesSetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Parameters(index = "1", description = "Путь к JSON")
    Path jsonFile;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        ConfigurationPropertiesDto dto = gson.fromJson(json, ConfigurationPropertiesDto.class);
        if (dto == null) {
          System.err.println("invalid JSON");
          return 2;
        }
        ConfigurationPropertiesEdit.write(configurationXml, version, dto);
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

  @Command(
    name = "validate-dump",
    description = "Проверить целостность выгрузки: состав, ссылки, версии формата; результат — JSON находок в stdout."
  )
  static final class CfValidateDumpCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Каталог выгрузки (src/cf или каталог расширения)")
    Path cfRoot;

    @Override
    public Integer call() throws Exception {
      var findings = io.github.yellowhammer.designerxml.cf.CfDumpValidation.validate(cfRoot);
      System.out.println(new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(findings));
      return 0;
    }
  }

  @Command(
    name = "init-empty-cf",
    description = "Инициализировать каталог пустой выгрузки конфигурации."
  )
  static final class InitEmptyCfCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Каталог целевой выгрузки src/cf")
    Path targetCfRoot;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_17 (V2_10…V2_21)")
    SchemaVersion version;

    @Option(
      names = "--name",
      description = "Имя конфигурации; по умолчанию «" + CfLayout.DEFAULT_CONFIGURATION_NAME + "»"
    )
    String configurationName;

    @Option(names = "--synonym-ru", description = "Синоним ru; по умолчанию пусто")
    String synonymRu;

    @Option(names = "--vendor", description = "Поставщик; по умолчанию пусто")
    String vendor;

    @Option(names = "--app-version", description = "Версия в Properties; по умолчанию пусто")
    String appVersion;

    @Override
    public Integer call() throws Exception {
      try {
        String name =
          configurationName == null || configurationName.isEmpty()
            ? CfLayout.DEFAULT_CONFIGURATION_NAME
            : configurationName;
        io.github.yellowhammer.designerxml.cf.EmptyCfScaffold.writeEmptyTree(
          targetCfRoot, name, synonymRu, vendor, appVersion, version);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK: " + targetCfRoot.toAbsolutePath());
      return 0;
    }
  }

  @Command(
    name = "init-empty-cfe",
    description = "Инициализировать каталог пустого расширения конфигурации."
  )
  static final class InitEmptyCfeCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Каталог расширения src/cfe/<Имя>")
    Path targetCfeRoot;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "Версия формата, например V2_20")
    SchemaVersion version;

    @Option(names = "--name", required = true, description = "Имя расширения")
    String extensionName;

    @Option(names = "--name-prefix", description = "Префикс имён объектов расширения; по умолчанию пусто, как у платформы")
    String namePrefix;

    @Option(
      names = "--purpose",
      description = "Назначение: patch, customization, add-on; по умолчанию customization"
    )
    String purpose;

    @Option(
      names = "--compatibility-mode",
      description = "Режим совместимости расширения, например Version8_3_24; берётся из основной конфигурации"
    )
    String compatibilityMode;

    @Option(
      names = "--interface-compatibility-mode",
      description = "Режим совместимости интерфейса, например TaxiEnableVersion8_2"
    )
    String interfaceCompatibilityMode;

    @Option(
      names = "--from-configuration",
      description = "Configuration.xml расширяемой конфигурации: режимы совместимости берутся из неё"
    )
    Path mainConfigurationXml;

    @Option(names = "--synonym-ru", description = "Синоним ru; по умолчанию имя расширения")
    String synonymRu;

    @Override
    public Integer call() throws Exception {
      try {
        io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose purposeValue =
          purpose == null || purpose.isBlank()
            ? io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose.CUSTOMIZATION
            : io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose.fromCliName(purpose);
        if (mainConfigurationXml != null) {
          io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
            targetCfeRoot, extensionName, synonymRu, namePrefix, purposeValue, mainConfigurationXml, version);
        } else {
          io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.writeEmptyTree(
            targetCfeRoot,
            extensionName,
            synonymRu,
            namePrefix,
            purposeValue,
            compatibilityMode,
            interfaceCompatibilityMode,
            version);
        }
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK: " + targetCfeRoot.toAbsolutePath());
      return 0;
    }
  }

  @Command(
    name = "project-metadata-tree",
    description = "Дерево метаданных проекта (src/cf, расширения, внешние отчёты/обработки) в JSON."
  )
  static final class ProjectMetadataTreeCmd implements Callable<Integer> {
    @Parameters(
      index = "0",
      description = "Корень проекта (с src/cf и опционально src/cfe/*, src/erf/*, src/epf/*)"
    )
    Path projectRoot;

    @Option(names = "--pretty", description = "Форматировать JSON")
    boolean pretty;

    @Override
    public Integer call() throws Exception {
      try {
        ProjectMetadataTreeDto dto = ProjectMetadataTreeBuilder.build(projectRoot);
        Gson gson =
          pretty
            ? new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
            : new GsonBuilder().disableHtmlEscaping().create();
        System.out.println(gson.toJson(dto));
      } catch (Exception e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }

  @Command(
    name = "cf-md-graph",
    description = "Граф метаданных проекта (узлы + типизированные связи) "
      + "по основной выгрузке src/cf, расширениям src/cfe/* и внешним отчётам/обработкам src/erf/*, src/epf/* в JSON."
  )
  static final class CfMdGraphCmd implements Callable<Integer> {
    @Parameters(
      index = "0",
      description = "Корень проекта (с src/cf и опционально src/cfe/*, src/erf/*, src/epf/*)"
    )
    Path projectRoot;

    @Option(names = "--pretty", description = "Форматировать JSON")
    boolean pretty;

    @Override
    public Integer call() throws Exception {
      try {
        ProjectMetadataGraphDto dto = ProjectMetadataGraphBuilder.build(projectRoot);
        Gson gson =
          pretty
            ? new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
            : new GsonBuilder().disableHtmlEscaping().create();
        System.out.println(gson.toJson(dto));
      } catch (Exception e) {
        System.err.println(e.getMessage());
        return 2;
      }
      return 0;
    }
  }
}
