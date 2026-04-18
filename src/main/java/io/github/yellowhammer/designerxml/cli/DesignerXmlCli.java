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

import io.github.yellowhammer.designerxml.cf.AddCatalog;
import io.github.yellowhammer.designerxml.cf.CatalogFormDto;
import io.github.yellowhammer.designerxml.cf.CatalogFormEdit;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;
import io.github.yellowhammer.designerxml.cf.ConfigurationCatalogLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationChildObjectLister;
import io.github.yellowhammer.designerxml.cf.CfLayout;
import io.github.yellowhammer.designerxml.cf.MdObjectAdd;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.NewConfigurationXml;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;
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
  version = "md-sparrow 0.1",
  subcommands = {
    DesignerXmlCli.ValidateCmd.class,
    DesignerXmlCli.RoundTripCmd.class,
    DesignerXmlCli.AddCatalogCmd.class,
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
    DesignerXmlCli.CfMdObjectStructureGetCmd.class,
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
    DesignerXmlCli.InitEmptyCfCmd.class,
    DesignerXmlCli.ProjectMetadataTreeCmd.class
  },
  description = "Чтение/запись Designer XML по XSD (JAXB)."
)
public final class DesignerXmlCli implements Callable<Integer> {

  /** Создаёт корневую команду для picocli. */
  public DesignerXmlCli() {
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

  @Command(name = "validate", description = "Проверить XML по XSD (корень namespace-forest + каталог schemas/…).")
  static final class ValidateCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к .xml")
    Path xml;

    @Parameters(index = "1", description = "Корень репозитория namespace-forest (submodule)")
    Path xsdRoot;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Option(names = "--catalog", description = "Путь к catalog.xml (если не лежит рядом с XSD в submodule)")
    Path catalog;

    @Override
    public Integer call() throws Exception {
      try {
        XmlValidator.validate(xml, version, xsdRoot, catalog);
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

  @Command(
    name = "add-catalog",
    description = "Создать справочник и добавить его в Configuration.xml."
  )
  static final class AddCatalogCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml (каталог src/cf)")
    Path configurationXml;

    @Parameters(index = "1", description = "Имя справочника (идентификатор 1С)")
    String catalogName;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Option(names = "--synonym-ru", description = "Синоним (ru); по умолчанию совпадает с именем")
    String synonymRu;

    @Option(
      names = "--synonym-empty",
      description = "Пустой синоним ru (как в эталонной выгрузке); приоритетнее --synonym-ru")
    boolean synonymEmpty;

    @Override
    public Integer call() throws Exception {
      try {
        AddCatalog.add(configurationXml, catalogName, synonymRu, synonymEmpty, version);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK");
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

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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

    @Parameters(index = "1", description = "Имя объекта (идентификатор 1С)")
    String objectName;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Option(names = "--type", required = true, description = "ENUM, CONSTANT, DOCUMENT, REPORT, DATA_PROCESSOR, TASK, CHART_OF_ACCOUNTS, …")
    String type;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectAddType k = MdObjectAddType.fromCliName(type);
        MdObjectAdd.add(configurationXml, objectName, version, k);
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
    name = "cf-catalog-form-get",
    description = "Вывести JSON полей справочника."
  )
  static final class CfCatalogFormGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Catalogs/&lt;имя&gt;.xml")
    Path catalogXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(objectXml, version);
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
    name = "cf-md-object-structure-get",
    description = "Вывести JSON структуры объекта метаданных."
  )
  static final class CfMdObjectStructureGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к MetaDataObject .xml")
    Path objectXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
    SchemaVersion version;

    @Override
    public Integer call() throws Exception {
      try {
        MdObjectStructureDto dto = MdObjectStructureRead.read(objectXml, version);
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
    name = "cf-configuration-properties-get",
    description = "Вывести JSON свойств Configuration.xml (для основной конфигурации и расширения)."
  )
  static final class CfConfigurationPropertiesGetCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Путь к Configuration.xml")
    Path configurationXml;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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
    name = "init-empty-cf",
    description = "Инициализировать каталог пустой выгрузки конфигурации."
  )
  static final class InitEmptyCfCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Каталог целевой выгрузки src/cf")
    Path targetCfRoot;

    @Option(names = {"-v", "--schema-version"}, required = true, description = "V2_20 | V2_21")
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
        NewConfigurationXml.writeConfiguratorEmptyTree(targetCfRoot, name, synonymRu, vendor, appVersion, version);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 2;
      }
      System.out.println("OK: " + targetCfRoot.toAbsolutePath());
      return 0;
    }
  }

  @Command(
    name = "project-metadata-tree",
    description = "Дерево метаданных проекта (src/cf, расширения, внешние отчёты/обработки) в JSON."
  )
  static final class ProjectMetadataTreeCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Корень проекта (каталог с src/cf)")
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
}
