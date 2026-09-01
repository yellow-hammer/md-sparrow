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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Состав плана обмена в формате 1С:EDT.
 *
 * Состав лежит в самом описании плана: узел на объект, внутри ссылка и режим
 * регистрации изменений. У выгрузки конфигуратора он вынесен отдельным файлом,
 * поэтому здесь своё чтение и своя запись, а контракт общий.
 */
public final class EdtExchangePlanContent {

  /** Отступ уровня в файлах EDT. */
  private static final String INDENT = "  ";

  /** Режим авторегистрации по умолчанию: файл его не пишет. */
  private static final String DEFAULT_MODE = "Deny";

  private EdtExchangePlanContent() {
  }

  /**
   * Читает состав плана обмена.
   *
   * @param exchangePlanMdo файл плана обмена
   * @return объекты состава в порядке файла
   * @throws IOException если файл не читается
   */
  public static List<MdContentMemberDto> read(Path exchangePlanMdo) throws IOException {
    List<MdContentMemberDto> members = new ArrayList<>();
    for (EdtNode item : EdtObjectReader.read(exchangePlanMdo).list("content")) {
      String ref = item.property("mdObject");
      if (ref.isEmpty()) {
        continue;
      }
      // Авторегистрацию файл пишет, только когда она не по умолчанию
      String mode = item.property("autoRecord");
      members.add(new MdContentMemberDto(ref, mode.isEmpty() ? DEFAULT_MODE : mode, null));
    }
    return members;
  }

  /**
   * Записывает состав плана обмена.
   *
   * Меняется только состав: остальные свойства плана и порядок его элементов
   * остаются как были.
   *
   * @param exchangePlanMdo файл плана обмена
   * @param members объекты состава целиком
   * @throws IOException если файл не читается или не пишется
   */
  public static void write(Path exchangePlanMdo, List<MdContentMemberDto> members) throws IOException {
    String xml = Files.readString(exchangePlanMdo, StandardCharsets.UTF_8);
    String eol = xml.contains("\r\n") ? "\r\n" : "\n";
    try {
      List<Region> written = EdtObjectRegions.properties(xml, "content");
      String block = block(members, eol);

      if (written.isEmpty()) {
        int at = insertionPoint(xml);
        Files.writeString(exchangePlanMdo, xml.substring(0, at) + block + xml.substring(at),
            StandardCharsets.UTF_8);
        return;
      }

      // Состав переписывается одним куском: он идёт в файле подряд
      int start = EdtObjectRegions.lineStart(xml, written.get(0).start());
      int end = lineEnd(xml, written.get(written.size() - 1).end());
      Files.writeString(exchangePlanMdo, xml.substring(0, start) + block + xml.substring(end),
          StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать план обмена: " + exchangePlanMdo, error);
    }
  }

  /** Разметка состава: узел на объект. */
  private static String block(List<MdContentMemberDto> members, String eol) {
    StringBuilder block = new StringBuilder();
    for (MdContentMemberDto member : members) {
      if (member == null || member.ref == null || member.ref.isBlank()) {
        continue;
      }
      block.append(INDENT).append("<content>").append(eol);
      block.append(INDENT).append(INDENT).append("<mdObject>").append(escape(member.ref))
          .append("</mdObject>").append(eol);
      if (member.mode != null && !member.mode.isBlank() && !member.mode.equals(DEFAULT_MODE)) {
        block.append(INDENT).append(INDENT).append("<autoRecord>").append(escape(member.mode))
            .append("</autoRecord>").append(eol);
      }
      block.append(INDENT).append("</content>").append(eol);
    }
    return block.toString();
  }

  /** Куда встаёт состав, когда его в файле ещё нет: перед закрывающим тегом плана. */
  private static int insertionPoint(String xml) {
    return EdtObjectRegions.lineStart(xml, xml.lastIndexOf("</"));
  }

  private static int lineEnd(String xml, int end) {
    int line = xml.indexOf('\n', end);
    return line < 0 ? xml.length() : line + 1;
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
