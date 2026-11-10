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

import static io.github.yellowhammer.edt.EdtXmlText.escape;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Состав общего реквизита в формате 1С:EDT.
 *
 * Элемент состава - узел: ссылка на объект, использование и условное
 * разделение. Использование по умолчанию файл не пишет.
 */
final class EdtCommonAttributeContent {

  /** Отступ уровня в файлах EDT. */
  private static final String INDENT = "  ";
  /** Свойство состава. */
  static final String CONTENT = "content";
  private static final String METADATA = "metadata";
  private static final String USE = "use";
  private static final String SEPARATION = "conditionalSeparation";

  private EdtCommonAttributeContent() {
  }

  /**
   * Класс элемента состава.
   *
   * @param owner класс объекта в метамодели
   * @return класс элемента или {@code null}, если состав вида не из таких узлов
   */
  static EClass itemClass(EClass owner) {
    EStructuralFeature feature = owner == null ? null : owner.getEStructuralFeature(CONTENT);
    if (feature instanceof EReference reference && reference.isContainment()
        && reference.getEReferenceType().getEStructuralFeature(METADATA) != null) {
      return reference.getEReferenceType();
    }
    return null;
  }

  /**
   * Читает состав.
   *
   * @param node узел объекта
   * @param owner класс объекта в метамодели
   * @return элементы в порядке файла; {@code null}, если состав вида не из таких узлов
   */
  static List<MdContentMemberDto> read(EdtNode node, EClass owner) {
    EClass item = itemClass(owner);
    if (item == null) {
      return null;
    }
    List<MdContentMemberDto> members = new ArrayList<>();
    for (EdtNode written : node.list(CONTENT)) {
      String ref = written.property(METADATA);
      if (ref.isEmpty()) {
        continue;
      }
      members.add(new MdContentMemberDto(
          ref, EdtPropertyValues.text(written, item, USE), written.property(SEPARATION)));
    }
    return members;
  }

  /**
   * Элементы в написании файла: использование литералом схемы, по умолчанию -
   * {@code null}.
   *
   * @param members элементы контракта
   * @param item класс элемента
   * @return элементы без пустых ссылок
   */
  static List<MdContentMemberDto> written(List<MdContentMemberDto> members, EClass item) {
    EAttribute use = (EAttribute) item.getEStructuralFeature(USE);
    String byDefault = use.getDefaultValue() instanceof Enumerator literal ? literal.getLiteral() : null;
    List<MdContentMemberDto> written = new ArrayList<>();
    for (MdContentMemberDto member : members) {
      if (member == null || member.ref == null || member.ref.isBlank()) {
        continue;
      }
      String mode = member.mode == null || member.mode.isBlank() ? null : literal(use, member.mode);
      written.add(new MdContentMemberDto(
          member.ref, mode == null || mode.equals(byDefault) ? null : mode, member.conditionalSeparation));
    }
    return written;
  }

  /**
   * Разметка элементов: первый без отступа, следующие с новой строки.
   *
   * @param members элементы в написании файла
   * @param indent отступ элемента
   * @param eol перевод строки файла
   * @return текст элементов
   */
  static String render(List<MdContentMemberDto> members, String indent, String eol) {
    StringBuilder text = new StringBuilder();
    for (MdContentMemberDto member : members) {
      if (text.length() > 0) {
        text.append(eol).append(indent);
      }
      text.append('<').append(CONTENT).append('>').append(eol);
      line(text, indent, METADATA, member.ref, eol);
      if (member.mode != null) {
        line(text, indent, USE, member.mode, eol);
      }
      if (member.conditionalSeparation != null && !member.conditionalSeparation.isBlank()) {
        line(text, indent, SEPARATION, member.conditionalSeparation, eol);
      }
      text.append(indent).append("</").append(CONTENT).append('>');
    }
    return text.toString();
  }

  private static void line(StringBuilder text, String indent, String name, String value, String eol) {
    text.append(indent).append(INDENT)
        .append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>')
        .append(eol);
  }

  /** Литерал схемы по имени константы контракта: {@code DontUse} для {@code DONT_USE}. */
  private static String literal(EAttribute use, String mode) {
    for (EEnumLiteral literal : ((EEnum) use.getEType()).getELiterals()) {
      if (EdtPropertyValues.constantName(literal.getName()).equals(mode)) {
        return literal.getLiteral();
      }
    }
    throw new IllegalArgumentException("Использование в составе не принимает значение " + mode);
  }
}
