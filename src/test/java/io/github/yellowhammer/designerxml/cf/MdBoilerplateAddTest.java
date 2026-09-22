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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Добавление объектов в пустую выгрузку: файл появляется, регистрируется в {@code Configuration.xml},
 * читается моделью своей версии и не зависит от запуска. Проверяем в каждом поддерживаемом формате.
 *
 * <p>Профиль объекта (набор элементов «как у конфигуратора») сверяется с эталоном той же версии,
 * поэтому такие проверки тоже параметризованы.
 */
class MdObjectAddTest {

  private static final Pattern UUID_TOKEN = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String CLASS_ID_OPEN_END = "ClassId>";

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
  void повторноеСозданиеИмениПослеПереименованияДаётНовыеИдентификаторы(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfUuid");
    String initialName = MdObjectAdd.addWithNextAvailableName(cfg, version, MdObjectAddType.CATALOG, null, false);
    Path initialXml = CfLayout.catalogObjectXml(cfg.getParent(), initialName);
    String renamedName = "Склады";
    CfMdObjectMutations.rename(cfg, initialXml, "Catalog", initialName, renamedName);

    String repeatedName = MdObjectAdd.addWithNextAvailableName(cfg, version, MdObjectAddType.CATALOG, null, false);
    Set<String> firstIdentifiers = nonClassUuids(CfLayout.catalogObjectXml(cfg.getParent(), renamedName));
    Set<String> secondIdentifiers = nonClassUuids(CfLayout.catalogObjectXml(cfg.getParent(), repeatedName));

    assertThat(repeatedName).isEqualTo(initialName);
    assertThat(secondIdentifiers).isNotEmpty().doesNotContainAnyElementsOf(firstIdentifiers);
  }

  /**
   * Сценарий из отчётов о переименовании и повторном создании: имена порождаемых типов
   * и поля ввода по строке переходят на новое имя, а {@code TypeId}/{@code ValueId} остаются.
   * Второй справочник получает другие идентификаторы.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void переименованиеИВторойСправочникНеДелятИдентификаторыТипов(SchemaVersion version) throws Exception {
    Path cfg = emptyCfg(version, "cfRename");
    String initialName = MdObjectAdd.addWithNextAvailableName(cfg, version, MdObjectAddType.CATALOG, null, false);
    Path initialXml = CfLayout.catalogObjectXml(cfg.getParent(), initialName);
    String before = Files.readString(initialXml);
    Set<String> typeIds = elementValues(before, "TypeId");
    Set<String> valueIds = elementValues(before, "ValueId");

    String renamedName = "Склады";
    CfMdObjectMutations.rename(cfg, initialXml, "Catalog", initialName, renamedName);
    String renamed = Files.readString(CfLayout.catalogObjectXml(cfg.getParent(), renamedName));
    assertThat(elementValues(renamed, "TypeId")).isEqualTo(typeIds);
    assertThat(elementValues(renamed, "ValueId")).isEqualTo(valueIds);
    assertThat(generatedTypeNames(renamed)).containsExactlyInAnyOrderElementsOf(
      MdObjectAddType.CATALOG.generatedTypeCategories().stream()
        .map(category -> "Catalog" + category + "." + renamedName)
        .toList());
    assertThat(renamed).contains("<xr:Field>Catalog." + renamedName + ".StandardAttribute.Description</xr:Field>");
    assertThat(renamed).contains("<v8:content>" + initialName + "</v8:content>");

    String secondName = MdObjectAdd.addWithNextAvailableName(cfg, version, MdObjectAddType.CATALOG, null, false);
    CfMdObjectMutations.rename(cfg, CfLayout.catalogObjectXml(cfg.getParent(), secondName), "Catalog", secondName, "Номенклатура");
    String second = Files.readString(CfLayout.catalogObjectXml(cfg.getParent(), "Номенклатура"));
    assertThat(elementValues(second, "TypeId")).doesNotContainAnyElementsOf(typeIds);
    assertThat(elementValues(second, "ValueId")).doesNotContainAnyElementsOf(valueIds);
    assertThat(generatedTypeNames(second)).contains("CatalogObject.Номенклатура", "CatalogRef.Номенклатура");
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

  private static List<String> generatedTypeNames(String xml) {
    return Pattern.compile("<(?:[\\w.-]+:)?GeneratedType\\s+name=\"([^\"]+)\"")
      .matcher(xml).results().map(match -> match.group(1)).toList();
  }

  private static Set<String> elementValues(String xml, String localName) {
    Set<String> values = new LinkedHashSet<>();
    Matcher matcher = Pattern.compile(
      "<(?:[\\w.-]+:)?" + localName + ">([^<]*)</(?:[\\w.-]+:)?" + localName + ">").matcher(xml);
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }

  private static Set<String> nonClassUuids(Path xmlFile) throws Exception {
    String xml = Files.readString(xmlFile);
    Set<String> identifiers = new LinkedHashSet<>();
    Matcher matcher = UUID_TOKEN.matcher(xml);
    while (matcher.find()) {
      int openEnd = matcher.start() - CLASS_ID_OPEN_END.length();
      if (openEnd < 0 || !xml.regionMatches(openEnd, CLASS_ID_OPEN_END, 0, CLASS_ID_OPEN_END.length())) {
        identifiers.add(matcher.group());
      }
    }
    return identifiers;
  }
}
