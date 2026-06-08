/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistinctUuidRewriteTest {

  @Test
  void preservesClassIdButRemapsOtherUuids() {
    String xml = """
      <xr:ContainedObject>
      \t<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>
      \t<xr:ObjectId>11111111-1111-1111-1111-111111111111</xr:ObjectId>
      </xr:ContainedObject>""";

    String out = DistinctUuidRewrite.remapDeterministic(xml, "seed");

    // ClassId — фиксированный идентификатор класса платформы, не подменяется
    assertThat(out).contains("<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>");
    // объектно-зависимый ObjectId — подменяется
    assertThat(out).doesNotContain("11111111-1111-1111-1111-111111111111");
  }

  @Test
  void isDeterministicForSameSeed() {
    String xml = "<uuid>11111111-1111-1111-1111-111111111111</uuid>";
    assertThat(DistinctUuidRewrite.remapDeterministic(xml, "s"))
      .isEqualTo(DistinctUuidRewrite.remapDeterministic(xml, "s"));
  }
}
