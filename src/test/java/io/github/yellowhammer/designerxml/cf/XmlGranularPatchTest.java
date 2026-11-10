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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Раскладка фрагмента сплайса по строкам: разметка между тегами расставляется заново, а текст
 * элементов переносится дословно — с переводом строки файла и без отступов на продолжениях.
 */
class XmlGranularPatchTest {

  private static final String CRLF_FILE = "<root>\r\n\t\t<value>x</value>\r\n</root>";

  private static final String LF_FILE = "<root>\n\t\t<value>x</value>\n</root>";

  /** Смещение заменяемого узла: его строка задаёт отступ раскладки. */
  private static final int AT = CRLF_FILE.indexOf("<value>");

  @Test
  void продолженияМногострочногоТекстаНеПолучаютОтступа() {
    String fragment = "<title><v8:item><v8:lang>ru</v8:lang><v8:content>две\nстроки</v8:content></v8:item></title>";
    assertThat(XmlGranularPatch.formatReplacementPreservingIndent(CRLF_FILE, AT, fragment)).isEqualTo(
      "<title>\r\n\t\t\t<v8:item>\r\n\t\t\t\t<v8:lang>ru</v8:lang>\r\n\t\t\t\t<v8:content>две\r\nстроки</v8:content>"
        + "\r\n\t\t\t</v8:item>\r\n\t\t</title>");
    assertThat(XmlGranularPatch.formatInsertion(LF_FILE, "\t", fragment)).isEqualTo(
      "\t<title>\n\t\t<v8:item>\n\t\t\t<v8:lang>ru</v8:lang>\n\t\t\t<v8:content>две\nстроки</v8:content>"
        + "\n\t\t</v8:item>\n\t</title>");
  }

  @Test
  void пустыеСтрокиВедущиеТабуляцииИКонцыСтрокТекстаСохраняются() {
    String text = "а\r\n\r\n\t\tб  \rв\n";
    String fragment = "<value xsi:type=\"xs:string\">" + text + "</value>";
    // Один узел без раскладки: меняется только вид перевода строки
    assertThat(XmlGranularPatch.formatReplacementPreservingIndent(CRLF_FILE, AT, fragment))
      .isEqualTo("<value xsi:type=\"xs:string\">а\r\n\r\n\t\tб  \r\nв\r\n</value>");
    assertThat(XmlGranularPatch.formatInsertion(LF_FILE, "\t\t", "<p><name>x</name>" + fragment + "</p>"))
      .isEqualTo("\t\t<p>\n\t\t\t<name>x</name>\n"
        + "\t\t\t<value xsi:type=\"xs:string\">а\n\n\t\tб  \nв\n</value>\n\t\t</p>");
  }

  @Test
  void пробельноеЗначениеЭлементаНеСхлопываетсяАРазметкаМеждуТегамиРасставляетсяЗаново() {
    // Готовая разметка исходника с чужими отступами раскладывается по месту
    String pretty = "<p>\n    <name>x</name>\n    <value>\n</value>\n  <empty></empty>\n</p>";
    assertThat(XmlGranularPatch.formatInsertion(CRLF_FILE, "\t", pretty))
      .isEqualTo("\t<p>\r\n\t\t<name>x</name>\r\n\t\t<value>\r\n</value>\r\n\t\t<empty></empty>\r\n\t</p>");
    // Пробел без перевода строки — тоже значение
    assertThat(XmlGranularPatch.formatInsertion(LF_FILE, "", "<p><value> </value><b/></p>"))
      .isEqualTo("<p>\n\t<value> </value>\n\t<b/>\n</p>");
  }

  @Test
  void кавычкиАтрибутовИКомментарииНеСбиваютРазметку() {
    String fragment = "<p a=\"x>y\"><!-- примечание --><v>1</v></p>";
    assertThat(XmlGranularPatch.formatInsertion(LF_FILE, "", fragment))
      .isEqualTo("<p a=\"x>y\">\n\t<!-- примечание -->\n\t<v>1</v>\n</p>");
  }

  @Test
  void переводыСтрокПриводятсяКПереводуСтрокиФайла() {
    assertThat(XmlGranularPatch.lineBreaks("а\r\nб\rв\nг", "\r\n")).isEqualTo("а\r\nб\r\nв\r\nг");
    assertThat(XmlGranularPatch.lineBreaks("а\r\nб\rв\nг", "\n")).isEqualTo("а\nб\nв\nг");
    assertThat(XmlGranularPatch.lineBreaks("без переводов", "\r\n")).isEqualTo("без переводов");
  }
}
