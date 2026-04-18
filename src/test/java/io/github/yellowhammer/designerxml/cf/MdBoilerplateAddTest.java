/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MdObjectAddTest {

  @TempDir
  Path workspace;

  @Test
  void addEnumAfterEmptyCfV220() throws Exception {
    Path cf = workspace.resolve("cf");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "_ПеречислениеТест";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.ENUM);
    Path out = CfLayout.objectXmlInSubdir(cf, "Enums", name);
    assertThat(out).exists();
    DesignerXml.read(out, SchemaVersion.V2_20);
    assertThat(Files.readString(cfg)).contains("<Enum>" + name + "</Enum>");
  }

  @Test
  void addConstantAfterEmptyCfV221() throws Exception {
    Path cf = workspace.resolve("cf221");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "_КонстантаТест";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_21, MdObjectAddType.CONSTANT);
    Path out = CfLayout.objectXmlInSubdir(cf, "Constants", name);
    assertThat(out).exists();
    DesignerXml.read(out, SchemaVersion.V2_21);
    assertThat(Files.readString(cfg)).contains("<Constant>" + name + "</Constant>");
    String xml = Files.readString(out);
    assertThat(xml).contains("<xr:GeneratedType name=\"ConstantValueManager." + name + "\" category=\"ValueManager\">");
    assertThat(xml).contains("<xr:GeneratedType name=\"ConstantValueKey." + name + "\" category=\"ValueKey\">");
    assertThat(xml).contains("<v8:Type>xs:string</v8:Type>");
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<QuickChoice>Auto</QuickChoice>");
  }

  @Test
  void addObjectIsDeterministicForSameInputV220() throws Exception {
    Path cfA = workspace.resolve("cfA");
    Path cfB = workspace.resolve("cfB");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfB, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);

    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);
    String name = "_ОтчетДетерминизм";

    MdObjectAdd.add(cfgA, name, SchemaVersion.V2_20, MdObjectAddType.REPORT);
    MdObjectAdd.add(cfgB, name, SchemaVersion.V2_20, MdObjectAddType.REPORT);

    Path outA = CfLayout.objectXmlInSubdir(cfA, "Reports", name);
    Path outB = CfLayout.objectXmlInSubdir(cfB, "Reports", name);
    assertThat(outA).exists();
    assertThat(outB).exists();
    assertThat(Files.readString(outA)).isEqualTo(Files.readString(outB));
    assertThat(Files.readString(cfgA)).isEqualTo(Files.readString(cfgB));
  }

  @Test
  void addObjectAllTypesReadableV220() throws Exception {
    Path cf = workspace.resolve("cfAllKinds");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);

    List<MdObjectAddType> types = List.of(MdObjectAddType.values());
    int idx = 1000;
    for (MdObjectAddType type : types) {
      String name = type.namePrefix() + idx++;
      MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, type);
      Path out = CfObjectPathResolver.objectXml(cf, type.configurationXmlTag(), name).orElseThrow();
      assertThat(out).exists();
      DesignerXml.read(out, SchemaVersion.V2_20);
    }
  }

  @Test
  void addDocumentProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfDocument");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "Документ1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.DOCUMENT);
    Path out = CfLayout.objectXmlInSubdir(cf, "Documents", name);
    String xml = Files.readString(out);
    assertThat(xml).contains("<InputByString>");
    assertThat(xml).contains("<xr:Field>Document." + name + ".StandardAttribute.Number</xr:Field>");
    assertThat(xml).contains("<Posting>Allow</Posting>");
    assertThat(xml).contains("<RealTimePosting>Allow</RealTimePosting>");
    assertThat(xml).contains("<RegisterRecordsDeletion>AutoDeleteOnUnpost</RegisterRecordsDeletion>");
    assertThat(xml).contains("<SequenceFilling>AutoFill</SequenceFilling>");
  }

  @Test
  void addReportProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfReport");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "Отчет1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.REPORT);
    String xml = Files.readString(CfLayout.objectXmlInSubdir(cf, "Reports", name));
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<MainDataCompositionSchema/>");
    assertThat(xml).contains("<IncludeHelpInContents>false</IncludeHelpInContents>");
    assertThat(xml).contains("<ExtendedPresentation/>");
  }

  @Test
  void addDataProcessorProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfDataProcessor");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "Обработка1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.DATA_PROCESSOR);
    String xml = Files.readString(CfLayout.objectXmlInSubdir(cf, "DataProcessors", name));
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<AuxiliaryForm/>");
    assertThat(xml).contains("<ExtendedPresentation/>");
    assertThat(xml).contains("<Explanation/>");
  }

  @Test
  void addEnumProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfEnum");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "Перечисление1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.ENUM);
    String xml = Files.readString(CfLayout.objectXmlInSubdir(cf, "Enums", name));
    assertThat(xml).contains("<UseStandardCommands>false</UseStandardCommands>");
    assertThat(xml).contains("<QuickChoice>true</QuickChoice>");
    assertThat(xml).contains("<ChoiceMode>BothWays</ChoiceMode>");
    assertThat(xml).contains("<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>");
  }

  @Test
  void addExternalDataSourceProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfExternalDataSource");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "ВнешнийИсточникДанных1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.EXTERNAL_DATA_SOURCE);
    String xml = Files.readString(CfLayout.objectXmlInSubdir(cf, "ExternalDataSources", name));
    assertThat(xml).contains("ExternalDataSourceTablesManager." + name);
    assertThat(xml).contains("ExternalDataSourceCubesManager." + name);
    assertThat(xml).contains("<DataLockControlMode>Automatic</DataLockControlMode>");
  }

  @Test
  void addTaskProfileIsSnapshotLikeV220() throws Exception {
    Path cf = workspace.resolve("cfTask");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    String name = "Задача1";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.TASK);
    String xml = Files.readString(CfLayout.objectXmlInSubdir(cf, "Tasks", name));
    assertThat(xml).contains("<TaskNumberAutoPrefix>DontUse</TaskNumberAutoPrefix>");
    assertThat(xml).contains("<DescriptionLength>25</DescriptionLength>");
    assertThat(xml).contains("<DefaultPresentation>AsDescription</DefaultPresentation>");
    assertThat(xml).contains("<xr:Field>Task." + name + ".StandardAttribute.Description</xr:Field>");
    assertThat(xml).contains("<FullTextSearch>Use</FullTextSearch>");
  }

  @Test
  void addObjectAllTypesDeterministicV221() throws Exception {
    Path cfA = workspace.resolve("cfA221");
    Path cfB = workspace.resolve("cfB221");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfB, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);
    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);

    List<MdObjectAddType> types = List.of(MdObjectAddType.values());
    int idx = 2000;
    for (MdObjectAddType type : types) {
      String name = type.namePrefix() + idx++;
      MdObjectAdd.add(cfgA, name, SchemaVersion.V2_21, type);
      MdObjectAdd.add(cfgB, name, SchemaVersion.V2_21, type);
      Path outA = CfObjectPathResolver.objectXml(cfA, type.configurationXmlTag(), name).orElseThrow();
      Path outB = CfObjectPathResolver.objectXml(cfB, type.configurationXmlTag(), name).orElseThrow();
      assertThat(Files.readString(outA))
        .as("детерминированный вывод %s", type)
        .isEqualTo(Files.readString(outB));
      DesignerXml.read(outA, SchemaVersion.V2_21);
    }
  }

  @Test
  void addFixtureMatrixCoversAllAddKinds() {
    long withFixtures = AddGoldenFixtureMatrix.withFixtures().stream()
      .filter(e -> e.command().equals("add-md-object"))
      .count();
    assertThat(withFixtures).isEqualTo(MdObjectAddType.values().length);

    long externalWithoutFixture = AddGoldenFixtureMatrix.withoutFixtures().stream()
      .filter(e -> e.command().equals("external-artifact-add"))
      .count();
    assertThat(externalWithoutFixture).isEqualTo(2);
  }

}
