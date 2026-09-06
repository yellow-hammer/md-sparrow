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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.yellowhammer.designerxml.cf.CfObjectPathResolver;

/**
 * Раскладка проекта 1С:EDT.
 *
 * Каталоги видов объектов те же, что в выгрузке конфигуратора, а вот сам объект
 * лежит в своём каталоге: {@code Catalogs/Валюты/Валюты.mdo} вместо
 * {@code Catalogs/Валюты.xml}. Модули лежат рядом с объектом, без {@code Ext}.
 */
public final class EdtLayout {

  /** Каталог исходников проекта. */
  public static final String SOURCE_DIR = "src";

  /** Корень конфигурации внутри исходников. */
  public static final String CONFIGURATION_MDO = "Configuration/Configuration.mdo";

  /** Файл описания проекта: по нему EDT узнаёт своё имя. */
  public static final String PROJECT_FILE = ".project";

  private EdtLayout() {
  }

  /**
   * Корень конфигурации проекта.
   *
   * @param projectDir каталог проекта
   * @return файл {@code src/Configuration/Configuration.mdo}
   */
  public static Path configurationMdo(Path projectDir) {
    return projectDir.resolve(SOURCE_DIR).resolve(CONFIGURATION_MDO);
  }

  /**
   * Проект ли это EDT.
   *
   * @param projectDir проверяемый каталог
   * @return {@code true}, если внутри лежит конфигурация в формате EDT
   */
  public static boolean isProject(Path projectDir) {
    return Files.isRegularFile(configurationMdo(projectDir));
  }

  /**
   * Файл объекта в формате EDT.
   *
   * @param file проверяемый файл
   * @return {@code true} у файла {@code .mdo}
   */
  public static boolean isObjectFile(Path file) {
    return file != null && isObjectFile(file.getFileName().toString());
  }

  /**
   * Файл объекта в формате EDT.
   *
   * @param path путь к файлу
   * @return {@code true} у файла {@code .mdo}
   */
  public static boolean isObjectFile(String path) {
    return path != null && path.endsWith(".mdo");
  }

  /**
   * Файл управляемой формы в формате EDT.
   *
   * Форма лежит своим файлом рядом с описанием: {@code Form.form} вместо
   * {@code Ext/Form.xml}.
   *
   * @param path путь к файлу
   * @return {@code true} у файла {@code .form}
   */
  public static boolean isFormFile(String path) {
    return path != null && path.endsWith(".form");
  }

  /**
   * Файл схемы компоновки в формате EDT.
   *
   * Сама схема у обоих форматов одна и та же, различается только имя файла:
   * {@code Template.dcs} вместо {@code Ext/Template.xml}.
   *
   * @param path путь к файлу
   * @return {@code true} у файла {@code .dcs}
   */
  public static boolean isSchemaFile(String path) {
    return path != null && path.endsWith(".dcs");
  }

  /**
   * Файл объекта метаданных.
   *
   * @param sourceRoot каталог {@code src} проекта
   * @param objectType вид объекта: {@code Catalog}, {@code Document}
   * @param name имя объекта
   * @return файл {@code <Каталог вида>/<Имя>/<Имя>.mdo}, если он есть
   * @throws IOException если каталог проекта не читается
   */
  public static Optional<Path> objectMdo(Path sourceRoot, String objectType, String name) throws IOException {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    if ("Subsystem".equals(objectType)) {
      return subsystemMdo(sourceRoot, name);
    }
    String directory = CfObjectPathResolver.subdirsByType().get(objectType);
    if (directory == null) {
      return Optional.empty();
    }
    Path file = sourceRoot.resolve(directory).resolve(name).resolve(name + ".mdo");
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  /** Подсистема: вложенные лежат внутри родителя, поэтому ищутся по всему каталогу. */
  private static Optional<Path> subsystemMdo(Path sourceRoot, String name) throws IOException {
    Path root = sourceRoot.resolve("Subsystems");
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.getFileName().toString().equals(name + ".mdo")).findFirst();
    }
  }

  /**
   * Модуль рядом с объектом.
   *
   * @param objectMdo файл объекта
   * @param moduleName имя модуля: {@code Module}, {@code ObjectModule}, {@code ManagerModule}
   * @return файл модуля, если он есть
   */
  public static Optional<Path> module(Path objectMdo, String moduleName) {
    Path file = objectMdo.getParent().resolve(moduleName + ".bsl");
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  /**
   * Форма объекта.
   *
   * @param objectMdo файл объекта
   * @param formName имя формы
   * @return каталог формы, если он есть
   */
  public static Optional<Path> form(Path objectMdo, String formName) {
    Path directory = objectMdo.getParent().resolve("Forms").resolve(formName);
    return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
  }

  /**
   * Проекты EDT рабочей области.
   *
   * @param workspaceRoot корень рабочей области
   * @return каталоги проектов: сам корень, если он проект, иначе его подкаталоги
   * @throws IOException если каталог не читается
   */
  public static List<Path> projects(Path workspaceRoot) throws IOException {
    if (isProject(workspaceRoot)) {
      return List.of(workspaceRoot);
    }
    if (!Files.isDirectory(workspaceRoot)) {
      return List.of();
    }
    try (Stream<Path> children = Files.list(workspaceRoot)) {
      return children.filter(EdtLayout::isProject).sorted().toList();
    }
  }
}
