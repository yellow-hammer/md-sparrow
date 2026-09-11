/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Язык текстов конфигурации.
 *
 * <p>Синоним, представления и пояснение хранятся по строке на язык, а какой язык
 * основной, знает сама конфигурация: у неё есть ссылка на объект языка, а у того
 * код, который и стоит в строках. У конфигурации не на русском чтение по
 * русскому коду не находит ничего, а запись уводит текст в язык, которого в
 * конфигурации нет.
 *
 * <p>Порядок разрешения: основной язык конфигурации, иначе первый её язык, иначе
 * {@code ru}.
 */
public final class ConfigurationLanguage {

  /** Ссылка на объект языка: {@code Language.Русский}. */
  private static final String REFERENCE_PREFIX = "Language.";

  /** Язык, когда конфигурация о своих языках ничего не говорит. */
  public static final String FALLBACK = "ru";

  /** На сколько уровней вверх от файла объекта искать корень конфигурации. */
  private static final int ROOT_SEARCH_DEPTH = 6;

  private static final Pattern EDT_LANGUAGE = Pattern.compile(
    "<languages[\\s>].*?</languages>", Pattern.DOTALL);

  /** Код языка по описанию конфигурации; ключ учитывает размер и время файла. */
  private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

  private ConfigurationLanguage() {
  }

  /**
   * Код языка текстов для файла объекта.
   *
   * @param objectFile файл описания объекта или самой конфигурации
   * @return код языка; {@link #FALLBACK}, если корень конфигурации не найден
   */
  public static String codeOf(Path objectFile) {
    if (objectFile == null) {
      return FALLBACK;
    }
    Path configuration = configurationFile(objectFile.toAbsolutePath().normalize());
    if (configuration == null) {
      return FALLBACK;
    }
    return CACHE.computeIfAbsent(cacheKey(configuration), key -> read(configuration));
  }

  /** Язык текущей операции: он один на всю операцию, от чтения до проверки записи. */
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  /**
   * Язык текстов текущей операции.
   *
   * @return код языка, заданный {@link #with}, иначе {@link #FALLBACK}
   */
  public static String current() {
    String code = CURRENT.get();
    return code == null || code.isEmpty() ? FALLBACK : code;
  }

  /**
   * Выполняет работу с языком текстов конфигурации, к которой относится файл.
   *
   * <p>Чтение, запись и проверка после записи идут одним языком: иначе точечная
   * запись на конфигурации не на русском отвергается собственной сверкой.
   *
   * @param objectFile файл описания объекта или конфигурации
   * @param work работа
   * @param <T> её результат
   */
  public static <T> T with(Path objectFile, Work<T> work) throws IOException, JAXBException {
    String previous = CURRENT.get();
    CURRENT.set(codeOf(objectFile));
    try {
      return work.run();
    } finally {
      CURRENT.set(previous);
    }
  }

  /** Работа, которой нужен язык конфигурации. */
  public interface Work<T> {
    T run() throws IOException, JAXBException;
  }

  /** Забывает разобранные языки: нужно, когда описание конфигурации правится в том же процессе. */
  public static void forget() {
    CACHE.clear();
  }

  /**
   * Код языка по описанию конфигурации.
   *
   * @param configuration {@code Configuration.xml} выгрузки либо {@code Configuration.mdo} проекта
   * @return код языка либо {@link #FALLBACK}
   */
  static String read(Path configuration) {
    boolean edt = configuration.getFileName().toString().endsWith(".mdo");
    String reference = XmlElementTextReader.read(configuration, edt ? "defaultLanguage" : "DefaultLanguage");
    String name = reference.startsWith(REFERENCE_PREFIX)
      ? reference.substring(REFERENCE_PREFIX.length())
      : reference;
    String code = edt ? edtCode(configuration, name) : designerCode(configuration, name);
    return code.isEmpty() ? FALLBACK : code;
  }

  /** Код языка выгрузки конфигуратора: он лежит в описании самого языка. */
  private static String designerCode(Path configurationXml, String name) {
    Path languages = configurationXml.resolveSibling(CfLayout.LANGUAGES_DIR);
    if (!name.isEmpty()) {
      String code = XmlElementTextReader.read(languages.resolve(name + ".xml"), "LanguageCode");
      if (!code.isEmpty()) {
        return code;
      }
    }
    // Основного языка нет либо у него нет кода: берём первый язык конфигурации
    if (!Files.isDirectory(languages)) {
      return "";
    }
    try (var entries = Files.list(languages)) {
      return entries
        .filter(file -> file.getFileName().toString().endsWith(".xml"))
        .sorted()
        .map(file -> XmlElementTextReader.read(file, "LanguageCode"))
        .filter(code -> !code.isEmpty())
        .findFirst()
        .orElse("");
    } catch (IOException error) {
      return "";
    }
  }

  /** Код языка проекта EDT: языки описаны в самом файле конфигурации. */
  private static String edtCode(Path configurationMdo, String name) {
    String xml;
    try {
      xml = Files.readString(configurationMdo);
    } catch (IOException error) {
      return "";
    }
    String first = "";
    Matcher blocks = EDT_LANGUAGE.matcher(xml);
    while (blocks.find()) {
      String block = blocks.group();
      String code = element(block, "languageCode");
      if (code.isEmpty()) {
        continue;
      }
      if (first.isEmpty()) {
        first = code;
      }
      if (element(block, "name").equals(name)) {
        return code;
      }
    }
    return first;
  }

  /** Текст элемента внутри уже вырезанного блока. */
  private static String element(String xml, String name) {
    return XmlElementTextReader.read(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), name);
  }

  /** Описание конфигурации над файлом объекта, в любой из двух раскладок. */
  private static Path configurationFile(Path objectFile) {
    Path current = objectFile.getParent();
    for (int depth = 0; current != null && depth < ROOT_SEARCH_DEPTH; depth++) {
      Path designer = current.resolve(CfLayout.CONFIGURATION_XML);
      if (Files.isRegularFile(designer)) {
        return designer;
      }
      Path edt = current.resolve("src").resolve("Configuration").resolve("Configuration.mdo");
      if (Files.isRegularFile(edt)) {
        return edt;
      }
      current = current.getParent();
    }
    return null;
  }

  private static String cacheKey(Path configuration) {
    try {
      return configuration + "|" + Files.size(configuration) + "|"
        + Files.getLastModifiedTime(configuration).toMillis();
    } catch (IOException error) {
      return configuration.toString();
    }
  }
}
