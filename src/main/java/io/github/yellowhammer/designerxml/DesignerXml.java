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
package io.github.yellowhammer.designerxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Чтение и запись корневого элемента {@code MetaDataObject} (и вложенной структуры) по XSD выбранной версии.
 * <p>
 * Для каждой {@link SchemaVersion} генерируются свои классы JAXB (разные пакеты). Результат {@link #read read} —
 * обычно {@link jakarta.xml.bind.JAXBElement} с {@code declaredType = MetaDataObject} соответствующего пакета
 * {@code ...jaxb.v2_20.mdclasses} или {@code ...jaxb.v2_21.mdclasses}; значение корня — {@code getValue()}.
 */
public final class DesignerXml {

  private static final String JAXB_ENCODING = "UTF-8";
  /** См. org.glassfish.jaxb.runtime.MarshallerProperties (не в jakarta.xml.bind-api). */
  private static final String GLASSFISH_NAMESPACE_PREFIX_MAPPER = "org.glassfish.jaxb.namespacePrefixMapper";

  private DesignerXml() {
  }

  /**
   * Разбор XML-файла метаданных.
   *
   * @param path путь к {@code .xml}
   * @param version версия набора XSD / пакетов JAXB
   * @return корень JAXB (обычно {@code MetaDataObject})
   * @throws JAXBException если XML не соответствует схеме или разбор невозможен
   * @throws IOException при ошибке чтения файла
   */
  public static Object read(Path path, SchemaVersion version) throws JAXBException, IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return unmarshal(version, in);
    }
  }

  /**
   * Разбор XML из символьного потока.
   *
   * @param reader поток с XML (кодировка — ответственность вызывающего)
   * @param version версия схем
   * @return корень JAXB
   * @throws JAXBException при ошибке разбора
   */
  public static Object read(Reader reader, SchemaVersion version) throws JAXBException {
    return unmarshal(version, reader);
  }

  /**
   * Десериализация из байтового потока (кодировка по объявлению XML или UTF-8).
   *
   * @param version версия схем
   * @param inputStream поток с XML
   * @return корень JAXB
   * @throws JAXBException при ошибке разбора
   */
  public static Object unmarshal(SchemaVersion version, InputStream inputStream) throws JAXBException {
    Unmarshaller u = version.jaxbContext().createUnmarshaller();
    return u.unmarshal(inputStream);
  }

  /**
   * Десериализация из символьного потока.
   *
   * @param version версия схем
   * @param reader поток с XML
   * @return корень JAXB
   * @throws JAXBException при ошибке разбора
   */
  public static Object unmarshal(SchemaVersion version, Reader reader) throws JAXBException {
    Unmarshaller u = version.jaxbContext().createUnmarshaller();
    return u.unmarshal(reader);
  }

  /**
   * Запись корня JAXB в файл (перезапись).
   *
   * @param path путь к выходному {@code .xml}
   * @param jaxbRoot корень, полученный из {@link #read} / {@link #unmarshal}
   * @param version версия схем (Marshaller из того же контекста)
   * @param options форматирование и прочие параметры записи
   * @throws JAXBException при ошибке сериализации
   * @throws IOException при ошибке записи файла
   */
  public static void write(Path path, Object jaxbRoot, SchemaVersion version, WriteOptions options)
    throws JAXBException, IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    marshal(version, jaxbRoot, buf, options);
    byte[] bytes = buf.toByteArray();
    Path preserveFrom = options.preserveRootNamespacesFrom();
    if (preserveFrom != null) {
      try {
        bytes = MetaDataObjectRootNamespaces.mergeMarshalledBytes(
          preserveFrom, bytes, options.formatPretty());
      } catch (Exception e) {
        throw new IOException(
          "Не удалось дополнить объявления xmlns на корне MetaDataObject из " + preserveFrom, e);
      }
    }
    bytes = normalizeLineEndings(bytes, options);
    Files.write(path, bytes);
  }

  /**
   * Сериализация корня JAXB в байтовый поток (кодировка UTF-8).
   *
   * @param version версия схем
   * @param jaxbRoot корень объекта
   * @param out поток вывода (не закрывается методом)
   * @param options параметры {@link Marshaller}
   * @throws JAXBException при ошибке сериализации
   */
  public static void marshal(SchemaVersion version, Object jaxbRoot, OutputStream out, WriteOptions options)
    throws JAXBException {
    Marshaller m = createMarshaller(version, options);
    m.marshal(jaxbRoot, out);
  }

  /**
   * Сериализация фрагмента (без XML-декларации), например только {@code Properties} или {@code ChildObjects}.
   * <p>
   * При {@link WriteOptions#formatPretty()} после JAXB выполняется дополнительное форматирование: с
   * {@link Marshaller#JAXB_FRAGMENT} отступы marshaller'ом часто не применяются.
   *
   * @param jaxbFragmentRoot обычно {@link jakarta.xml.bind.JAXBElement} с нужным {@code QName}
   */
  public static byte[] marshalFragment(SchemaVersion version, Object jaxbFragmentRoot, WriteOptions options)
    throws JAXBException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    Marshaller m = createMarshaller(version, options);
    m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    m.marshal(jaxbFragmentRoot, buf);
    byte[] raw = buf.toByteArray();
    if (!options.formatPretty()) {
      return raw;
    }
    try {
      return XmlFragmentUtf8Indent.indent(raw);
    } catch (Exception ignored) {
      return raw;
    }
  }

  /**
   * Сериализация корня JAXB в символьный поток.
   *
   * @param version версия схем
   * @param jaxbRoot корень объекта
   * @param writer поток вывода (не закрывается методом)
   * @param options параметры {@link Marshaller}
   * @throws JAXBException при ошибке сериализации
   */
  public static void marshal(SchemaVersion version, Object jaxbRoot, Writer writer, WriteOptions options)
    throws JAXBException {
    Marshaller m = createMarshaller(version, options);
    m.marshal(jaxbRoot, writer);
  }

  private static Marshaller createMarshaller(SchemaVersion version, WriteOptions options) throws JAXBException {
    JAXBContext ctx = version.jaxbContext();
    Marshaller m = ctx.createMarshaller();
    m.setProperty(Marshaller.JAXB_ENCODING, JAXB_ENCODING);
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, options.formatPretty());
    if (options.oneCNamespacePrefixes()) {
      m.setProperty(GLASSFISH_NAMESPACE_PREFIX_MAPPER, new OneCDesignerXmlNamespacePrefixMapper());
    }
    return m;
  }

  private static byte[] normalizeLineEndings(byte[] bytes, WriteOptions options) {
    if (!options.normalizeLineEndings()) {
      return bytes;
    }
    String text = new String(bytes, StandardCharsets.UTF_8)
      .replace("\r\n", "\n")
      .replace('\r', '\n');
    if (text.startsWith("<?xml") && text.contains("standalone=\"yes\"")) {
      text = text.replace(" standalone=\"yes\"", "");
    }
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Проверка XML по XSD для выбранной версии (без полного разбора в доменную модель приложения).
   *
   * @param xmlPath путь к проверяемому {@code .xml}
   * @param version версия схем (подкаталог в {@code xsdRoot})
   * @param xsdRoot корень репозитория {@code namespace-forest} (родитель {@code schemas/…})
   * @throws Exception ошибки XSD/API валидации или I/O ({@link XmlValidator})
   */
  public static void validate(Path xmlPath, SchemaVersion version, Path xsdRoot) throws Exception {
    XmlValidator.validate(xmlPath, version, xsdRoot);
  }

  /**
   * То же, с явным путём к {@code catalog.xml}, если в каталоге со схемами его нет.
   *
   * @param xmlPath путь к проверяемому {@code .xml}
   * @param version версия схем
   * @param xsdRoot корень {@code namespace-forest}
   * @param catalogFile OASIS catalog или {@code null} для {@code schemas/…/catalog.xml}
   * @throws Exception ошибки валидации или I/O
   */
  public static void validate(Path xmlPath, SchemaVersion version, Path xsdRoot, Path catalogFile) throws Exception {
    XmlValidator.validate(xmlPath, version, xsdRoot, catalogFile);
  }
}
