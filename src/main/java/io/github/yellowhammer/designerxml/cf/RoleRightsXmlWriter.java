/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class RoleRightsXmlWriter {
  private static final List<String> CONFIG_RIGHTS = List.of(
    "ThinClient",
    "WebClient",
    "MobileClient",
    "MainWindowModeNormal",
    "MainWindowModeWorkplace",
    "MainWindowModeEmbeddedWorkplace",
    "MainWindowModeFullscreenWorkplace",
    "MainWindowModeKiosk",
    "AnalyticsSystemClient",
    "SaveUserData",
    "Output"
  );

  private RoleRightsXmlWriter() {
  }

  static String generate(SchemaVersion version, String configurationName) {
    String v = version == SchemaVersion.V2_20 ? "2.20" : "2.21";
    String cfg = configurationName == null || configurationName.isBlank()
      ? CfLayout.DEFAULT_CONFIGURATION_NAME
      : configurationName;
    StringBuilder sb = new StringBuilder(1024);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<Rights xmlns=\"http://v8.1c.ru/8.2/roles\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
      .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"Rights\" version=\"")
      .append(v)
      .append("\">\n");
    sb.append("\t<setForNewObjects>false</setForNewObjects>\n");
    sb.append("\t<setForAttributesByDefault>true</setForAttributesByDefault>\n");
    sb.append("\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n");
    sb.append("\t<object>\n");
    sb.append("\t\t<name>Configuration.").append(cfg).append("</name>\n");
    for (String right : CONFIG_RIGHTS) {
      sb.append("\t\t<right>\n");
      sb.append("\t\t\t<name>").append(right).append("</name>\n");
      sb.append("\t\t\t<value>true</value>\n");
      sb.append("\t\t</right>\n");
    }
    sb.append("\t</object>\n");
    sb.append("</Rights>\n");
    return new String(sb.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }
}
