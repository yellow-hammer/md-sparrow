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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

public final class ExternalArtifactPropertiesEdit {

  private ExternalArtifactPropertiesEdit() {
  }

  public static ExternalArtifactPropertiesDto read(Path objectXml, SchemaVersion version)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    return switch (version) {
      case V2_20 -> readV20(objectXml);
      case V2_21 -> readV21(objectXml);
    };
  }

  public static void write(Path objectXml, SchemaVersion version, ExternalArtifactPropertiesDto dto)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    switch (version) {
      case V2_20 -> writeV20(objectXml, dto);
      case V2_21 -> writeV21(objectXml, dto);
    }
  }

  private static ExternalArtifactPropertiesDto readV20(Path objectXml) throws IOException, JAXBException {
    Object root = DesignerXml.read(objectXml, SchemaVersion.V2_20);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    ExternalArtifactPropertiesDto out = new ExternalArtifactPropertiesDto();
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      out.kind = "REPORT";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      out.kind = "DATA_PROCESSOR";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
  }

  private static ExternalArtifactPropertiesDto readV21(Path objectXml) throws IOException, JAXBException {
    Object root = DesignerXml.read(objectXml, SchemaVersion.V2_21);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    ExternalArtifactPropertiesDto out = new ExternalArtifactPropertiesDto();
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      out.kind = "REPORT";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      out.kind = "DATA_PROCESSOR";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
  }

  private static void writeV20(Path objectXml, ExternalArtifactPropertiesDto dto) throws IOException, JAXBException {
    ExternalArtifactPropertiesDto baseline = readV20(objectXml);
    Object root = DesignerXml.read(objectXml, SchemaVersion.V2_20);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    String containerLocal;
    String originalXml = Files.readString(objectXml, StandardCharsets.UTF_8);
    ExternalArtifactPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      p.setName(nvl(incoming.name));
      LocalStringSync.setOrPutRuV20(p.getSynonym(), nvl(incoming.synonymRu));
      p.setComment(nvl(incoming.comment));
      containerLocal = "ExternalReport";
    } else if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      p.setName(nvl(incoming.name));
      LocalStringSync.setOrPutRuV20(p.getSynonym(), nvl(incoming.synonymRu));
      p.setComment(nvl(incoming.comment));
      containerLocal = "ExternalDataProcessor";
    } else {
      throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
    }
    byte[] patched = tryGranularPatch(
      originalXml,
      root,
      SchemaVersion.V2_20,
      containerLocal,
      baseline,
      incoming
    ).orElseThrow(() -> new IllegalStateException(
      "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
    ));
    Files.write(objectXml, patched);
  }

  private static void writeV21(Path objectXml, ExternalArtifactPropertiesDto dto) throws IOException, JAXBException {
    ExternalArtifactPropertiesDto baseline = readV21(objectXml);
    Object root = DesignerXml.read(objectXml, SchemaVersion.V2_21);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    String containerLocal;
    String originalXml = Files.readString(objectXml, StandardCharsets.UTF_8);
    ExternalArtifactPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      p.setName(nvl(incoming.name));
      LocalStringSync.setOrPutRuV21(p.getSynonym(), nvl(incoming.synonymRu));
      p.setComment(nvl(incoming.comment));
      containerLocal = "ExternalReport";
    } else if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      p.setName(nvl(incoming.name));
      LocalStringSync.setOrPutRuV21(p.getSynonym(), nvl(incoming.synonymRu));
      p.setComment(nvl(incoming.comment));
      containerLocal = "ExternalDataProcessor";
    } else {
      throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
    }
    byte[] patched = tryGranularPatch(
      originalXml,
      root,
      SchemaVersion.V2_21,
      containerLocal,
      baseline,
      incoming
    ).orElseThrow(() -> new IllegalStateException(
      "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
    ));
    Files.write(objectXml, patched);
  }

  private static Optional<byte[]> tryGranularPatch(
    String originalXml,
    Object rootAfterApply,
    SchemaVersion version,
    String containerLocal,
    ExternalArtifactPropertiesDto baseline,
    ExternalArtifactPropertiesDto incoming) throws JAXBException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(version, rootAfterApply, buf, WriteOptions.defaults());
    String updatedXml = buf.toString(StandardCharsets.UTF_8);
    List<String> changedTags = changedTags(baseline, incoming);
    if (changedTags.isEmpty()) {
      return Optional.of(originalXml.getBytes(StandardCharsets.UTF_8));
    }
    List<Replacement> replacements = new ArrayList<>();
    try {
      for (String tag : changedTags) {
        MdObjectXmlRegions.Region updatedReg =
          MdObjectXmlRegions.findDirectChildOfPropertiesRegion(updatedXml, containerLocal, tag);
        if (!updatedReg.isValid()) {
          return Optional.empty();
        }
        String replacement = updatedXml.substring(updatedReg.start(), updatedReg.end());
        MdObjectXmlRegions.Region currentReg =
          MdObjectXmlRegions.findDirectChildOfPropertiesRegion(originalXml, containerLocal, tag);
        if (currentReg.isValid()) {
          replacements.add(new Replacement(currentReg.start(), currentReg.end(), replacement));
          continue;
        }
        MdObjectXmlRegions.Region propertiesRegion = MdObjectXmlRegions.findPropertiesRegion(originalXml, containerLocal);
        if (!propertiesRegion.isValid()) {
          return Optional.empty();
        }
        int insertPos = propertiesCloseTagStart(originalXml, propertiesRegion);
        if (insertPos < 0) {
          return Optional.empty();
        }
        replacements.add(new Replacement(insertPos, insertPos, insertionBeforePropertiesClose(originalXml, insertPos, replacement)));
      }
    } catch (XMLStreamException e) {
      return Optional.empty();
    }
    replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
    StringBuilder sb = new StringBuilder(originalXml);
    for (Replacement rep : replacements) {
      sb.replace(rep.start, rep.end, rep.text);
    }
    byte[] out = sb.toString().getBytes(StandardCharsets.UTF_8);
    try {
      ExternalArtifactPropertiesDto verified = readFromBytes(out, version);
      return equalsDto(verified, incoming) ? Optional.of(out) : Optional.empty();
    } catch (JAXBException e) {
      return Optional.empty();
    }
  }

  private static ExternalArtifactPropertiesDto readFromBytes(byte[] xmlBytes, SchemaVersion version) throws JAXBException {
    Object root = DesignerXml.unmarshal(version, new ByteArrayInputStream(xmlBytes));
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    return switch (version) {
      case V2_20 -> readFromRootV20(je);
      case V2_21 -> readFromRootV21(je);
    };
  }

  private static ExternalArtifactPropertiesDto readFromRootV20(JAXBElement<?> je) {
    if (!(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    ExternalArtifactPropertiesDto out = new ExternalArtifactPropertiesDto();
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      out.kind = "REPORT";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      out.kind = "DATA_PROCESSOR";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
  }

  private static ExternalArtifactPropertiesDto readFromRootV21(JAXBElement<?> je) {
    if (!(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    ExternalArtifactPropertiesDto out = new ExternalArtifactPropertiesDto();
    if (mdo.getExternalReport() != null && mdo.getExternalReport().getProperties() != null) {
      var p = mdo.getExternalReport().getProperties();
      out.kind = "REPORT";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    if (mdo.getExternalDataProcessor() != null && mdo.getExternalDataProcessor().getProperties() != null) {
      var p = mdo.getExternalDataProcessor().getProperties();
      out.kind = "DATA_PROCESSOR";
      out.name = nvl(p.getName());
      out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
      out.comment = nvl(p.getComment());
      return out;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
  }

  private static ExternalArtifactPropertiesDto normalizeIncoming(
    ExternalArtifactPropertiesDto incoming,
    ExternalArtifactPropertiesDto baseline) {
    ExternalArtifactPropertiesDto out = incoming == null ? new ExternalArtifactPropertiesDto() : incoming;
    ExternalArtifactPropertiesDto base = baseline == null ? new ExternalArtifactPropertiesDto() : baseline;
    out.kind = nvl(out.kind).isEmpty() ? nvl(base.kind) : out.kind;
    out.name = nvl(out.name);
    out.synonymRu = nvl(out.synonymRu);
    out.comment = nvl(out.comment);
    return out;
  }

  private static boolean equalsDto(ExternalArtifactPropertiesDto left, ExternalArtifactPropertiesDto right) {
    if (left == right) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return nvl(left.kind).equals(nvl(right.kind))
      && nvl(left.name).equals(nvl(right.name))
      && nvl(left.synonymRu).equals(nvl(right.synonymRu))
      && nvl(left.comment).equals(nvl(right.comment));
  }

  private static List<String> changedTags(ExternalArtifactPropertiesDto baseline, ExternalArtifactPropertiesDto incoming) {
    List<String> tags = new ArrayList<>();
    if (!nvl(baseline.name).equals(nvl(incoming.name))) {
      tags.add("Name");
    }
    if (!nvl(baseline.synonymRu).equals(nvl(incoming.synonymRu))) {
      tags.add("Synonym");
    }
    if (!nvl(baseline.comment).equals(nvl(incoming.comment))) {
      tags.add("Comment");
    }
    return tags;
  }

  private record Replacement(int start, int end, String text) {
  }

  private static int propertiesCloseTagStart(String xmlUtf8, MdObjectXmlRegions.Region propertiesRegion) {
    return xmlUtf8.lastIndexOf("</", propertiesRegion.end() - 1);
  }

  private static String insertionBeforePropertiesClose(String xmlUtf8, int insertPos, String replacementElementXml) {
    String parentIndent = currentLineIndent(xmlUtf8, insertPos);
    String childIndent = parentIndent + "\t";
    String compact = replacementElementXml.trim().replace(">\r\n<", "><").replace(">\n<", "><");
    String expanded = compact.replace("><", ">\n<");
    String[] lines = expanded.split("\n");
    StringBuilder out = new StringBuilder(expanded.length() + childIndent.length() * lines.length + 2);
    out.append('\n');
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(childIndent).append(lines[i].trim());
    }
    return out.toString();
  }

  private static String currentLineIndent(String xmlUtf8, int startOffset) {
    int from = startOffset - 1;
    while (from >= 0 && xmlUtf8.charAt(from) != '\n' && xmlUtf8.charAt(from) != '\r') {
      from--;
    }
    from++;
    int i = from;
    while (i < xmlUtf8.length()) {
      char c = xmlUtf8.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      i++;
    }
    return xmlUtf8.substring(from, i);
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
