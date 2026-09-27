/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Какие эталоны платформы есть у каждого формата.
 *
 * <p>Сверки по эталонам пропускают формат, у которого набора нет, поэтому пропавший набор иначе
 * остался бы незамеченным. Каждый набор выгрузила платформа своего формата (2.16, 2.17, 2.20 и 2.21 -
 * локально, остальные - workflow golden-snapshots); внешние объекты с формами собраны 8.3.27 и
 * разобраны своей платформой, а {@code ibcmd} собирает и разбирает их с 8.3.23, то есть с формата 2.16.
 */
class GoldenSnapshotsCoverageTest {

  private static final SchemaVersion EXTERNAL_SINCE = SchemaVersion.V2_16;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void уКаждогоФорматаЕстьВсеНаборыПлатформы(SchemaVersion version) {
    for (String set : List.of(
      GoldenSnapshots.CF, GoldenSnapshots.EMPTY_INFOBASE, GoldenSnapshots.NODES, GoldenSnapshots.CFE)) {
      assertThat(GoldenSnapshots.files(version, set)).as("%s в формате %s", set, version).isNotEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void внешниеОбъектыЕстьСФормата2_16(SchemaVersion version) {
    boolean expected = version.compareTo(EXTERNAL_SINCE) >= 0;
    for (String set : List.of(GoldenSnapshots.EXTERNAL, GoldenSnapshots.EXTERNAL_FULL)) {
      assertThat(GoldenSnapshots.files(version, set).isEmpty())
        .as("%s в формате %s", set, version)
        .isEqualTo(!expected);
    }
  }
}
