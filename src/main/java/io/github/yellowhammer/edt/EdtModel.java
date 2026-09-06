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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EFactoryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIHandler;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/**
 * Метамодель формата 1С:EDT, загруженная из схем.
 *
 * Формат EDT описан метамоделью EMF, и схемы лежат в том же хранилище, что и XSD
 * конфигуратора: каталог {@code schemas/edt/<версия>/ecore}. Классы модели не
 * генерируются - пакеты читаются из {@code .ecore} и регистрируются по nsURI.
 * В сборку попадает только та часть метамодели, без которой не прочитать
 * метаданные: остальное описывает редактор, отладчик и формы.
 *
 * Метамодель служит справочником: какие свойства есть у класса, какого они типа
 * и что из перечисленного в файле - состав конфигурации, а что её свойство. Сами
 * файлы {@code .mdo} читаются своим разбором: EDT записывает ссылки на типы
 * текстом ({@code <types>Boolean</types>}) и оживляет их собственным реестром
 * типов платформы, которого вне EDT нет.
 *
 * Так поддержка новой версии EDT сводится к появлению её схем в хранилище.
 */
public final class EdtModel {

  /** Каталог схем в ресурсах сборки. */
  private static final String SCHEMAS = "/edt-schemas";

  /** Протокол, по которому схемы ссылаются друг на друга внутри сборки. */
  private static final String SCHEME = "edt-schemas";

  /** Аннотация схемы у ссылок на объекты состава: {@code catalogs}, {@code commonModules}. */
  private static final String MD_CLASS = "http://www.1c.ru/v8/dt/metadata/MdClass";

  /** Пакеты метамодели по nsURI. */
  private final Map<String, EPackage> packages;

  /** Версия EDT, чьи схемы попали в сборку. */
  private final String version;

  private EdtModel(Map<String, EPackage> packages, String version) {
    this.packages = packages;
    this.version = version;
  }

  /**
   * Загружает метамодель из схем, попавших в сборку.
   *
   * @return метамодель метаданных EDT
   * @throws IOException если схем в сборке нет или они не читаются
   */
  public static EdtModel bundled() throws IOException {
    ResourceSet schemaSet = new ResourceSetImpl();
    schemaSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    schemaSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
    schemaSet.getURIConverter().getURIHandlers().add(0, new BundledSchemas());

    Map<String, EPackage> loaded = new LinkedHashMap<>();
    for (String name : schemaNames()) {
      Resource resource = schemaSet.getResource(URI.createURI(SCHEME + ":/" + name), true);
      for (EObject content : resource.getContents()) {
        if (content instanceof EPackage ePackage && ePackage.getNsURI() != null) {
          loaded.put(ePackage.getNsURI(), ePackage);
        }
      }
    }
    if (loaded.isEmpty()) {
      throw new IOException("В сборке нет схем EDT.");
    }

    // Ссылки между пакетами разрешаются по nsURI: пока все не зарегистрированы,
    // прокси остаются неразрешёнными
    loaded.forEach((nsUri, ePackage) -> schemaSet.getPackageRegistry().put(nsUri, ePackage));

    // Часть типов метамодели описана классами самой EDT (uuid, картинки, ссылки
    // на её интерфейсы). Их кода у нас нет, поэтому такие типы читаются строками:
    // для чтения метаданных этого достаточно, а иначе разбор падал бы на первом
    // же атрибуте uuid в корне файла
    loaded.values().forEach(EdtModel::relaxUnavailableTypes);

    return new EdtModel(loaded, resourceLine("version.txt"));
  }

