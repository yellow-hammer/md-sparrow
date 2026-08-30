/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Правила поддержки поставщика: {@code Ext/ParentConfigurations.bin} выгрузки.
 *
 * <p>Файл текстовый, скобочного формата, ревизия 6: заголовок с глобальным
 * флагом видимости правил, затем блоки поставщиков. Блок несёт свой флаг,
 * реквизиты поставки и записи объектов «режим, флаг, uuid, uuid поставщика».
 * Режим записи: {@code 0} - не редактируется, {@code 1} - на поддержке с
 * возможностью изменения, {@code 2} - снят с поддержки. Глобальный флаг и флаг
 * блока: {@code 0} - правила объектов действуют, {@code 1} - скрыты, вся
 * поставка закрыта от изменения.
 *
 * <p>Запись меняет только однобайтовые цифровые токены: длина файла и всё
 * остальное содержимое сохраняются байт-в-байт, результат перечитывается
 * разбором перед записью.
 */
public final class SupportRules {

  /** Режим записи объекта: изменение запрещено. */
  public static final int MODE_NOT_EDITABLE = 0;
  /** Режим записи объекта: на поддержке с возможностью изменения. */
  public static final int MODE_EDITABLE = 1;
  /** Режим записи объекта: снят с поддержки. */
  public static final int MODE_REMOVED = 2;

  private static final Pattern OBJECT_UUID = Pattern.compile("<[A-Za-z]+ uuid=\"([0-9a-fA-F-]+)\">");
  private static final Pattern UUID_TOKEN = Pattern.compile(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

  /** Кэш разбора: файл большой, а guard дёргает его на каждую мутацию. */
  private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

  private SupportRules() {
  }

  /** Токен скобочного файла: значение и границы в байтах. */
  private record Token(String value, int start, int end) {
  }

  /** Запись объекта: режим и позиция его токена. */
  static final class ObjectEntry {
    final int mode;
    final int modeTokenStart;
    final String localUuid;

    ObjectEntry(int mode, int modeTokenStart, String localUuid) {
      this.mode = mode;
      this.modeTokenStart = modeTokenStart;
      this.localUuid = localUuid;
    }
  }

  /** Блок поставщика: флаг блока и его записи. */
  static final class SupplierBlock {
    String vendor = "";
    String version = "";
    String name = "";
    boolean rulesEnabled;
    int blockTokenStart;
    final List<ObjectEntry> objects = new ArrayList<>();
  }

  /** Свод правил поддержки одной выгрузки. */
  public static final class Rules {
    /** Поставщик из первого блока. */
    public String vendor = "";
    /** Версия поставки. */
    public String version = "";
    /** Имя конфигурации поставщика. */
    public String name = "";
    /** Глобальный флаг: правила объектов действуют, конфигурация открыта для изменения. */
    public boolean rulesEnabled;
    /** Сырой режим записи по uuid объекта: 0 - запрещено, 1 - разрешено, 2 - снят. */
    public Map<String, Integer> modeByUuid = new HashMap<>();
    /** Объекты поставщиков с выключенным флагом блока: для них правила скрыты. */
    public Map<String, Boolean> blockLockedByUuid = new HashMap<>();

    int globalTokenStart = -1;
    List<SupplierBlock> suppliers = new ArrayList<>();

    public boolean isEmpty() {
      return modeByUuid.isEmpty();
    }

    /**
     * Действующее состояние объекта: {@code locked}, {@code editable} либо пусто,
     * когда объект снят с поддержки или записи о нём нет.
     */
    public String effectiveState(String uuid) {
      if (uuid == null) {
        return null;
      }
      Integer mode = modeByUuid.get(uuid.toLowerCase(Locale.ROOT));
      if (mode == null || mode == MODE_REMOVED) {
        return null;
      }
      if (!rulesEnabled || Boolean.TRUE.equals(blockLockedByUuid.get(uuid.toLowerCase(Locale.ROOT)))) {
        return "locked";
      }
      return mode == MODE_NOT_EDITABLE ? "locked" : "editable";
    }

    /** Состояние конфигурации целиком: {@code locked}, {@code editable} либо пусто без правил. */
    public String configurationState() {
      if (isEmpty()) {
        return null;
      }
      return rulesEnabled ? "editable" : "locked";
    }
  }

  private record CacheEntry(long modified, long size, Rules rules) {
  }

  /** Файл правил рядом с Configuration.xml выгрузки. */
  public static Path rulesPath(Path configurationRoot) {
    return configurationRoot.resolve("Ext").resolve("ParentConfigurations.bin");
  }

  /**
   * Читает правила поддержки выгрузки; пустые правила, когда файла нет.
   *
   * @param configurationRoot каталог с Configuration.xml
   */
  public static Rules read(Path configurationRoot) throws IOException {
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      return new Rules();
    }
    String key = file.toAbsolutePath().normalize().toString();
    long modified = Files.getLastModifiedTime(file).toMillis();
    long size = Files.size(file);
    CacheEntry cached = CACHE.get(key);
    if (cached != null && cached.modified == modified && cached.size == size) {
      return cached.rules;
    }
    Rules rules = parse(Files.readAllBytes(file));
    CACHE.put(key, new CacheEntry(modified, size, rules));
    return rules;
  }

