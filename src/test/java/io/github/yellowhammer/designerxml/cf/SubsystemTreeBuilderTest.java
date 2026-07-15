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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дерево подсистем: вложенность и состав читаются из XML, а не угадываются по каталогам.
 */
class SubsystemTreeBuilderTest {

  @Test
  void build_readsNestedSubsystemsAndContent() throws Exception {
    List<SubsystemTreeBuilder.SubsystemNodeDto> roots =
      SubsystemTreeBuilder.build(configurationXml(), SchemaVersion.V2_20);

    assertThat(roots).as("подсистемы верхнего уровня").isNotEmpty();
    assertThat(roots).allSatisfy(node -> {
      assertThat(node.name()).isNotBlank();
      assertThat(node.xmlPath()).endsWith(node.name() + ".xml");
    });

    SubsystemTreeBuilder.SubsystemNodeDto withChildren = roots.stream()
      .filter(node -> !node.children().isEmpty())
      .findFirst()
      .orElseThrow(() -> new AssertionError("ожидалась подсистема с вложенными"));
    assertThat(withChildren.children()).allSatisfy(child ->
      assertThat(child.xmlPath()).contains(withChildren.name()));

    assertThat(roots.stream().anyMatch(node -> !node.contentRefs().isEmpty()))
      .as("состав подсистем должен читаться")
      .isTrue();
  }

  @Test
  void build_returnsEachSubsystemOnce() throws Exception {
    List<SubsystemTreeBuilder.SubsystemNodeDto> roots =
      SubsystemTreeBuilder.build(configurationXml(), SchemaVersion.V2_20);

    long total = count(roots);
    long unique = roots.stream().mapToLong(SubsystemTreeBuilderTest::countUniquePaths).sum();
    assertThat(total).isEqualTo(unique);
  }

  private static long count(List<SubsystemTreeBuilder.SubsystemNodeDto> nodes) {
    return nodes.stream().mapToLong(node -> 1 + count(node.children())).sum();
  }

  private static long countUniquePaths(SubsystemTreeBuilder.SubsystemNodeDto node) {
    return 1 + node.children().stream().mapToLong(SubsystemTreeBuilderTest::countUniquePaths).sum();
  }

  private static Path configurationXml() {
    return Path.of(System.getProperty("fixtures.ssl31.root")).resolve("src").resolve("cf").resolve("Configuration.xml");
  }
}
