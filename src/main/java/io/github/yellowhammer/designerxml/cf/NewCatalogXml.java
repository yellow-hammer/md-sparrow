/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBElement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Минимальный справочник без файла-прототипа в {@code Catalogs/}: только JAXB по XSD
 * (см. {@link NewConfigurationXml} для конфигурации).
 *
 * <p><b>Почему два больших блока (2_20 / 2_21), а не один:</b> XJC кладёт классы в разные пакеты
 * ({@code jaxb.v2_20.*} и {@code jaxb.v2_21.*}) — это разные типы, общий заполняющий код без копий
 * или отдельного слоя абстракции не собрать. Число поддерживаемых версий задаётся
 * {@link SchemaVersion}; при появлении новой схемы добавляется ещё одна ветка (или вынос в
 * отдельный класс по версии). Масштабирование «на 20 версий» в проде обычно не делают: либо узкий
 * набор актуальных форматов, либо генерация/шаблоны поверх XSD.
 */
final class NewCatalogXml {

  /** Префиксы имён в {@code InternalInfo/GeneratedType} — порядок как у платформы. */
  private static final String[] CATALOG_GENERATED_TYPE_PREFIXES = {
    "CatalogObject",
    "CatalogRef",
    "CatalogSelection",
    "CatalogList",
    "CatalogManager",
  };

  private static final io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories[]
    CATALOG_GEN_CATEGORIES_V220 = {
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.OBJECT,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.REF,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.SELECTION,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.LIST,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.MANAGER,
  };

  private static final io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories[]
    CATALOG_GEN_CATEGORIES_V221 = {
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.OBJECT,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.REF,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.SELECTION,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.LIST,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.MANAGER,
  };

  /** Семантика стандартных реквизитов (значения перечислений JAXB маппятся отдельно на 2_20 / 2_21). */
  private enum StdFill {
    DONT_CHECK,
    SHOW_ERROR,
  }

  private enum StdTr {
    TRANSFORM_VALUES,
    DENY,
  }

  private record StdAttrRow(String name, StdFill fill, boolean fillFromFillingValue, StdTr tr) {
  }

