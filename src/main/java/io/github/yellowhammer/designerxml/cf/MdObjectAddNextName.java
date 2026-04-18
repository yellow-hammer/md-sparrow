/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MdObjectAddNextName {

  private static final int MAX_SUFFIX = 999_999;

  private MdObjectAddNextName() {
  }

  public static String nextFreeName(
    Path configurationXml, SchemaVersion version, MdObjectAddType type, Path cfRoot) throws IOException, JAXBException {
    Set<String> taken = mergeTakenNames(configurationXml, version, type, cfRoot);
    String prefix = type.namePrefix();
    for (int n = 1; n <= MAX_SUFFIX; n++) {
      String candidate = prefix + n;
      if (!taken.contains(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no free name for " + type);
  }

  static Set<String> mergeTakenNames(
    Path configurationXml, SchemaVersion version, MdObjectAddType type, Path cfRoot)
    throws IOException, JAXBException {
    List<String> fromCfg = ConfigurationChildObjectLister.listNames(configurationXml, version, type.configurationXmlTag());
    Set<String> taken = new HashSet<>(fromCfg);
    Path subdir = cfRoot.resolve(type.cfSubdir());
    if (Files.isDirectory(subdir)) {
      try (Stream<Path> stream = Files.list(subdir)) {
        stream
          .filter(Files::isRegularFile)
          .map(p -> p.getFileName().toString())
          .filter(fn -> fn.endsWith(".xml"))
          .map(MdObjectAddNextName::stripXmlExtension)
          .forEach(taken::add);
      }
    }
    return taken;
  }

  private static String stripXmlExtension(String fileName) {
    if (fileName.endsWith(".xml")) {
      return fileName.substring(0, fileName.length() - ".xml".length());
    }
    return fileName;
  }
}
