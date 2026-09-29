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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

/**
 * Заимствование объекта в расширение проекта 1С:EDT.
 *
 * Заимствованный объект 1С:EDT записывает коротко: имя, принадлежность «Adopted»,
 * пустое описание расширения его вида и порождаемые типы в том же составе, что у
 * оригинала, но со своими идентификаторами. Свои у него и идентификаторы корня, у
 * плана обмена вместе с {@code thisNode}. Так же записано и здесь; ссылка в
 * составе расширения встаёт на место по порядку схемы.
 */
public final class EdtBorrow {

  private static final String EXTENSION_NAMESPACE = "http://g5.1c.ru/v8/dt/metadata/mdclass/extension";
  private static final Pattern ROOT = Pattern.compile("<mdclass:([A-Za-z]+)\\b([^>]*)>");
  private static final Pattern ATTRIBUTE = Pattern.compile("\\s([A-Za-z]+)=\"([^\"]*)\"");
  private static final String UUID = "uuid";
  /** Тип идентификатора в метамодели EDT. */
  private static final String UUID_TYPE = "Uuid";
  private static final Pattern PRODUCED_TYPES = Pattern.compile("[ \\t]*<producedTypes>.*?</producedTypes>\\r?\\n", Pattern.DOTALL);
  private static final Pattern NAME = Pattern.compile("<name>([^<]+)</name>");
  private static final String INDENT = "  ";

  private EdtBorrow() {
  }

  /**
   * Заимствует объект в расширение и вписывает его в состав.
   *
   * @param objectMdo описание объекта расширяемой конфигурации
   * @param extensionConfigurationMdo описание расширения
   * @param model метамодель EDT
   * @return описание заимствованного объекта
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static Path borrowObject(Path objectMdo, Path extensionConfigurationMdo, EdtModel model) throws IOException {
    String original = Files.readString(objectMdo, StandardCharsets.UTF_8);
    Matcher root = ROOT.matcher(original);
    Map<String, String> attributes = new HashMap<>();
    if (root.find()) {
      Matcher attribute = ATTRIBUTE.matcher(root.group(2));
      while (attribute.find()) {
        attributes.put(attribute.group(1), attribute.group(2));
      }
    }
    if (!attributes.containsKey(UUID)) {
      throw new IllegalArgumentException("Не найден корневой узел объекта в " + objectMdo);
    }
    String kind = root.group(1);
    Matcher name = NAME.matcher(original);
    if (!name.find(root.end())) {
      throw new IllegalArgumentException("У объекта нет имени: " + objectMdo);
    }
    String objectName = name.group(1);
    EPackage extensions = model.packageOf(EXTENSION_NAMESPACE);
    String extensionClass = kind + "Extension";
    if (extensions == null || extensions.getEClassifier(extensionClass) == null) {
      throw new IllegalArgumentException("Объекты вида " + kind + " в расширение не заимствуются.");
    }

    Path subdir = objectMdo.toAbsolutePath().normalize().getParent().getParent();
    Path target = EdtObjectScaffold.sourceRoot(extensionConfigurationMdo)
        .resolve(subdir.getFileName().toString())
        .resolve(objectName)
        .resolve(objectName + ".mdo");
    if (Files.exists(target)) {
      throw new IllegalArgumentException("Объект уже заимствован: " + objectName);
    }

    String eol = original.contains("\r\n") ? "\r\n" : "\n";
    Matcher produced = PRODUCED_TYPES.matcher(original);
    String producedTypes = produced.find() ? produced.group() : "";
    String adopted = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + eol
        + "<mdclass:" + kind + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
        + " xmlns:mdclass=\"http://g5.1c.ru/v8/dt/metadata/mdclass\""
        + " xmlns:mdclassExtension=\"" + EXTENSION_NAMESPACE + "\""
        + identifiers(model.classOf(kind), attributes, objectMdo) + ">" + eol
        + producedTypes
        + INDENT + "<name>" + objectName + "</name>" + eol
        + INDENT + "<objectBelonging>Adopted</objectBelonging>" + eol
        + INDENT + "<extension xsi:type=\"mdclassExtension:" + extensionClass + "\"/>" + eol
        + "</mdclass:" + kind + ">" + eol;

    Files.createDirectories(target.getParent());
    // Идентификаторы у заимствованного объекта свои: связь с оригиналом держится на имени
    String seed = EdtObjectScaffold.seed("borrow|" + kind + "|" + objectName,
        Files.readString(extensionConfigurationMdo, StandardCharsets.UTF_8));
    Files.writeString(target, EdtObjectScaffold.freshUuids(adopted, seed), StandardCharsets.UTF_8);
    EdtObjectScaffold.appendReference(extensionConfigurationMdo, model, kind, objectName);
    return target;
  }

  /**
   * Идентификаторы корня: {@code uuid} и остальные обязательные идентификаторы класса, у плана
   * обмена - {@code thisNode}. Значения - оригинала, свои они получают вместе с остальными.
   */
  private static String identifiers(EClass eClass, Map<String, String> original, Path objectMdo) {
    StringBuilder out = new StringBuilder(" " + UUID + "=\"" + original.get(UUID) + "\"");
    for (EAttribute attribute : eClass.getEAllAttributes()) {
      String name = attribute.getName();
      if (!attribute.isRequired() || UUID.equals(name) || !UUID_TYPE.equals(attribute.getEAttributeType().getName())) {
        continue;
      }
      String value = original.get(name);
      if (value == null) {
        throw new IllegalArgumentException("У объекта нет идентификатора " + name + ": " + objectMdo);
      }
      out.append(' ').append(name).append("=\"").append(value).append('"');
    }
    return out.toString();
  }
}