  /** Имена файлов схем: каталог ресурсов внутри jar не перечислить. */
  private static List<String> schemaNames() throws IOException {
    List<String> names = new ArrayList<>();
    InputStream stream = EdtModel.class.getResourceAsStream(SCHEMAS + "/index.txt");
    if (stream == null) {
      throw new IOException("В сборке нет списка схем EDT: " + SCHEMAS + "/index.txt");
    }
    try (InputStream resource = stream;
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        String name = line.trim();
        if (!name.isEmpty()) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /** Первая строка файла из ресурсов сборки. */
  private static String resourceLine(String name) throws IOException {
    InputStream stream = EdtModel.class.getResourceAsStream(SCHEMAS + "/" + name);
    if (stream == null) {
      return "";
    }
    try (InputStream resource = stream;
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      return line == null ? "" : line.trim();
    }
  }

  /** Доступ к схемам сборки по ссылкам между ними. */
  private static final class BundledSchemas extends URIHandlerImpl implements URIHandler {

    @Override
    public boolean canHandle(URI uri) {
      return SCHEME.equals(uri.scheme());
    }

    @Override
    public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
      String name = uri.lastSegment();
      InputStream stream = EdtModel.class.getResourceAsStream(SCHEMAS + "/" + name);
      if (stream == null) {
        throw new IOException("Схема EDT не найдена в сборке: " + name);
      }
      return stream;
    }

    @Override
    public OutputStream createOutputStream(URI uri, Map<?, ?> options) throws IOException {
      throw new IOException("Схемы EDT в сборке не изменяются: " + uri);
    }

    @Override
    public boolean exists(URI uri, Map<?, ?> options) {
      return EdtModel.class.getResource(SCHEMAS + "/" + uri.lastSegment()) != null;
    }
  }

  /**
   * Переводит на строки типы, чьих классов нет в сборке.
   *
   * @param ePackage пакет метамодели
   */
  private static void relaxUnavailableTypes(EPackage ePackage) {
    for (EClassifier classifier : ePackage.getEClassifiers()) {
      if (!(classifier instanceof EDataType dataType)) {
        continue;
      }
      String instanceClass = dataType.getInstanceClassName();
      if (instanceClass == null || instanceClass.isEmpty() || isAvailable(instanceClass)) {
        continue;
      }
      dataType.setInstanceClassName("java.lang.String");
    }
    ePackage.setEFactoryInstance(new LenientFactory());
  }

  /** Есть ли класс типа в сборке. */
  private static boolean isAvailable(String className) {
    try {
      Class.forName(className, false, EdtModel.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError error) {
      return false;
    }
  }

  /**
   * Фабрика, которой неизвестный тип данных не мешает прочитать файл.
   *
   * Значение остаётся строкой, как оно записано в файле.
   */
  private static final class LenientFactory extends EFactoryImpl {

    @Override
    public Object createFromString(EDataType dataType, String literal) {
      // Идентификаторы объектов метамодель объявляет java.util.UUID, а базовая
      // фабрика такой тип не разбирает
      if (dataType.getInstanceClass() == UUID.class) {
        try {
          return UUID.fromString(literal);
        } catch (IllegalArgumentException error) {
          return null;
        }
      }
      try {
        return super.createFromString(dataType, literal);
      } catch (RuntimeException error) {
        return literal;
      }
    }

    @Override
    public String convertToString(EDataType dataType, Object value) {
      try {
        return super.convertToString(dataType, value);
      } catch (RuntimeException error) {
        return value == null ? null : value.toString();
      }
    }
  }

  /**
   * Загруженные пространства имён метамодели.
   *
   * @return nsURI всех пакетов
   */
  public List<String> namespaces() {
    return new ArrayList<>(packages.keySet());
  }

  /**
   * Пакет метамодели по nsURI.
   *
   * @param nsUri пространство имён, например {@code http://g5.1c.ru/v8/dt/metadata/mdclass}
   * @return пакет или {@code null}, если такого нет
   */
  public EPackage packageOf(String nsUri) {
    return packages.get(nsUri);
  }

  /**
   * Версия EDT, чьи схемы попали в сборку.
   *
   * @return версия вида {@code 2026.1}
   */
  public String version() {
    return version;
  }

  /**
   * Класс метамодели по имени.
   *
   * @param name имя класса: {@code Configuration}, {@code Catalog}
   * @return класс или {@code null}, если такого в метамодели нет
   */
  public EClass classOf(String name) {
    for (EPackage ePackage : packages.values()) {
      if (ePackage.getEClassifier(name) instanceof EClass eClass) {
        return eClass;
      }
    }
    return null;
  }

  /**
   * Состав класса: чем описан один его элемент.
   *
   * @param feature имя элемента в файле: {@code catalogs}, {@code languages}
   * @param objectType вид объекта: {@code Catalog}, {@code Language}
   * @param inline объект записан внутри файла, а не ссылкой на отдельный файл
   */
  public record Composition(String feature, String objectType, boolean inline) {
  }

  /**
   * Состав, перечисленный классом.
   *
   * По одной строке файла не понять, состав это или свойство:
   * {@code <catalogs>Catalog.Валюты</catalogs>} и
   * {@code <defaultRoles>Role.Администратор</defaultRoles>} записаны одинаково.
   * Схема их различает аннотацией: у состава стоит класс метаданных, у свойства -
   * свойство метаданных.
   *
   * @param name имя класса: {@code Configuration}, {@code Subsystem}
   * @return состав в порядке схемы; пусто, если класса в метамодели нет
   */
  public List<Composition> composition(String name) {
    EClass eClass = classOf(name);
    if (eClass == null) {
      return List.of();
    }
    List<Composition> composition = new ArrayList<>();
    for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
      if (feature.getEAnnotation(MD_CLASS) == null || !(feature instanceof EReference reference)) {
        continue;
      }
      // Языки и подобные им объекты живут внутри файла владельца, поэтому имя
      // берётся не из ссылки, а из самого объекта
      composition.add(new Composition(
          feature.getName(),
          reference.getEReferenceType().getName(),
          reference.isContainment()));
    }
    return composition;
  }
}
