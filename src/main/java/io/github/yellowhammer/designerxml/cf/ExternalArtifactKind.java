/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

public enum ExternalArtifactKind {
  REPORT,
  DATA_PROCESSOR;

  public static ExternalArtifactKind fromCli(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("kind required: REPORT | DATA_PROCESSOR");
    }
    try {
      return ExternalArtifactKind.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unknown kind: " + raw + " (expected REPORT | DATA_PROCESSOR)");
    }
  }
}