  static Rules parse(byte[] bytes) {
    Rules rules = new Rules();
    List<Token> tokens = tokenize(bytes);
    if (tokens.size() < 3 || !"6".equals(tokens.get(0).value())) {
      return rules;
    }
    Token global = tokens.get(1);
    rules.rulesEnabled = "0".equals(global.value());
    rules.globalTokenStart = global.start();
    int supplierCount = parseIntSafe(tokens.get(2).value());
    int cursor = 3;
    for (int s = 0; s < supplierCount && cursor + 7 <= tokens.size(); s++) {
      SupplierBlock block = new SupplierBlock();
      cursor++; // uuid конфигурации поставщика
      Token blockToken = tokens.get(cursor++);
      block.rulesEnabled = "0".equals(blockToken.value());
      block.blockTokenStart = blockToken.start();
      cursor++; // uuid родительской конфигурации
      block.version = unquote(tokens.get(cursor++).value());
      block.vendor = unquote(tokens.get(cursor++).value());
      block.name = unquote(tokens.get(cursor++).value());
      int objectCount = parseIntSafe(tokens.get(cursor++).value());
      for (int o = 0; o < objectCount && cursor + 4 <= tokens.size(); o++) {
        Token modeToken = tokens.get(cursor++);
        cursor++; // служебный флаг записи
        Token localUuid = tokens.get(cursor++);
        cursor++; // uuid объекта поставщика
        if (!UUID_TOKEN.matcher(localUuid.value()).matches()) {
          continue;
        }
        int mode = parseIntSafe(modeToken.value());
        String uuid = localUuid.value().toLowerCase(Locale.ROOT);
        block.objects.add(new ObjectEntry(mode, modeToken.start(), uuid));
        rules.modeByUuid.put(uuid, mode);
        if (!block.rulesEnabled) {
          rules.blockLockedByUuid.put(uuid, true);
        }
      }
      cursor += 2; // хвост блока
      if (rules.vendor.isEmpty()) {
        rules.vendor = block.vendor;
        rules.version = block.version;
        rules.name = block.name;
      }
      rules.suppliers.add(block);
    }
    return rules;
  }

  /** Токены между внешними скобками; кавычки с удвоением учитываются, границы — в байтах. */
  private static List<Token> tokenize(byte[] bytes) {
    List<Token> tokens = new ArrayList<>();
    int cursor = bytes.length >= 3
      && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
    if (cursor >= bytes.length || bytes[cursor] != '{') {
      return tokens;
    }
    cursor++;
    int start = cursor;
    boolean quoted = false;
    for (; cursor < bytes.length; cursor++) {
      byte b = bytes[cursor];
      if (b == '"') {
        if (quoted && cursor + 1 < bytes.length && bytes[cursor + 1] == '"') {
          cursor++;
          continue;
        }
        quoted = !quoted;
        continue;
      }
      if (!quoted && (b == ',' || b == '}')) {
        int from = start;
        int to = cursor;
        while (from < to && isWhitespace(bytes[from])) {
          from++;
        }
        while (to > from && isWhitespace(bytes[to - 1])) {
          to--;
        }
        tokens.add(new Token(new String(bytes, from, to - from, StandardCharsets.UTF_8), from, to));
        if (b == '}') {
          return tokens;
        }
        start = cursor + 1;
      }
    }
    return tokens;
  }

