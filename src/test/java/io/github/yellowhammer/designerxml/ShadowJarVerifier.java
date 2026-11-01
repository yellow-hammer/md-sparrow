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

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Регрессия упаковки: проверяет центральный каталог и реальную загрузку только fat JAR. */
public final class ShadowJarVerifier {

  private ShadowJarVerifier() {
  }

  /**
   * Проверяет собранную поставку независимо от classpath тестов.
   *
   * @param args JAR, затем исходные зависимости в формате coordinate=path
   * @throws Exception при потере ресурса, повторе имени или ошибке загрузки
   */
  public static void main(String[] args) throws Exception {
    Path candidate = Path.of(args[0]).toAbsolutePath();
    Map<String, Path> dependencies = new LinkedHashMap<>();
    for (int i = 1; i < args.length; i++) {
      String[] parts = args[i].split("=", 2);
      dependencies.put(parts[0], Path.of(parts[1]));
    }
    try (ZipFile jar = new ZipFile(candidate.toFile())) {
      Set<String> names = new HashSet<>();
      List<String> duplicates = new ArrayList<>();
      var entries = jar.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        if (!names.add(name)) {
          duplicates.add(name);
        }
      }
      require(duplicates.isEmpty(), "Повторяющиеся ZIP entries: " + duplicates);
      for (String path : List.of(
          "META-INF/MANIFEST.MF",
          "META-INF/dependencies/index.tsv",
          "META-INF/services/jakarta.xml.bind.JAXBContextFactory",
          "META-INF/services/javax.xml.stream.XMLInputFactory",
          "io/github/yellowhammer/designerxml/cf/ProjectMetadataTreeBuilder.class",
          "io/github/yellowhammer/designerxml/jaxb/v2_20/mdclasses/MetaDataObject.class",
          "org/eclipse/emf/ecore/EPackage.class",
          "designer-schemas/index.txt",
          "designer-schemas/v8.1c.ru-8.3-MDClasses.xsd",
          "edt-schemas/index.txt", "edt-schemas/version.txt",
          "edt-schemas/g5.1c.ru.v8.dt.metadata.mdclass.ecore",
          "golden/index.txt", "golden/format.txt", "edt-golden/index.txt")) {
        require(names.contains(path), "В fat JAR нет ресурса: " + path);
        require(read(jar, path).length > 0, "Пустой ресурс: " + path);
      }
      verifyIndexes(jar, "designer-schemas/");
      verifyIndexes(jar, "edt-schemas/");
      verifyDependencies(jar, dependencies);
    }
    verifyIsolatedApi(candidate);
    verifyCli(candidate);
    System.out.println("Fat JAR: уникальные entries, services, ресурсы, notices, isolated Java API и CLI проверены.");
  }

  private static void verifyIndexes(ZipFile jar, String directory) throws Exception {
    for (String name : new String(read(jar, directory + "index.txt"), StandardCharsets.UTF_8).lines().toList()) {
      if (!name.isBlank() && !name.startsWith("#")) {
        require(jar.getEntry(directory + name.strip()) != null, "Схема из индекса отсутствует: " + name);
      }
    }
  }

  private static void verifyDependencies(ZipFile jar, Map<String, Path> dependencies) throws Exception {
    Map<String, Set<String>> services = new TreeMap<>();
    Map<String, Set<String>> properties = new TreeMap<>();
    Set<String> expectedIndex = new HashSet<>();
    for (var dependency : dependencies.entrySet()) {
      try (ZipFile source = new ZipFile(dependency.getValue().toFile())) {
        var entries = source.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (entry.isDirectory()) {
            continue;
          }
          byte[] bytes;
          try (InputStream input = source.getInputStream(entry)) {
            bytes = input.readAllBytes();
          }
          String name = entry.getName();
          if (metadata(name)) {
            String target = "META-INF/dependencies/" + dependency.getKey().replace(':', '/') + "/" + name;
            require(Arrays.equals(bytes, read(jar, target)), "Потеря исходного metadata/notice: " + target);
            expectedIndex.add(dependency.getKey() + "\t" + name + "\t" + sha256(bytes) + "\t" + target);
          }
          if (name.startsWith("META-INF/services/")) {
            services.computeIfAbsent(name, unused -> new LinkedHashSet<>()).addAll(providers(bytes));
          }
          if (name.equals("plugin.properties")) {
            Properties values = new Properties();
            try (InputStream input = source.getInputStream(entry)) {
              values.load(input);
            }
            values.forEach((key, value) -> properties.computeIfAbsent(key.toString(), unused -> new LinkedHashSet<>()).add(value.toString()));
          }
        }
      }
    }
    Set<String> actualIndex = new HashSet<>(new String(read(jar, "META-INF/dependencies/index.tsv"), StandardCharsets.UTF_8).lines().toList());
    require(actualIndex.containsAll(expectedIndex), "Неполная карта происхождения notices");
    // Проверяем и собственную LGPL, и каждую строку индекса, включая её checksum.
    boolean ownLicense = false;
    for (String line : actualIndex) {
      if (line.startsWith("coordinate\t")) {
        continue;
      }
      String[] parts = line.split("\t");
      require(parts.length == 4, "Некорректная строка индекса notices");
      require(sha256(read(jar, parts[3])).equals(parts[2]), "Checksum notice не совпала: " + parts[3]);
      ownLicense |= parts[0].startsWith("io.github.yellowhammer:md-sparrow:") && parts[1].equals("LICENCE");
    }
    require(ownLicense, "Нет LGPL самой библиотеки");
    for (var service : services.entrySet()) {
      require(!service.getValue().isEmpty(), "Пустой исходный service descriptor: " + service.getKey());
      require(providers(read(jar, service.getKey())).equals(service.getValue()), "Провайдеры потеряны или добавлены: " + service.getKey());
    }
    Properties merged = new Properties();
    try (InputStream input = jar.getInputStream(jar.getEntry("plugin.properties"))) {
      merged.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
    }
    for (var property : properties.entrySet()) {
      String actual = merged.getProperty(property.getKey());
      require(actual != null, "Потеря сообщения EMF: " + property.getKey());
      if (property.getKey().equals("pluginName") || property.getKey().equals("providerName")) {
        require(new HashSet<>(Arrays.asList(actual.split(" / "))).equals(property.getValue()), "Потеря описательного имени EMF");
      } else {
        require(property.getValue().size() == 1 && property.getValue().contains(actual), "Конфликт рабочего сообщения EMF: " + property.getKey());
      }
    }
  }

  private static boolean metadata(String path) {
    String upper = path.toUpperCase(Locale.ROOT);
    for (String prefix : List.of("META-INF/LICENSE", "META-INF/LICENCE", "META-INF/NOTICE", "LICENSE", "LICENCE", "NOTICE", "COPYRIGHT")) {
      if (upper.equals(prefix) || upper.startsWith(prefix + ".")) {
        return true;
      }
    }
    return Set.of("plugin.xml", "plugin.properties", "about.html", "about.ini", "about.mappings", "about.properties", "modeling32.png").contains(path)
        || path.startsWith("about_files/");
  }

  private static Set<String> providers(byte[] bytes) {
    Set<String> providers = new LinkedHashSet<>();
    new String(bytes, StandardCharsets.UTF_8).lines().forEach(line -> {
      String name = line.split("#", 2)[0].strip();
      if (!name.isEmpty()) {
        providers.add(name);
      }
    });
    return providers;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void verifyIsolatedApi(Path jar) throws Exception {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try (URLClassLoader isolated = new URLClassLoader(new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      thread.setContextClassLoader(isolated);
      Class<?> factory = isolated.loadClass("jakarta.xml.bind.JAXBContextFactory");
      Object provider = ServiceLoader.load((Class) factory, isolated).findFirst().orElseThrow();
      require(provider.getClass().getClassLoader() == isolated, "JAXB provider взят вне fat JAR");
      require(javax.xml.stream.XMLInputFactory.newFactory().getClass().getClassLoader() == isolated, "StAX provider взят вне fat JAR");
      Class<?> builder = isolated.loadClass("io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder");
      require(builder.getClassLoader() == isolated, "API взят из classpath тестов");
      Path fixture = Path.of(ShadowJarVerifier.class.getResource("/shadowjar-project").toURI());
      String expected = Files.readString(fixture.resolve("expected-object.txt")).strip();
      Object dto = builder.getMethod("build", Path.class).invoke(null, fixture);
      require(containsItem(list(dto, "sources"), expected), "API не прочитал объект из искусственной фикстуры");
      Class<?> emf = isolated.loadClass("org.eclipse.emf.ecore.plugin.EcorePlugin");
      Object instance = emf.getField("INSTANCE").get(null);
      Class<?> locator = isolated.loadClass("org.eclipse.emf.common.util.ResourceLocator");
      String message = (String) locator.getMethod("getString", String.class).invoke(instance, "_UI_SimpleListTailLimited_composition");
      Properties bundle = new Properties();
      try (InputStream input = isolated.getResourceAsStream("plugin.properties")) {
        bundle.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
      }
      require(message.equals(bundle.getProperty("_UI_SimpleListTailLimited_composition")), "EMF resource bundle искажает Unicode");
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static boolean containsItem(List<?> nodes, String expected) throws Exception {
    for (Object node : nodes) {
      for (String field : List.of("groups", "subgroups", "items")) {
        try {
          List<?> children = list(node, field);
          for (Object item : children) {
            if (field.equals("items") && expected.equals(item.getClass().getMethod("name").invoke(item))) {
              return true;
            }
          }
          if (containsItem(children, expected)) {
            return true;
          }
        } catch (NoSuchMethodException ignored) {
          // У разных уровней DTO разные списки детей.
        }
      }
    }
    return false;
  }

  private static List<?> list(Object object, String method) throws Exception {
    return (List<?>) object.getClass().getMethod(method).invoke(object);
  }

  private static void verifyCli(Path jar) throws Exception {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    Path output = Files.createTempFile("md-sparrow-help-", ".txt");
    Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-jar", jar.toString(), "--help")
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("CLI --help не завершился");
      }
      require(process.exitValue() == 0 && Files.size(output) > 0, "CLI --help завершился ошибкой");
    } finally {
      Files.deleteIfExists(output);
    }
  }

  private static byte[] read(ZipFile jar, String path) throws Exception {
    ZipEntry entry = jar.getEntry(path);
    require(entry != null, "Ресурс отсутствует: " + path);
    try (InputStream input = jar.getInputStream(entry)) {
      return input.readAllBytes();
    }
  }

  private static String sha256(byte[] bytes) throws Exception {
    return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
