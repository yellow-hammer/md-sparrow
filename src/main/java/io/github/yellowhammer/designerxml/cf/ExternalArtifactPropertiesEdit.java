/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;
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
    return readFromRoot(DesignerXml.read(objectXml, version));
  }

  public static void write(Path objectXml, SchemaVersion version, ExternalArtifactPropertiesDto dto)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    ExternalArtifactPropertiesDto baseline = read(objectXml, version);
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    String originalXml = Files.readString(objectXml, StandardCharsets.UTF_8);
    ExternalArtifactPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    Object props = artifactProperties(je.getValue());
    if (props == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
    }
    JaxbReflect.set(props, "setName", nvl(incoming.name));
    LocalStringSync.setOrPutRu(JaxbReflect.get(props, "getSynonym"), nvl(incoming.synonymRu));
    JaxbReflect.set(props, "setComment", nvl(incoming.comment));
    Object report = JaxbReflect.get(je.getValue(), "getExternalReport");
    String containerLocal = report != null && JaxbReflect.get(report, "getProperties") != null
      ? "ExternalReport"
      : "ExternalDataProcessor";
    byte[] patched = tryGranularPatch(originalXml, root, version, containerLocal, baseline, incoming)
      .orElseThrow(() -> new IllegalStateException(
        "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
      ));
    Files.write(objectXml, patched);
  }

  /** {@code ExternalReport.Properties} или {@code ExternalDataProcessor.Properties}, иначе {@code null}. */
  private static Object artifactProperties(Object metaDataObject) {
    Object report = JaxbReflect.get(metaDataObject, "getExternalReport");
    Object reportProps = report == null ? null : JaxbReflect.get(report, "getProperties");
    if (reportProps != null) {
      return reportProps;
    }
    Object processor = JaxbReflect.get(metaDataObject, "getExternalDataProcessor");
    return processor == null ? null : JaxbReflect.get(processor, "getProperties");
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
    return readFromRoot(DesignerXml.unmarshal(version, new ByteArrayInputStream(xmlBytes)));
  }

  private static ExternalArtifactPropertiesDto readFromRoot(Object root) {
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    Object mdo = je.getValue();
    ExternalArtifactPropertiesDto out = new ExternalArtifactPropertiesDto();
    Object report = JaxbReflect.get(mdo, "getExternalReport");
    Object reportProps = report == null ? null : JaxbReflect.get(report, "getProperties");
    if (reportProps != null) {
      out.kind = "REPORT";
      fill(out, reportProps);
      return out;
    }
    Object processor = JaxbReflect.get(mdo, "getExternalDataProcessor");
    Object processorProps = processor == null ? null : JaxbReflect.get(processor, "getProperties");
    if (processorProps != null) {
      out.kind = "DATA_PROCESSOR";
      fill(out, processorProps);
      return out;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for external-artifact-properties");
  }

  private static void fill(ExternalArtifactPropertiesDto out, Object props) {
    out.name = nvl(JaxbReflect.getString(props, "getName"));
    out.synonymRu = LocalStringSync.firstRu(JaxbReflect.get(props, "getSynonym"));
    out.comment = nvl(JaxbReflect.getString(props, "getComment"));
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