  /**
   * Ставит объекту режим поддержки: 0 - запретить, 1 - разрешить, 2 - снять.
   *
   * <p>Меняется один байт токена режима; правила при выключенном глобальном
   * флаге скрыты, и правка отклоняется с подсказкой включить возможность
   * изменения конфигурации.
   */
  public static void setObjectMode(Path configurationRoot, String uuid, int mode) throws IOException {
    if (mode != MODE_NOT_EDITABLE && mode != MODE_EDITABLE && mode != MODE_REMOVED) {
      throw new IllegalArgumentException("Неизвестный режим поддержки: " + mode);
    }
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("У выгрузки нет файла правил поддержки.");
    }
    byte[] bytes = Files.readAllBytes(file);
    Rules rules = parse(bytes);
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("Файл правил поддержки не разобран.");
    }
    if (!rules.rulesEnabled) {
      throw new IllegalStateException(
        "Конфигурация на полной поддержке: сначала включите возможность изменения конфигурации.");
    }
    String needle = uuid.toLowerCase(Locale.ROOT);
    boolean patched = false;
    for (SupplierBlock block : rules.suppliers) {
      for (ObjectEntry entry : block.objects) {
        if (entry.localUuid.equals(needle)) {
          patchDigit(bytes, entry.modeTokenStart, mode);
          patched = true;
        }
      }
    }
    if (!patched) {
      throw new IllegalArgumentException("В правилах поддержки нет записи об объекте " + uuid + ".");
    }
    verifyAndWrite(file, bytes);
  }

  /**
   * Включает возможность изменения конфигурации: глобальный флаг и флаги блоков
   * открываются, каждой записи ставится правило по умолчанию из диалога
   * конфигуратора - «не редактируется» либо «редактируется с сохранением
   * поддержки». Дальше режим меняется по объекту.
   */
  public static void enableRules(Path configurationRoot, int defaultMode) throws IOException {
    if (defaultMode != MODE_NOT_EDITABLE && defaultMode != MODE_EDITABLE) {
      throw new IllegalArgumentException("Правило по умолчанию: 0 не редактируется либо 1 с сохранением поддержки.");
    }
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("У выгрузки нет файла правил поддержки.");
    }
    byte[] bytes = Files.readAllBytes(file);
    Rules rules = parse(bytes);
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("Файл правил поддержки не разобран.");
    }
    if (rules.rulesEnabled) {
      throw new IllegalStateException("Возможность изменения уже включена.");
    }
    patchDigit(bytes, rules.globalTokenStart, 0);
    for (SupplierBlock block : rules.suppliers) {
      patchDigit(bytes, block.blockTokenStart, 0);
      for (ObjectEntry entry : block.objects) {
        patchDigit(bytes, entry.modeTokenStart, defaultMode);
      }
    }
    verifyAndWrite(file, bytes);
  }

  /**
   * Снимает конфигурацию с поддержки: файл поставки удаляется, правил больше
   * нет, вся выгрузка редактируется свободно. Обратной дороги без поставщика
   * нет, поэтому вызывающая сторона спрашивает подтверждение сама.
   */
  public static void removeSupport(Path configurationRoot) throws IOException {
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Конфигурация не на поддержке: файла поставки нет.");
    }
    Files.delete(file);
    CACHE.remove(file.toAbsolutePath().normalize().toString());
  }

  private static void patchDigit(byte[] bytes, int at, int digit) {
    if (at < 0 || at >= bytes.length || bytes[at] < '0' || bytes[at] > '2') {
      throw new IllegalStateException("Токен правил поддержки не совпал с ожидаемым: смещение " + at + ".");
    }
    bytes[at] = (byte) ('0' + digit);
  }

  private static void verifyAndWrite(Path file, byte[] bytes) throws IOException {
    Rules verified = parse(bytes);
    if (verified.isEmpty()) {
      throw new IllegalStateException("Файл правил поддержки после правки не разобран, запись отменена.");
    }
    Files.write(file, bytes);
    CACHE.remove(file.toAbsolutePath().normalize().toString());
  }

  /**
   * Проверяет, что объект можно менять; иначе бросает с внятным текстом.
   *
   * <p>Корень выгрузки ищется от файла объекта вверх; объект, снятый с
   * поддержки, и объект без записи изменению не мешают.
   */
  public static void ensureEditable(Path objectXml) throws IOException {
    Path normalized = objectXml.toAbsolutePath().normalize();
    Path root = findConfigurationRoot(normalized);
    if (root == null) {
      return;
    }
    Rules rules = read(root);
    if (rules.isEmpty()) {
      return;
    }
    String uuid = objectUuid(normalized);
    if (uuid == null) {
      return;
    }
    if ("locked".equals(rules.effectiveState(uuid))) {
      throw new IllegalStateException(
        "Объект на поддержке поставщика «" + rules.vendor
          + "» без возможности изменения. Включите возможность изменения"
          + " или снимите объект с поддержки.");
    }
  }

  /**
   * Действующее состояние конкретного объекта: {@code locked}, {@code editable}
   * либо пусто, когда объект не на поддержке или правил в выгрузке нет.
   */
  public static String objectState(Path objectXml) throws IOException {
    Rules rules = rulesFor(objectXml);
    if (rules == null || rules.isEmpty()) {
      return null;
    }
    return rules.effectiveState(objectUuid(objectXml.toAbsolutePath().normalize()));
  }

  /** Правила выгрузки, которой принадлежит объект; пусто вне выгрузки. */
  public static Rules rulesFor(Path objectXml) throws IOException {
    Path root = findConfigurationRoot(objectXml.toAbsolutePath().normalize());
    return root == null ? null : read(root);
  }

  private static String objectUuid(Path objectXml) throws IOException {
    try (var lines = Files.lines(objectXml, StandardCharsets.UTF_8)) {
      return lines
        .limit(8)
        .map(OBJECT_UUID::matcher)
        .filter(Matcher::find)
        .map(matcher -> matcher.group(1))
        .findFirst()
        .orElse(null);
    }
  }

  private static Path findConfigurationRoot(Path objectXml) {
    Path current = objectXml.getParent();
    for (int depth = 0; current != null && depth < 6; depth++) {
      if (Files.isRegularFile(current.resolve("Configuration.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private static boolean isWhitespace(byte b) {
    return b == ' ' || b == '\t' || b == '\r' || b == '\n';
  }

  private static int parseIntSafe(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1).replace("\"\"", "\"");
    }
    return value;
  }
}
