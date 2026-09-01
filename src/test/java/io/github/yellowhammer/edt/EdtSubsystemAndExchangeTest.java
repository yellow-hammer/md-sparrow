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
package io.github.yellowhammer.edt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
import io.github.yellowhammer.designerxml.cf.SubsystemCommandInterfaceFile;

/**
 * Командный интерфейс подсистемы и состав плана обмена в формате EDT.
 *
 * Сверяется с той же библиотекой в выгрузке конфигуратора: разметка у форматов
 * разная, а содержимое обязано совпасть.
 */
class EdtSubsystemAndExchangeTest {

  private static Path edtSource;
  private static Path designerCf;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() {
    edtSource = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    designerCf = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf");
    assertThat(edtSource).exists();
    assertThat(designerCf).exists();
  }

  @Test
  void командныйИнтерфейсСовпадаетСВыгрузкойКонфигуратора() throws Exception {
    SubsystemCommandInterfaceFile.Dto edt = EdtSubsystemCommandInterface.read(
        edtSource.resolve("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo"));
    SubsystemCommandInterfaceFile.Dto designer = SubsystemCommandInterfaceFile.read(
        designerCf.resolve("Subsystems/_ДемоАнкетирование.xml"));

    assertThat(commands(edt.visibility)).isEqualTo(commands(designer.visibility));
    assertThat(commands(edt.placement)).isEqualTo(commands(designer.placement));
    assertThat(commands(edt.order)).isEqualTo(commands(designer.order));
    assertThat(edt.subsystemsOrder).isEqualTo(designer.subsystemsOrder);
  }

  private static List<String> commands(List<SubsystemCommandInterfaceFile.CommandEntry> entries) {
    return entries.stream().map(entry -> entry.command).sorted().toList();
  }

  @Test
  void группыРазмещенияЧитаются() throws Exception {
    SubsystemCommandInterfaceFile.Dto edt = EdtSubsystemCommandInterface.read(
        edtSource.resolve("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo"));

    assertThat(edt.placement).isNotEmpty();
    assertThat(edt.placement).allSatisfy(entry -> assertThat(entry.place).isNotEmpty());
  }

  @Test
  void кругЧтениеЗаписьСохраняетИнтерфейс() throws Exception {
    Path subsystem = copySubsystem();
    SubsystemCommandInterfaceFile.Dto before = EdtSubsystemCommandInterface.read(subsystem);

    EdtSubsystemCommandInterface.write(subsystem, before);
    SubsystemCommandInterfaceFile.Dto after = EdtSubsystemCommandInterface.read(subsystem);

    assertThat(commands(after.visibility)).isEqualTo(commands(before.visibility));
    assertThat(commands(after.placement)).isEqualTo(commands(before.placement));
    assertThat(commands(after.order)).isEqualTo(commands(before.order));
    assertThat(after.subsystemsOrder).isEqualTo(before.subsystemsOrder);
  }

  @Test
  void видимостьКомандыМеняется() throws Exception {
    Path subsystem = copySubsystem();
    SubsystemCommandInterfaceFile.Dto dto = EdtSubsystemCommandInterface.read(subsystem);
    String command = dto.visibility.get(0).command;

    dto.visibility.get(0).value = "false";
    EdtSubsystemCommandInterface.write(subsystem, dto);

    SubsystemCommandInterfaceFile.Dto after = EdtSubsystemCommandInterface.read(subsystem);
    assertThat(after.visibility).filteredOn(entry -> entry.command.equals(command))
        .extracting(entry -> entry.value)
        .containsExactly("false");
    assertThat(Files.readString(EdtSubsystemCommandInterface.interfacePath(subsystem), StandardCharsets.UTF_8))
        .contains("<cmi:CommandInterface");
  }

  /** Копия подсистемы во временном каталоге: фикстуру не правим. */
  private Path copySubsystem() throws IOException {
    Path from = edtSource.resolve("Subsystems/_ДемоАнкетирование");
    Path to = workDir.resolve("_ДемоАнкетирование");
    try (Stream<Path> files = Files.walk(from)) {
      for (Path file : files.toList()) {
        Path target = to.resolve(from.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
    return to.resolve("_ДемоАнкетирование.mdo");
  }

  @Test
  void составПланаОбменаСовпадаетСВыгрузкойКонфигуратора() throws Exception {
    List<MdContentMemberDto> edt = EdtExchangePlanContent.read(
        edtSource.resolve("ExchangePlans/_ДемоАвтономнаяРабота/_ДемоАвтономнаяРабота.mdo"));
    List<MdContentMemberDto> designer = io.github.yellowhammer.designerxml.cf.ExchangePlanContentFile.read(
        designerCf.resolve("ExchangePlans/_ДемоАвтономнаяРабота.xml"));

    assertThat(edt).extracting(member -> member.ref)
        .containsExactlyInAnyOrderElementsOf(designer.stream().map(member -> member.ref).toList());
    assertThat(edt).extracting(member -> member.mode).containsOnly("Deny");
  }

  @Test
  void составПланаОбменаПишетсяТочечно() throws Exception {
    Path plan = copyExchangePlan();
    String before = Files.readString(plan, StandardCharsets.UTF_8);
    List<MdContentMemberDto> members = new java.util.ArrayList<>(EdtExchangePlanContent.read(plan));

    // Авторегистрация по умолчанию в файле не пишется, поэтому её включение
    // добавляет строку, а состав остаётся прежним
    String changed = members.get(0).ref;
    members.get(0).mode = "Allow";
    EdtExchangePlanContent.write(plan, members);

    String after = Files.readString(plan, StandardCharsets.UTF_8);
    assertThat(after.lines().count()).isEqualTo(before.lines().count() + 1);
    List<MdContentMemberDto> written = EdtExchangePlanContent.read(plan);
    assertThat(written).hasSize(members.size());
    assertThat(written).filteredOn(member -> member.ref.equals(changed))
        .extracting(member -> member.mode)
        .containsExactly("Allow");
  }

  /** Копия плана обмена во временном каталоге. */
  private Path copyExchangePlan() throws IOException {
    Path from = edtSource.resolve("ExchangePlans/_ДемоАвтономнаяРабота/_ДемоАвтономнаяРабота.mdo");
    Path to = workDir.resolve("_ДемоАвтономнаяРабота.mdo");
    Files.copy(from, to);
    return to;
  }

  @Test
  void версияФорматаПлануОбменаНеНужна() throws Exception {
    // У выгрузки конфигуратора состав лежит отдельным файлом и версии требует
    assertThat(io.github.yellowhammer.designerxml.cf.ExchangePlanContentFile.read(
        designerCf.resolve("ExchangePlans/_ДемоАвтономнаяРабота.xml"))).isNotEmpty();
    assertThat(SchemaVersion.values()).isNotEmpty();
  }
}
