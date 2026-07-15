/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Добавление объектов в пустую выгрузку: файл появляется, регистрируется в {@code Configuration.xml},
 * читается моделью своей версии и не зависит от запуска. Проверяем в каждом поддерживаемом формате.
 *
 * <p>Профиль объекта (набор элементов «как у конфигуратора») сверяется с эталоном той же версии,
 * поэтому такие проверки тоже параметризованы.
 */
class MdObjectAddTest {

  @TempDir
  Path workspace;

  private Path emptyCfg(SchemaVersion version, String dirName) throws Exception {
    Path cf = workspace.resolve(dirName + "-" + version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    return cf.resolve(CfLayout.CONFIGURATION_XML);
  }

  private static Path addAndRead(Path cfg, String subdir, String name, SchemaVersion version, MdObjectAddType type)
    throws Exception {
    MdObjectAdd.add(cfg, name, version, type);
    Path out = CfLayout.objectXmlInSubdir(cfg.getParent(), subdir, name);
    assertThat(out).exists();
    DesignerXml.read(out, version);
    return out;
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addEnumAfterEmptyCf(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cf");
    String name = "_ПеречислениеТест";
    addAndRead(cfg, "Enums", name, version, MdObjectAddType.ENUM);
    assertThat(Files.readString(cfg)).contains("<Enum>" + name + "</Enum>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addConstantAfterEmptyCf(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfConstant");
    String name = "_КонстантаТест";
    Path out = addAndRead(cfg, "Constants", name, version, MdObjectAddType.CONSTANT);
    assertThat(Files.readString(cfg)).contains("<Constant>" + name + "</Constant>");
    String xml = Files.readString(out);
    assertThat(xml).contains("<xr:GeneratedType name=\"ConstantValueManager." + name + "\" category=\"ValueManager\">");
    assertThat(xml).contains("<xr:GeneratedType name=\"ConstantValueKey." + name + "\" category=\"ValueKey\">");
    assertThat(xml).contains("<v8:Type>xs:string</v8:Type>");
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<QuickChoice>Auto</QuickChoice>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addObjectAllTypesReadableAndDeterministic(SchemaVersion version) throws Exception {
    Path cfgA = emptyCfg(version, "cfA");
    Path cfgB = emptyCfg(version, "cfB");
    List<MdObjectAddType> types = List.of(MdObjectAddType.values());
    int idx = 1000;
    for (MdObjectAddType type : types) {
      String name = type.namePrefix() + idx++;
      MdObjectAdd.add(cfgA, name, version, type);
      MdObjectAdd.add(cfgB, name, version, type);
      Path outA = CfObjectPathResolver.objectXml(cfgA.getParent(), type.configurationXmlTag(), name).orElseThrow();
      Path outB = CfObjectPathResolver.objectXml(cfgB.getParent(), type.configurationXmlTag(), name).orElseThrow();
      assertThat(Files.readString(outA)).as("детерминированный вывод %s", type).isEqualTo(Files.readString(outB));
      DesignerXml.read(outA, version);
    }
    assertThat(Files.readString(cfgA)).isEqualTo(Files.readString(cfgB));
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addWithNextAvailableNamePicksDocument1OnEmptyCf(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfAutoDocument");
    String name = MdObjectAdd.addWithNextAvailableName(cfg, version, MdObjectAddType.DOCUMENT, null, false);
    assertThat(name).isEqualTo("Документ1");
    assertThat(CfLayout.objectXmlInSubdir(cfg.getParent(), "Documents", name)).exists();
    assertThat(Files.readString(cfg)).contains("<Document>" + name + "</Document>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addDocumentProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfDocument");
    String name = "Документ1";
    String xml = Files.readString(addAndRead(cfg, "Documents", name, version, MdObjectAddType.DOCUMENT));
    assertThat(xml).contains("<InputByString>");
    assertThat(xml).contains("<xr:Field>Document." + name + ".StandardAttribute.Number</xr:Field>");
    assertThat(xml).contains("<Posting>Allow</Posting>");
    assertThat(xml).contains("<RealTimePosting>Allow</RealTimePosting>");
    assertThat(xml).contains("<RegisterRecordsDeletion>AutoDeleteOnUnpost</RegisterRecordsDeletion>");
    assertThat(xml).contains("<SequenceFilling>AutoFill</SequenceFilling>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addReportProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfReport");
    String xml = Files.readString(addAndRead(cfg, "Reports", "Отчет1", version, MdObjectAddType.REPORT));
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<MainDataCompositionSchema/>");
    assertThat(xml).contains("<IncludeHelpInContents>false</IncludeHelpInContents>");
    assertThat(xml).contains("<ExtendedPresentation/>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addDataProcessorProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfDataProcessor");
    String xml = Files.readString(addAndRead(cfg, "DataProcessors", "Обработка1", version, MdObjectAddType.DATA_PROCESSOR));
    assertThat(xml).contains("<DefaultForm/>");
    assertThat(xml).contains("<AuxiliaryForm/>");
    assertThat(xml).contains("<ExtendedPresentation/>");
    assertThat(xml).contains("<Explanation/>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addEnumProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfEnumProfile");
    String xml = Files.readString(addAndRead(cfg, "Enums", "Перечисление1", version, MdObjectAddType.ENUM));
    assertThat(xml).contains("<UseStandardCommands>false</UseStandardCommands>");
    assertThat(xml).contains("<QuickChoice>true</QuickChoice>");
    assertThat(xml).contains("<ChoiceMode>BothWays</ChoiceMode>");
    assertThat(xml).contains("<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addExternalDataSourceProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfExternalDataSource");
    String name = "ВнешнийИсточникДанных1";
    String xml = Files.readString(addAndRead(cfg, "ExternalDataSources", name, version, MdObjectAddType.EXTERNAL_DATA_SOURCE));
    assertThat(xml).contains("ExternalDataSourceTablesManager." + name);
    assertThat(xml).contains("ExternalDataSourceCubesManager." + name);
    assertThat(xml).contains("<DataLockControlMode>Automatic</DataLockControlMode>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void addTaskProfileIsSnapshotLike(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfTask");
    String name = "Задача1";
    String xml = Files.readString(addAndRead(cfg, "Tasks", name, version, MdObjectAddType.TASK));
    assertThat(xml).contains("<TaskNumberAutoPrefix>DontUse</TaskNumberAutoPrefix>");
    assertThat(xml).contains("<DescriptionLength>25</DescriptionLength>");
    assertThat(xml).contains("<DefaultPresentation>AsDescription</DefaultPresentation>");
    assertThat(xml).contains("<xr:Field>Task." + name + ".StandardAttribute.Description</xr:Field>");
    assertThat(xml).contains("<FullTextSearch>Use</FullTextSearch>");
  }
}