  private static final List<StdAttrRow> STD_CATALOG_ATTRS = List.of(
    new StdAttrRow("PredefinedDataName", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("Predefined", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("Ref", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("DeletionMark", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("IsFolder", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("Owner", StdFill.SHOW_ERROR, true, StdTr.DENY),
    new StdAttrRow("Parent", StdFill.DONT_CHECK, true, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("Description", StdFill.SHOW_ERROR, false, StdTr.TRANSFORM_VALUES),
    new StdAttrRow("Code", StdFill.DONT_CHECK, false, StdTr.TRANSFORM_VALUES)
  );

  private NewCatalogXml() {
  }

  /**
   * Корень {@code MetaDataObject} со справочником для последующей мутации/маршалинга
   * ({@link CatalogXmlEmitter}).
   */
  /**
   * @param resolvedSynonymRu итоговый ru-текст для полей синонима/представлений (может быть {@code ""});
   *                        правила «пусто → имя» задаёт {@link CatalogXmlEmitter}
   */
  static Object newCatalogRoot(SchemaVersion version, String catalogName, String resolvedSynonymRu) {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(catalogName, "catalogName");
    Objects.requireNonNull(resolvedSynonymRu, "resolvedSynonymRu");
    CatalogNameConstraints.check(catalogName);
    return switch (version) {
      case V2_20 -> rootV220(catalogName, resolvedSynonymRu);
      case V2_21 -> rootV221(catalogName, resolvedSynonymRu);
    };
  }

  private static JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject> rootV220(
    String catalogName,
    String synonymRu) {
    String seed = "newCatalog|V2_20|" + catalogName;
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());

    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Catalog cat = factory.createCatalog();
    cat.setUuid(GoldenUuid.from(seed, "catalog.uuid"));
    cat.setInternalInfo(internalInfoV220(catalogName, seed + "|internalInfo"));
    cat.setProperties(fillPropertiesV220(factory, catalogName, synonymRu));
    cat.setChildObjects(factory.createCatalogChildObjects());

    mdo.setCatalog(cat);
    return factory.createMetaDataObject(mdo);
  }

  private static JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject> rootV221(
    String catalogName,
    String synonymRu) {
    String seed = "newCatalog|V2_21|" + catalogName;
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());

    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Catalog cat = factory.createCatalog();
    cat.setUuid(GoldenUuid.from(seed, "catalog.uuid"));
    cat.setInternalInfo(internalInfoV221(catalogName, seed + "|internalInfo"));
    cat.setProperties(fillPropertiesV221(factory, catalogName, synonymRu));
    cat.setChildObjects(factory.createCatalogChildObjects());

    mdo.setCatalog(cat);
    return factory.createMetaDataObject(mdo);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo internalInfoV220(
    String catalogName,
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo();
    for (int i = 0; i < CATALOG_GENERATED_TYPE_PREFIXES.length; i++) {
      addGenV220(
        ii,
        CATALOG_GENERATED_TYPE_PREFIXES[i] + "." + catalogName,
        CATALOG_GEN_CATEGORIES_V220[i],
        seed,
        i);
    }
    return ii;
  }

  private static void addGenV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo ii,
    String name,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories category,
    String seed,
    int index) {
    var gt = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.GeneratedType();
    gt.setName(name);
    gt.setCategory(category);
    gt.setTypeId(GoldenUuid.from(seed, "generatedType." + index + ".typeId"));
    gt.setValueId(GoldenUuid.from(seed, "generatedType." + index + ".valueId"));
    ii.getGeneratedType().add(gt);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo internalInfoV221(
    String catalogName,
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo();
    for (int i = 0; i < CATALOG_GENERATED_TYPE_PREFIXES.length; i++) {
      addGenV221(
        ii,
        CATALOG_GENERATED_TYPE_PREFIXES[i] + "." + catalogName,
        CATALOG_GEN_CATEGORIES_V221[i],
        seed,
        i);
    }
    return ii;
  }

  private static void addGenV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo ii,
    String name,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories category,
    String seed,
    int index) {
    var gt = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.GeneratedType();
    gt.setName(name);
    gt.setCategory(category);
    gt.setTypeId(GoldenUuid.from(seed, "generatedType." + index + ".typeId"));
    gt.setValueId(GoldenUuid.from(seed, "generatedType." + index + ".valueId"));
    ii.getGeneratedType().add(gt);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties fillPropertiesV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory factory,
    String catalogName,
    String synonymRu) {
    var xrOf = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties p = factory.createCatalogProperties();
    p.setName(catalogName);
    p.setSynonym(localStringV220(synonymRu));
    p.setComment("");
    p.setObjectBelonging(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectBelonging.NATIVE);
    p.setHierarchical(false);
    p.setHierarchyType(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.HierarchyType.HIERARCHY_FOLDERS_AND_ITEMS);
    p.setLimitLevelCount(false);
    p.setLevelCount(new BigDecimal("2"));
    p.setFoldersOnTop(true);
    p.setUseStandardCommands(true);
    p.setOwners(xrOf.createMDListType());
    p.setSubordinationUse(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SubordinationUse.TO_ITEMS);
    p.setCodeLength(new BigDecimal("9"));
    p.setDescriptionLength(new BigDecimal("25"));
    p.setCodeType(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogCodeType.STRING);
    p.setCodeAllowedLength(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.AllowedLength.VARIABLE);
    p.setCodeSeries(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogCodesSeries.WHOLE_CATALOG);
    p.setCheckUnique(true);
    p.setAutonumbering(true);
    p.setDefaultPresentation(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogMainPresentation.AS_DESCRIPTION);
    p.setStandardAttributes(standardAttributesV220());
    p.setCharacteristics(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.CharacteristicsDescriptions());
    p.setPredefined("");
    p.setPredefinedDataUpdate(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.PredefinedDataUpdate.AUTO);
    p.setEditType(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.EditType.IN_DIALOG);
    p.setQuickChoice(false);
    p.setChoiceMode(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ChoiceMode.BOTH_WAYS);
    p.setInputByString(inputByStringV220(xrOf, catalogName));
    p.setSearchStringModeOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.SearchStringModeOnInputByString
        .BEGIN);
    p.setFullTextSearchOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.FullTextSearchOnInputByString
        .DONT_USE);
    p.setChoiceDataGetModeOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.ChoiceDataGetModeOnInputByString
        .DIRECTLY);
    p.setDefaultObjectForm("");
    p.setDefaultFolderForm("");
    p.setDefaultListForm("");
    p.setDefaultChoiceForm("");
    p.setDefaultFolderChoiceForm("");
    p.setAuxiliaryObjectForm("");
    p.setAuxiliaryFolderForm("");
    p.setAuxiliaryListForm("");
    p.setAuxiliaryChoiceForm("");
    p.setAuxiliaryFolderChoiceForm("");
    p.setObjectModule("");
    p.setManagerModule("");
    p.setIncludeHelpInContents(false);
    p.setHelp("");
    p.setBasedOn(xrOf.createMDListType());
    p.setDataLockFields(xrOf.createFieldList());
    p.setDataLockControlMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode.MANAGED);
    p.setFullTextSearch(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.FullTextSearchUsing.USE);
    p.setObjectPresentation(localStringV220(synonymRu));
    p.setExtendedObjectPresentation(localStringV220(""));
    p.setListPresentation(localStringV220(""));
    p.setExtendedListPresentation(localStringV220(""));
    p.setExplanation(localStringV220(""));
    p.setCreateOnInput(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CreateOnInput.USE);
    p.setChoiceHistoryOnInput(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.ChoiceHistoryOnInput.AUTO);
    p.setDataHistory(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DataHistoryUse.DONT_USE);
    p.setUpdateDataHistoryImmediatelyAfterWrite(false);
    p.setExecuteAfterWriteDataHistoryVersionProcessing(false);
    p.setAdditionalIndexes("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.FieldList inputByStringV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ObjectFactory xrOf,
    String catalogName) {
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.FieldList fl = xrOf.createFieldList();
    fl.getField().add("Catalog." + catalogName + ".StandardAttribute.Description");
    fl.getField().add("Catalog." + catalogName + ".StandardAttribute.Code");
    return fl;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FillChecking fillCheckingV220(
    StdFill f) {
    return switch (f) {
      case DONT_CHECK -> io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FillChecking.DONT_CHECK;
      case SHOW_ERROR -> io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FillChecking.SHOW_ERROR;
    };
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeReductionMode typeReductionV220(
    StdTr t) {
    return switch (t) {
      case TRANSFORM_VALUES ->
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeReductionMode.TRANSFORM_VALUES;
      case DENY -> io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeReductionMode.DENY;
    };
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FillChecking fillCheckingV221(
    StdFill f) {
    return switch (f) {
      case DONT_CHECK -> io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FillChecking.DONT_CHECK;
      case SHOW_ERROR -> io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FillChecking.SHOW_ERROR;
    };
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeReductionMode typeReductionV221(
    StdTr t) {
    return switch (t) {
      case TRANSFORM_VALUES ->
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeReductionMode.TRANSFORM_VALUES;
      case DENY -> io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeReductionMode.DENY;
    };
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions
    standardAttributesV220() {
    var sad = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions();
    for (StdAttrRow row : STD_CATALOG_ATTRS) {
      sad.getStandardAttribute().add(
        stdAttrV220(row.name(), fillCheckingV220(row.fill()), row.fillFromFillingValue(), typeReductionV220(row.tr())));
    }
    return sad;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescription stdAttrV220(
    String name,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FillChecking fillChecking,
    boolean fillFromFillingValue,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeReductionMode typeReductionMode) {
    var sa = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescription();
    sa.setName(name);
    sa.setLinkByType(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.TypeLink());
    sa.setFillChecking(fillChecking);
    sa.setMultiLine(false);
    sa.setFillFromFillingValue(fillFromFillingValue);
    sa.setCreateOnInput(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CreateOnInput.AUTO);
    sa.setTypeReductionMode(typeReductionMode);
    sa.setDataHistory(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DataHistoryUse.USE);
    sa.setFullTextSearch(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.FullTextSearchUsing.USE);
    sa.setQuickChoice(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.UseQuickChoice.AUTO);
    sa.setChoiceHistoryOnInput(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.ChoiceHistoryOnInput.AUTO);
    sa.setMarkNegatives(false);
    sa.setPasswordMode(false);
    sa.setExtendedEdit(false);
    sa.setChoiceParameterLinks(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ChoiceParameterLinks());
    return sa;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType localStringV220(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties fillPropertiesV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory factory,
    String catalogName,
    String synonymRu) {
    var xrOf = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties p = factory.createCatalogProperties();
    p.setName(catalogName);
    p.setSynonym(localStringV221(synonymRu));
    p.setComment("");
    p.setObjectBelonging(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectBelonging.NATIVE);
    p.setHierarchical(false);
    p.setHierarchyType(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.HierarchyType.HIERARCHY_FOLDERS_AND_ITEMS);
    p.setLimitLevelCount(false);
    p.setLevelCount(new BigDecimal("2"));
    p.setFoldersOnTop(true);
    p.setUseStandardCommands(true);
    p.setOwners(xrOf.createMDListType());
    p.setSubordinationUse(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SubordinationUse.TO_ITEMS);
    p.setCodeLength(new BigDecimal("9"));
    p.setDescriptionLength(new BigDecimal("25"));
    p.setCodeType(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogCodeType.STRING);
    p.setCodeAllowedLength(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.AllowedLength.VARIABLE);
    p.setCodeSeries(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogCodesSeries.WHOLE_CATALOG);
    p.setCheckUnique(true);
    p.setAutonumbering(true);
    p.setDefaultPresentation(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogMainPresentation.AS_DESCRIPTION);
    p.setStandardAttributes(standardAttributesV221());
    p.setCharacteristics(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.CharacteristicsDescriptions());
    p.setPredefined("");
    p.setPredefinedDataUpdate(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.PredefinedDataUpdate.AUTO);
    p.setEditType(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.EditType.IN_DIALOG);
    p.setQuickChoice(false);
    p.setChoiceMode(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ChoiceMode.BOTH_WAYS);
    p.setInputByString(inputByStringV221(xrOf, catalogName));
    p.setSearchStringModeOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.SearchStringModeOnInputByString
        .BEGIN);
    p.setFullTextSearchOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.FullTextSearchOnInputByString
        .DONT_USE);
    p.setChoiceDataGetModeOnInputByString(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.ChoiceDataGetModeOnInputByString
        .DIRECTLY);
    p.setDefaultObjectForm("");
    p.setDefaultFolderForm("");
    p.setDefaultListForm("");
    p.setDefaultChoiceForm("");
    p.setDefaultFolderChoiceForm("");
    p.setAuxiliaryObjectForm("");
    p.setAuxiliaryFolderForm("");
    p.setAuxiliaryListForm("");
    p.setAuxiliaryChoiceForm("");
    p.setAuxiliaryFolderChoiceForm("");
    p.setObjectModule("");
    p.setManagerModule("");
    p.setIncludeHelpInContents(false);
    p.setHelp("");
    p.setBasedOn(xrOf.createMDListType());
    p.setDataLockFields(xrOf.createFieldList());
    p.setDataLockControlMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode.MANAGED);
    p.setFullTextSearch(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.FullTextSearchUsing.USE);
    p.setObjectPresentation(localStringV221(synonymRu));
    p.setExtendedObjectPresentation(localStringV221(""));
    p.setListPresentation(localStringV221(""));
    p.setExtendedListPresentation(localStringV221(""));
    p.setExplanation(localStringV221(""));
    p.setCreateOnInput(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CreateOnInput.USE);
    p.setChoiceHistoryOnInput(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.ChoiceHistoryOnInput.AUTO);
    p.setDataHistory(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DataHistoryUse.DONT_USE);
    p.setUpdateDataHistoryImmediatelyAfterWrite(false);
    p.setExecuteAfterWriteDataHistoryVersionProcessing(false);
    p.setAdditionalIndexes("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.FieldList inputByStringV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ObjectFactory xrOf,
    String catalogName) {
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.FieldList fl = xrOf.createFieldList();
    fl.getField().add("Catalog." + catalogName + ".StandardAttribute.Description");
    fl.getField().add("Catalog." + catalogName + ".StandardAttribute.Code");
    return fl;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions
    standardAttributesV221() {
    var sad = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions();
    for (StdAttrRow row : STD_CATALOG_ATTRS) {
      sad.getStandardAttribute().add(
        stdAttrV221(row.name(), fillCheckingV221(row.fill()), row.fillFromFillingValue(), typeReductionV221(row.tr())));
    }
    return sad;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescription stdAttrV221(
    String name,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FillChecking fillChecking,
    boolean fillFromFillingValue,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeReductionMode typeReductionMode) {
    var sa = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescription();
    sa.setName(name);
    sa.setLinkByType(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.TypeLink());
    sa.setFillChecking(fillChecking);
    sa.setMultiLine(false);
    sa.setFillFromFillingValue(fillFromFillingValue);
    sa.setCreateOnInput(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CreateOnInput.AUTO);
    sa.setTypeReductionMode(typeReductionMode);
    sa.setDataHistory(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DataHistoryUse.USE);
    sa.setFullTextSearch(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.FullTextSearchUsing.USE);
    sa.setQuickChoice(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.UseQuickChoice.AUTO);
    sa.setChoiceHistoryOnInput(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.ChoiceHistoryOnInput.AUTO);
    sa.setMarkNegatives(false);
    sa.setPasswordMode(false);
    sa.setExtendedEdit(false);
    sa.setChoiceParameterLinks(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ChoiceParameterLinks());
    return sa;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType localStringV221(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }
}
