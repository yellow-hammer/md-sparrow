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

  /**
   * Отказывать ли в правке того, что запрещено правилами поставки.
   *
   * <p>Выключается на весь процесс вызывающей программой, когда она работает с
   * выгрузкой так, будто поставки нет. Чтения правил это не касается.
   */
  private static volatile boolean enforced = true;

  /** Включает или выключает работу с правилами поддержки на весь процесс. */
  public static void setEnforced(boolean value) {
    enforced = value;
  }

  /** Правила поддержки учитываются: иначе выгрузка читается и правится как без поставки. */
  public static boolean isEnforced() {
    return enforced;
  }

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
    /** Глобальный флаг файла правил: правила объектов действуют. */
    public boolean rulesEnabled;
    /**
     * Рядом с правилами лежит файл поставки.
     *
     * <p>Его создаёт конфигуратор, когда включают возможность изменения, поэтому
     * без него правила ещё не включены, каким бы ни был глобальный флаг. Разбор
     * одних байтов каталога не видит и считает поставку на месте.
     */
    public boolean vendorPayloadPresent = true;
    /** Хотя бы у одного блока поставщика правила открыты. */
    public boolean anyBlockOpen;
    /** Сырой режим записи по uuid объекта: 0 - запрещено, 1 - разрешено, 2 - снят. */
    public Map<String, Integer> modeByUuid = new HashMap<>();
    /** Объекты поставщиков с выключенным флагом блока: для них правила скрыты. */
    public Map<String, Boolean> blockLockedByUuid = new HashMap<>();
    /**
     * Отпечаток прочитанного файла правил: с ним правка отклоняется, если файл
     * успел измениться конфигуратором или соседней сессией.
     */
    public String generationId = "";

    int globalTokenStart = -1;
    List<SupplierBlock> suppliers = new ArrayList<>();

    public boolean isEmpty() {
      return modeByUuid.isEmpty();
    }

    /**
     * Возможность изменения включена: правила действуют и поставка на месте.
     *
     * <p>Правила действуют, когда открыт и глобальный флаг, и флаг хотя бы
     * одного блока поставщика: при закрытом блоке его объекты заблокированы
     * целиком. Включает это только конфигуратор, он же кладёт файл поставки.
     */
    public boolean editingEnabled() {
      return rulesEnabled && anyBlockOpen && vendorPayloadPresent;
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
      if (!editingEnabled() || Boolean.TRUE.equals(blockLockedByUuid.get(uuid.toLowerCase(Locale.ROOT)))) {
        return "locked";
      }
      return mode == MODE_NOT_EDITABLE ? "locked" : "editable";
    }

    /** Состояние конфигурации целиком: {@code locked}, {@code editable} либо пусто без правил. */
    public String configurationState() {
      if (isEmpty()) {
        return null;
      }
      return editingEnabled() ? "editable" : "locked";
    }
  }

  private record CacheEntry(long modified, long size, Rules rules) {
  }

  /** Файл правил рядом с Configuration.xml выгрузки. */
  public static Path rulesPath(Path configurationRoot) {
    return configurationRoot.resolve("Ext").resolve("ParentConfigurations.bin");
  }

  /**
   * Каталог поставок поставщика рядом с правилами: платформа кладёт туда
   * {@code <Имя поставки>.cf} каждой поставки.
   *
   * <p>Проверено загрузкой в базу платформой 8.3.27: пока правила действуют,
   * выгрузка без файла поставки не загружается («Каталог не обнаружен»), а на
   * полной поддержке загружается и без него.
   */
  public static Path vendorPayloadDir(Path configurationRoot) {
    return configurationRoot.resolve("Ext").resolve("ParentConfigurations");
  }

  /** Отпечаток содержимого файла правил: им сверяется, что правка идёт поверх прочитанного. */
  public static String generationOf(byte[] bytes) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder out = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return out.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 недоступен", e);
    }
  }

  /**
   * Сверяет отпечаток прочитанных правил с текущим файлом.
   *
   * @param expectedGeneration отпечаток из чтения; пусто - сверка не выполняется
   */
  private static void ensureSameGeneration(byte[] bytes, String expectedGeneration) {
    if (expectedGeneration == null || expectedGeneration.isBlank()) {
      return;
    }
    if (!generationOf(bytes).equals(expectedGeneration.trim())) {
      throw new IllegalStateException(
        "Правила поддержки изменились после чтения: обновите дерево метаданных и повторите.");
    }
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
    byte[] bytes = Files.readAllBytes(file);
    Rules rules = parse(bytes);
    rules.generationId = generationOf(bytes);
    rules.vendorPayloadPresent = vendorPayloadFile(configurationRoot) != null;
    CACHE.put(key, new CacheEntry(modified, size, rules));
    return rules;
  }

  /** Файл поставки рядом с правилами; пусто, когда его нет. */
  public static Path vendorPayloadFile(Path configurationRoot) throws IOException {
    Path dir = vendorPayloadDir(configurationRoot);
    if (!Files.isDirectory(dir)) {
      return null;
    }
    try (var entries = Files.list(dir)) {
      return entries
        .filter(path -> Files.isRegularFile(path)
          && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cf"))
        .findFirst()
        .orElse(null);
    }
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
      rules.anyBlockOpen = rules.anyBlockOpen || block.rulesEnabled;
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
  public static void setObjectMode(Path configurationRoot, String uuid, int mode, String expectedGeneration)
    throws IOException {
    setObjectModes(configurationRoot, List.of(uuid), mode, expectedGeneration);
  }

  /** Ставит один режим сразу нескольким субъектам: объект вместе с его формами и макетами. */
  public static void setObjectModes(
    Path configurationRoot,
    List<String> uuids,
    int mode,
    String expectedGeneration
  ) throws IOException {
    if (mode != MODE_NOT_EDITABLE && mode != MODE_EDITABLE && mode != MODE_REMOVED) {
      throw new IllegalArgumentException("Неизвестный режим поддержки: " + mode);
    }
    if (uuids == null || uuids.isEmpty()) {
      throw new IllegalArgumentException("Не указан объект для смены режима поддержки.");
    }
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("У выгрузки нет файла правил поддержки.");
    }
    byte[] bytes = Files.readAllBytes(file);
    ensureSameGeneration(bytes, expectedGeneration);
    Rules rules = parse(bytes);
    rules.vendorPayloadPresent = vendorPayloadFile(configurationRoot) != null;
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("Файл правил поддержки не разобран.");
    }
    if (!rules.editingEnabled()) {
      throw new IllegalStateException(
        "Возможность изменения конфигурации не включена: включите её в конфигураторе и выгрузите"
          + " конфигурацию заново. Тогда рядом с правилами появится файл поставки, и правила"
          + " станут доступны для правки.");
    }
    java.util.Set<String> needles = new java.util.HashSet<>();
    for (String uuid : uuids) {
      if (uuid != null && !uuid.isBlank()) {
        needles.add(uuid.trim().toLowerCase(Locale.ROOT));
      }
    }
    int patched = 0;
    for (SupplierBlock block : rules.suppliers) {
      for (ObjectEntry entry : block.objects) {
        if (needles.contains(entry.localUuid)) {
          patchDigit(bytes, entry.modeTokenStart, mode);
          patched++;
        }
      }
    }
    if (patched == 0) {
      throw new IllegalArgumentException(
        "В правилах поддержки нет записей о выбранных объектах: " + String.join(", ", needles));
    }
    verifyAndWrite(file, bytes);
  }

  /**
   * Ставит режим поддержки файлу и, по требованию, его подчинённым.
   *
   * <p>Своё правило есть у объекта, формы, макета и команды, поэтому режим
   * объекта не решает за формы: конфигуратор для этого предлагает применить
   * правило к подчинённым.
   *
   * @param subjectXml файл объекта или его формы либо макета
   * @param includeChildren применить тот же режим к подчинённым субъектам
   */
  public static void setModeForFile(
    Path subjectXml,
    int mode,
    boolean includeChildren,
    String expectedGeneration
  ) throws IOException {
    Path subject = supportSubjectXml(subjectXml.toAbsolutePath().normalize());
    Path root = findConfigurationRoot(subject);
    if (root == null) {
      throw new IllegalArgumentException("Файл вне выгрузки конфигурации: " + subjectXml);
    }
    List<String> uuids = new ArrayList<>();
    String own = objectUuid(subject);
    if (own == null) {
      throw new IllegalArgumentException("В шапке файла не найден uuid: " + subject);
    }
    uuids.add(own);
    if (includeChildren) {
      // У конфигурации подчинённые - вся выгрузка, у объекта - его формы,
      // макеты и элементы
      uuids.addAll(
        subject.equals(root.resolve("Configuration.xml"))
          ? read(root).modeByUuid.keySet()
          : childSubjectUuids(subject));
    }
    setObjectModes(root, uuids, mode, expectedGeneration);
  }

  /**
   * Состояния объекта и его подчинённых: путь файла относительно каталога
   * выгрузки -> {@code locked}, {@code editable} либо пусто.
   */
  public static Map<String, String> statesForObject(Path objectXml) throws IOException {
    Path subject = supportSubjectXml(objectXml.toAbsolutePath().normalize());
    Path root = findConfigurationRoot(subject);
    Map<String, String> out = new java.util.LinkedHashMap<>();
    if (root == null) {
      return out;
    }
    Rules rules = read(root);
    if (rules.isEmpty()) {
      return out;
    }
    out.put(root.relativize(subject).toString().replace('\\', '/'), rules.effectiveState(objectUuid(subject)));
    for (Path child : childSubjectFiles(subject)) {
      out.put(
        root.relativize(child).toString().replace('\\', '/'),
        rules.effectiveState(objectUuid(child)));
    }
    for (Map.Entry<String, String> element : elementSubjects(subject).entrySet()) {
      out.put(element.getKey(), rules.effectiveState(element.getValue()));
    }
    return out;
  }

  /** Файлы подчинённых субъектов: формы, макеты и команды внутри каталога объекта. */
  private static List<Path> childSubjectFiles(Path subjectXml) throws IOException {
    Path dir = subjectXml.resolveSibling(
      subjectXml.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", ""));
    List<Path> out = new ArrayList<>();
    if (!Files.isDirectory(dir)) {
      return out;
    }
    for (String section : List.of("Forms", "Templates", "Commands", "Recalculations")) {
      Path sectionDir = dir.resolve(section);
      if (!Files.isDirectory(sectionDir)) {
        continue;
      }
      try (var entries = Files.list(sectionDir)) {
        entries
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
          .sorted()
          .forEach(out::add);
      }
    }
    return out;
  }

  private static List<String> childSubjectUuids(Path subjectXml) throws IOException {
    List<String> out = new ArrayList<>(elementSubjects(subjectXml).values());
    for (Path child : childSubjectFiles(subjectXml)) {
      String uuid = objectUuid(child);
      if (uuid != null) {
        out.add(uuid);
      }
      out.addAll(elementSubjects(child).values());
    }
    return out;
  }

  /**
   * Свои правила элементов внутри файла объекта: реквизитов, табличных частей,
   * измерений, ресурсов, значений перечисления, команд.
   *
   * <p>Правило поставки заведено на каждый uuid выгрузки, а не только на файл,
   * поэтому режим объекта не решает за его реквизиты.
   *
   * @return ключ вида {@code element:<операция вида>:<путь имён>} -> uuid
   */
  public static Map<String, String> elementSubjects(Path objectXml) throws IOException {
    Map<String, String> out = new java.util.LinkedHashMap<>();
    if (!Files.isRegularFile(objectXml)) {
      return out;
    }
    javax.xml.stream.XMLInputFactory factory = javax.xml.stream.XMLInputFactory.newInstance();
    factory.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false);
    try (var stream = Files.newInputStream(objectXml)) {
      javax.xml.stream.XMLStreamReader reader = factory.createXMLStreamReader(stream);
      List<ElementFrame> stack = new ArrayList<>();
      int depth = 0;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
          depth++;
          String uuid = reader.getAttributeValue(null, "uuid");
          if (uuid != null) {
            stack.add(new ElementFrame(reader.getLocalName(), uuid, depth));
          } else if (isNameOfCurrentElement(reader, stack, depth)) {
            ElementFrame frame = stack.get(stack.size() - 1);
            if (frame.name == null) {
              frame.name = reader.getElementText().trim();
              depth--;
              putElement(out, stack);
            }
          }
        } else if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
          if (!stack.isEmpty() && stack.get(stack.size() - 1).depth == depth) {
            stack.remove(stack.size() - 1);
          }
          depth--;
        }
      }
      reader.close();
    } catch (javax.xml.stream.XMLStreamException e) {
      return out;
    }
    return out;
  }

  /** Имя элемента лежит в {@code Properties/Name} внутри самого элемента. */
  private static boolean isNameOfCurrentElement(
    javax.xml.stream.XMLStreamReader reader,
    List<ElementFrame> stack,
    int depth
  ) {
    return !stack.isEmpty()
      && "Name".equals(reader.getLocalName())
      && depth == stack.get(stack.size() - 1).depth + 2;
  }

  private static void putElement(Map<String, String> out, List<ElementFrame> stack) {
    if (stack.size() < 2) {
      return;
    }
    String op = ELEMENT_OP_BY_KIND.get(stack.get(stack.size() - 1).kind);
    if (op == null) {
      return;
    }
    // Реквизит табличной части правится своими операциями, ключ идёт от них
    if ("cf-md-attribute".equals(op) && stack.size() > 2) {
      op = "cf-md-tabular-attribute";
    }
    StringBuilder path = new StringBuilder();
    for (int i = 1; i < stack.size(); i++) {
      String name = stack.get(i).name;
      if (name == null) {
        return;
      }
      if (path.length() > 0) {
        path.append('/');
      }
      path.append(name);
    }
    out.put("element:" + op + ":" + path, stack.get(stack.size() - 1).uuid);
  }

  /** Элемент с собственным uuid: вид, uuid, глубина в разборе и имя. */
  private static final class ElementFrame {
    final String kind;
    final String uuid;
    final int depth;
    String name;

    ElementFrame(String kind, String uuid, int depth) {
      this.kind = kind;
      this.uuid = uuid;
      this.depth = depth;
    }
  }

  /**
   * Вид элемента выгрузки -> общая часть операций его правки.
   *
   * <p>Ключом наружу служит операция, а не имя элемента формата: потребителю
   * не нужно знать разметку, он и так зовёт эти операции.
   */
  private static final Map<String, String> ELEMENT_OP_BY_KIND = Map.of(
    "Attribute", "cf-md-attribute",
    "TabularSection", "cf-md-tabular-section",
    "Dimension", "cf-md-dimension",
    "Resource", "cf-md-resource",
    "EnumValue", "cf-md-enum-value",
    "Command", "cf-md-command",
    "AccountingFlag", "cf-md-accounting-flag",
    "ExtDimensionAccountingFlag", "cf-md-ext-dimension-accounting-flag");

  /**
   * Ставит режим поддержки элементу внутри файла объекта и всему, что внутри
   * него: у табличной части - её реквизитам.
   *
   * @param elementKey ключ из {@link #elementSubjects(Path)}
   */
  public static void setModeForElement(
    Path objectXml,
    String elementKey,
    int mode,
    String expectedGeneration
  ) throws IOException {
    Path subject = objectXml.toAbsolutePath().normalize();
    Path root = findConfigurationRoot(subject);
    if (root == null) {
      throw new IllegalArgumentException("Файл вне выгрузки конфигурации: " + objectXml);
    }
    Map<String, String> elements = elementSubjects(subject);
    String uuid = elements.get(elementKey);
    if (uuid == null) {
      throw new IllegalArgumentException("В файле объекта нет элемента: " + elementKey);
    }
    List<String> uuids = new ArrayList<>();
    uuids.add(uuid);
    String nested = elementKey.substring(elementKey.lastIndexOf(':') + 1) + "/";
    for (Map.Entry<String, String> entry : elements.entrySet()) {
      String path = entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1);
      if (path.startsWith(nested)) {
        uuids.add(entry.getValue());
      }
    }
    setObjectModes(root, uuids, mode, expectedGeneration);
  }

  /**
   * Снимает конфигурацию с поддержки: файл поставки удаляется, правил больше
   * нет, вся выгрузка редактируется свободно. Обратной дороги без поставщика
   * нет, поэтому вызывающая сторона спрашивает подтверждение сама.
   */
  public static void removeSupport(Path configurationRoot, String expectedGeneration) throws IOException {
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Конфигурация не на поддержке: файла поставки нет.");
    }
    ensureSameGeneration(Files.readAllBytes(file), expectedGeneration);
    Files.delete(file);
    // Поставка нужна только правилам: без них платформе обновляться не от чего
    Path payload = vendorPayloadDir(configurationRoot);
    if (Files.isDirectory(payload)) {
      try (var entries = Files.walk(payload)) {
        for (Path path : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(path);
        }
      }
    }
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
    if (!enforced) {
      return;
    }
    Path normalized = supportSubjectXml(objectXml.toAbsolutePath().normalize());
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
   * Проверяет, что элемент объекта можно менять; иначе бросает с внятным текстом.
   *
   * <p>Разрешение на объект не открывает его элементы: у каждого своё правило.
   * Элемент без записи в правилах правку не ограничивает.
   *
   * @param elementKey ключ из {@link #elementSubjects(Path)}
   */
  public static void ensureElementEditable(Path objectXml, String elementKey) throws IOException {
    if (!enforced) {
      return;
    }
    Path subject = objectXml.toAbsolutePath().normalize();
    Path root = findConfigurationRoot(subject);
    if (root == null) {
      return;
    }
    Rules rules = read(root);
    if (rules.isEmpty()) {
      return;
    }
    String uuid = elementSubjects(subject).get(elementKey);
    if (uuid == null || !"locked".equals(rules.effectiveState(uuid))) {
      return;
    }
    throw new IllegalStateException(
      "Элемент на поддержке поставщика «" + rules.vendor
        + "» без возможности изменения. Смените режим поддержки элемента"
        + " или снимите объект с поддержки.");
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
    return rules.effectiveState(objectUuid(supportSubjectXml(objectXml.toAbsolutePath().normalize())));
  }

  /**
   * Файл, чьё правило поддержки решает судьбу переданного файла.
   *
   * <p>Своё правило есть у всего, что несёт uuid в шапке: у объекта, формы,
   * макета, команды. Содержимое формы, модуль и прочие файлы без uuid идут к
   * ближайшему такому файлу вверх по каталогам.
   *
   * @return файл субъекта правила либо исходный путь, если субъект не найден
   */
  public static Path supportSubjectXml(Path anyFileOfObject) {
    if (hasOwnUuid(anyFileOfObject)) {
      return anyFileOfObject;
    }
    Path current = anyFileOfObject.getParent();
    for (int depth = 0; current != null && current.getFileName() != null && depth < 8; depth++) {
      Path candidate = current.resolveSibling(current.getFileName().toString() + ".xml");
      if (hasOwnUuid(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return anyFileOfObject;
  }

  private static boolean hasOwnUuid(Path xml) {
    if (!Files.isRegularFile(xml) || !xml.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) {
      return false;
    }
    try {
      return objectUuid(xml) != null;
    } catch (IOException e) {
      return false;
    }
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
