/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class NewExternalArtifactXml {
  private static final String EXTERNAL_REPORT_CLASS_ID = "e41aff26-25cf-4bb6-b6c1-3f478a75f374";
  private static final String EXTERNAL_DATA_PROCESSOR_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";
  private static final String EXTERNAL_DATA_PROCESSOR_OBJECT_PREFIX = "ExternalDataProcessorObject.";
  private static final Pattern SYNONYM_WITH_ITEMS = Pattern.compile(
    "(?s)<Synonym>\\s*<v8:item>.*?</v8:item>\\s*</Synonym>");
  private static final Pattern INTERNAL_INFO_ORDER_EXTERNAL = Pattern.compile(
    "(?s)<InternalInfo>\\s*(<xr:GeneratedType\\b.*?</xr:GeneratedType>)\\s*(<xr:ContainedObject>.*?</xr:ContainedObject>)\\s*</InternalInfo>");

  private NewExternalArtifactXml() {
  }

  public static Path create(
    Path artifactsRoot,
    String artifactName,
    ExternalArtifactKind kind,
    SchemaVersion version) throws IOException, JAXBException {
    if (artifactsRoot == null) {
      throw new IllegalArgumentException("artifactsRoot required");
    }
    if (artifactName == null || artifactName.trim().isEmpty()) {
      throw new IllegalArgumentException("artifactName required");
    }
    String name = artifactName.trim();
    CatalogNameConstraints.check(name);
    Path dir = artifactsRoot.resolve(name);
    Path xmlPath = dir.resolve(name + ".xml");
    if (Files.exists(xmlPath)) {
      throw new IllegalArgumentException("file already exists: " + xmlPath);
    }
    Files.createDirectories(dir);
    if (version == SchemaVersion.V2_20) {
      writeV20(xmlPath, name, kind);
    } else {
      writeV21(xmlPath, name, kind);
    }
    return xmlPath;
  }

  private static void writeV20(Path xmlPath, String name, ExternalArtifactKind kind)
    throws JAXBException, IOException {
    String seed = "newExternalArtifact|V2_20|" + kind + "|" + name;
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory();
    var root = factory.createMetaDataObject();
    root.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    if (kind == ExternalArtifactKind.REPORT) {
      var obj = factory.createExternalReport();
      obj.setUuid(GoldenUuid.from(seed, "report.uuid"));
      obj.setInternalInfo(internalInfoV20("ExternalReportObject." + name, seed + "|report.internalInfo"));
      obj.setProperties(externalReportPropertiesV20(factory, name));
      obj.setChildObjects(factory.createExternalReportChildObjects());
      root.setExternalReport(obj);
    } else {
      var obj = factory.createExternalDataProcessor();
      obj.setUuid(GoldenUuid.from(seed, "processor.uuid"));
      obj.setInternalInfo(
        internalInfoV20(EXTERNAL_DATA_PROCESSOR_OBJECT_PREFIX + name, seed + "|processor.internalInfo"));
      obj.setProperties(externalDataProcessorPropertiesV20(factory, name));
      obj.setChildObjects(factory.createExternalDataProcessorChildObjects());
      root.setExternalDataProcessor(obj);
    }
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject> je =
      factory.createMetaDataObject(root);
    writeNormalized(xmlPath, je, SchemaVersion.V2_20);
  }

  private static void writeV21(Path xmlPath, String name, ExternalArtifactKind kind)
    throws JAXBException, IOException {
    String seed = "newExternalArtifact|V2_21|" + kind + "|" + name;
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory();
    var root = factory.createMetaDataObject();
    root.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    if (kind == ExternalArtifactKind.REPORT) {
      var obj = factory.createExternalReport();
      obj.setUuid(GoldenUuid.from(seed, "report.uuid"));
      obj.setInternalInfo(internalInfoV21("ExternalReportObject." + name, seed + "|report.internalInfo"));
      obj.setProperties(externalReportPropertiesV21(factory, name));
      obj.setChildObjects(factory.createExternalReportChildObjects());
      root.setExternalReport(obj);
    } else {
      var obj = factory.createExternalDataProcessor();
      obj.setUuid(GoldenUuid.from(seed, "processor.uuid"));
      obj.setInternalInfo(
        internalInfoV21(EXTERNAL_DATA_PROCESSOR_OBJECT_PREFIX + name, seed + "|processor.internalInfo"));
      obj.setProperties(externalDataProcessorPropertiesV21(factory, name));
      obj.setChildObjects(factory.createExternalDataProcessorChildObjects());
      root.setExternalDataProcessor(obj);
    }
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject> je =
      factory.createMetaDataObject(root);
    writeNormalized(xmlPath, je, SchemaVersion.V2_21);
  }

  private static void writeNormalized(Path xmlPath, Object jaxbRoot, SchemaVersion version) throws JAXBException, IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(version, jaxbRoot, buf, WriteOptions.defaults());
    String normalized = GoldenXmlPostProcessor.normalizeMetaDataObjectXml(
      buf.toString(StandardCharsets.UTF_8), version);
    String withSynonym = SYNONYM_WITH_ITEMS.matcher(normalized).replaceAll("<Synonym/>");
    String withInternalInfoOrder = reorderExternalInternalInfo(withSynonym);
    try (ByteArrayInputStream in = new ByteArrayInputStream(withInternalInfoOrder.getBytes(StandardCharsets.UTF_8))) {
      DesignerXml.unmarshal(version, in);
    }
    Files.writeString(xmlPath, withInternalInfoOrder, StandardCharsets.UTF_8);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo internalInfoV20(
    String generatedTypeName,
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo();
    var gt = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.GeneratedType();
    gt.setName(generatedTypeName);
    gt.setCategory(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.TypeCategories.OBJECT);
    gt.setTypeId(GoldenUuid.from(seed, "generatedType.typeId"));
    gt.setValueId(GoldenUuid.from(seed, "generatedType.valueId"));
    var contained = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ContainedObject();
    contained.setClassId(generatedTypeName.startsWith(EXTERNAL_DATA_PROCESSOR_OBJECT_PREFIX)
      ? EXTERNAL_DATA_PROCESSOR_CLASS_ID
      : EXTERNAL_REPORT_CLASS_ID);
    contained.setObjectId(GoldenUuid.from(seed, "contained.objectId"));
    ii.getContainedObject().add(contained);
    ii.getGeneratedType().add(gt);
    return ii;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo internalInfoV21(
    String generatedTypeName,
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo();
    var gt = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.GeneratedType();
    gt.setName(generatedTypeName);
    gt.setCategory(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.TypeCategories.OBJECT);
    gt.setTypeId(GoldenUuid.from(seed, "generatedType.typeId"));
    gt.setValueId(GoldenUuid.from(seed, "generatedType.valueId"));
    var contained = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ContainedObject();
    contained.setClassId(generatedTypeName.startsWith(EXTERNAL_DATA_PROCESSOR_OBJECT_PREFIX)
      ? EXTERNAL_DATA_PROCESSOR_CLASS_ID
      : EXTERNAL_REPORT_CLASS_ID);
    contained.setObjectId(GoldenUuid.from(seed, "contained.objectId"));
    ii.getContainedObject().add(contained);
    ii.getGeneratedType().add(gt);
    return ii;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExternalReportProperties externalReportPropertiesV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory factory,
    String name) {
    var p = factory.createExternalReportProperties();
    p.setName(name);
    p.setSynonym(localStringV20(""));
    p.setComment("");
    p.setDefaultForm("");
    p.setAuxiliaryForm("");
    p.setMainDataCompositionSchema("");
    p.setDefaultSettingsForm("");
    p.setAuxiliarySettingsForm("");
    p.setDefaultVariantForm("");
    p.setVariantsStorage("");
    p.setSettingsStorage("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExternalDataProcessorProperties externalDataProcessorPropertiesV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory factory,
    String name) {
    var p = factory.createExternalDataProcessorProperties();
    p.setName(name);
    p.setSynonym(localStringV20(""));
    p.setComment("");
    p.setDefaultForm("");
    p.setAuxiliaryForm("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExternalReportProperties externalReportPropertiesV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory factory,
    String name) {
    var p = factory.createExternalReportProperties();
    p.setName(name);
    p.setSynonym(localStringV21(""));
    p.setComment("");
    p.setDefaultForm("");
    p.setAuxiliaryForm("");
    p.setMainDataCompositionSchema("");
    p.setDefaultSettingsForm("");
    p.setAuxiliarySettingsForm("");
    p.setDefaultVariantForm("");
    p.setVariantsStorage("");
    p.setSettingsStorage("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExternalDataProcessorProperties externalDataProcessorPropertiesV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory factory,
    String name) {
    var p = factory.createExternalDataProcessorProperties();
    p.setName(name);
    p.setSynonym(localStringV21(""));
    p.setComment("");
    p.setDefaultForm("");
    p.setAuxiliaryForm("");
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType localStringV20(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }

  private static String reorderExternalInternalInfo(String xml) {
    String reorderedInternalInfo = """
      <InternalInfo>
      \t\t\t$2
      \t\t\t$1
      \t\t</InternalInfo>""";
    return INTERNAL_INFO_ORDER_EXTERNAL.matcher(xml).replaceFirst(reorderedInternalInfo);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType localStringV21(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }
}
