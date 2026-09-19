/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtObjectReader;
import io.github.yellowhammer.edt.EdtSupportRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Права всех ролей на один объект метаданных: чтение одним проходом по ролям и правка.
 *
 * <p>Файл прав хранит отличия от умолчания роли: при включённом «Устанавливать
 * права для новых объектов» записаны снятые права, при выключенном - выданные.
 * Право с ограничениями доступа записано при любом значении, остальные записанные
 * значения, совпадающие с умолчанием, платформа при выгрузке отбрасывает.
 */
public final class ObjectRights {

  private static final String ROLES = "Roles";
  private static final String EDT_RIGHTS = "Rights.rights";

  private ObjectRights() {
  }

  /** Права всех ролей на объект. */
  public static final class Dto {
    /** Имя объекта в файлах прав: {@code Catalog.Товары}. */
    public String object;
    public String kind;
    /** Права вида в порядке платформы. */
    public List<String> rights = new ArrayList<>();
    /** Право -> права, которые платформа выдаёт вместе с ним. */
    public Map<String, List<String>> requires = new LinkedHashMap<>();
    /** У вида объекта есть права в ролях. */
    public boolean editable;
    public List<RoleDto> roles = new ArrayList<>();
  }

  /** Права одной роли на объект. */
  public static final class RoleDto {
    public String name;
    public String synonym;
    public boolean setForNewObjects;
    /** Действующие права: записанные и взятые из умолчания роли. */
    public List<String> granted = new ArrayList<>();
    /** Ограничения доступа по праву: поля и текст условия. */
    public Map<String, List<Map<String, Object>>> restrictions = new LinkedHashMap<>();
    /** Записанные права подчинённых: реквизитов, команд, табличных частей. */
    public List<ChildDto> children = new ArrayList<>();
    /** Роль закрыта правилами поставки. */
    public String readonlyReason;
  }

  /** Записанные права подчинённого объекта. */
  public static final class ChildDto {
    /** Путь от объекта: {@code Attribute.Цена}. */
    public String name;
    public Map<String, Boolean> rights = new LinkedHashMap<>();
  }

  /** Правка: роль, право, выдать или снять. */
  public static final class Edit {
    public String role;
    public String right;
    public boolean value;
  }

  /** Объект в файлах прав и корень его проекта. */
  private record Target(Path sourceRoot, boolean edt, String object, String kind) {
  }

  public static Dto read(Path objectFile) throws IOException {
    Target target = target(objectFile);
    Dto out = new Dto();
    out.object = target.object();
    out.kind = target.kind();
    RoleRightsCatalog.Kind kind = RoleRightsCatalog.kind(target.kind());
    if (kind != null) {
      out.rights = kind.rights();
      out.requires = kind.requires();
    }
    out.editable = kind != null;
    RoleLocks locks = new RoleLocks(target);
    for (Path roleFile : roleFiles(target)) {
      out.roles.add(roleRights(target, kind, roleFile, locks));
    }
    return out;
  }

  private static RoleDto roleRights(Target target, RoleRightsCatalog.Kind kind, Path roleFile, RoleLocks locks)
      throws IOException {
    RoleDto role = new RoleDto();
    role.name = stem(roleFile);
    role.synonym = synonym(roleFile, target.edt());
    Path file = rightsFile(roleFile, target.edt());
    String text = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    role.setForNewObjects = RightsText.setForNewObjects(text);
    Map<String, RightsText.Right> own = new LinkedHashMap<>();
    for (RightsText.Block block : RightsText.blocksOf(text, target.object())) {
      if (block.name().equals(target.object())) {
        for (RightsText.Right right : block.rights()) {
          own.put(right.name(), right);
        }
        continue;
      }
      ChildDto child = new ChildDto();
      child.name = block.name().substring(target.object().length() + 1);
      for (RightsText.Right right : block.rights()) {
        child.rights.put(right.name(), right.value());
      }
      role.children.add(child);
    }
    List<String> rights = kind == null ? new ArrayList<>(own.keySet()) : kind.rights();
    for (String right : rights) {
      RightsText.Right stored = own.get(right);
      if (stored == null ? role.setForNewObjects : stored.value()) {
        role.granted.add(right);
      }
      if (stored != null && stored.restricted()) {
        role.restrictions.put(right, RightsText.restrictions(stored.tail()));
      }
    }
    role.readonlyReason = locks.of(roleFile);
    return role;
  }

  /**
   * Правит права ролей на объект.
   *
   * <p>Выданное право тянет за собой те, без которых не действует, снятое снимает
   * зависящие от него. Блок нового объекта встаёт на место по идентификатору объекта.
   * Файлы ролей пишутся, только когда правки всех ролей прошли проверку.
   */
  public static void apply(Path objectFile, List<Edit> edits) throws IOException {
    Target target = target(objectFile);
    RoleRightsCatalog.Kind kind = RoleRightsCatalog.kind(target.kind());
    if (kind == null) {
      throw new IllegalArgumentException("У объекта вида " + target.kind() + " нет прав в ролях.");
    }
    Map<String, List<Edit>> byRole = new LinkedHashMap<>();
    for (Edit edit : edits == null ? List.<Edit>of() : edits) {
      if (edit == null || edit.role == null || edit.right == null) {
        continue;
      }
      if (!kind.rights().contains(edit.right)) {
        throw new IllegalArgumentException("У объекта вида " + target.kind() + " нет права " + edit.right + ".");
      }
      byRole.computeIfAbsent(edit.role, key -> new ArrayList<>()).add(edit);
    }
    Path rolesDir = target.sourceRoot().resolve(ROLES);
    UuidOrder order = new UuidOrder(target.sourceRoot(), target.edt());
    RoleLocks locks = new RoleLocks(target);
    Map<Path, String> updates = new LinkedHashMap<>();
    for (Map.Entry<String, List<Edit>> entry : byRole.entrySet()) {
      String role = entry.getKey();
      Path roleFile = target.edt() ? rolesDir.resolve(role).resolve(role + ".mdo") : rolesDir.resolve(role + ".xml");
      if (!Files.isRegularFile(roleFile)) {
        throw new IllegalArgumentException("Нет роли " + role + ".");
      }
      String lock = locks.of(roleFile);
      if (lock != null) {
        throw new IllegalStateException(lock);
      }
      Path file = rightsFile(roleFile, target.edt());
      if (!Files.isRegularFile(file)) {
        throw new IllegalArgumentException("У роли " + role + " нет файла прав: " + file);
      }
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String updated = applyToRole(text, target, kind, entry.getValue(), order);
      if (!updated.equals(text)) {
        updates.put(file, updated);
      }
    }
    for (Map.Entry<Path, String> update : updates.entrySet()) {
      Files.writeString(update.getKey(), update.getValue(), StandardCharsets.UTF_8);
    }
  }

  private static String applyToRole(
      String text, Target target, RoleRightsCatalog.Kind kind, List<Edit> edits, UuidOrder order) {
    boolean byDefault = RightsText.setForNewObjects(text);
    RightsText.Block block = null;
    for (RightsText.Block candidate : RightsText.blocksOf(text, target.object())) {
      if (candidate.name().equals(target.object())) {
        block = candidate;
        break;
      }
    }
    Map<String, RightsText.Right> stored = new LinkedHashMap<>();
    if (block != null) {
      for (RightsText.Right right : block.rights()) {
        stored.put(right.name(), right);
      }
    }
    Map<String, Boolean> effective = new HashMap<>();
    for (String right : kind.rights()) {
      RightsText.Right known = stored.get(right);
      effective.put(right, known == null ? byDefault : known.value());
    }
    for (Edit edit : edits) {
      var linked = edit.value
        ? RoleRightsCatalog.withRequired(kind, edit.right)
        : RoleRightsCatalog.withDependent(kind, edit.right);
      for (String right : linked) {
        effective.put(right, edit.value);
      }
    }
    String eol = text.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder rights = new StringBuilder();
    for (String right : kind.rights()) {
      boolean value = effective.get(right);
      RightsText.Right known = stored.get(right);
      boolean restricted = known != null && known.restricted();
      if (value == byDefault && !restricted) {
        continue;
      }
      appendRight(rights, right, value, restricted ? known.tail() : eol + "\t\t", eol);
    }
    // Права вне набора вида остаются как были
    for (RightsText.Right right : stored.values()) {
      if (!kind.rights().contains(right.name())) {
        appendRight(rights, right.name(), right.value(), right.tail(), eol);
      }
    }
    String replacement = rights.length() == 0 ? "" : "\t<object>" + eol
      + "\t\t<name>" + target.object() + "</name>" + eol
      + rights
      + "\t</object>" + eol;
    if (block != null) {
      return text.substring(0, block.start()) + replacement + text.substring(block.end());
    }
    if (replacement.isEmpty()) {
      return text;
    }
    int at = order.insertionPoint(text, target.object());
    return text.substring(0, at) + replacement + text.substring(at);
  }

  private static void appendRight(StringBuilder out, String name, boolean value, String tail, String eol) {
    out.append("\t\t<right>").append(eol)
      .append("\t\t\t<name>").append(name).append("</name>").append(eol)
      .append("\t\t\t<value>").append(value).append("</value>").append(tail)
      .append("</right>").append(eol);
  }

  private static Target target(Path objectFile) throws IOException {
    Path file = objectFile.toAbsolutePath().normalize();
    boolean edt = EdtLayout.isObjectFile(file);
    Path root = edt ? edtSourceRoot(file) : designerRoot(file);
    if (root == null) {
      throw new IllegalArgumentException("Файл не в каталоге конфигурации: " + objectFile);
    }
    boolean configuration = edt
      ? file.equals(root.resolve("Configuration").resolve("Configuration.mdo"))
      : file.equals(root.resolve("Configuration.xml"));
    if (configuration) {
      String name = edt ? EdtObjectReader.read(file).name() : configurationName(file);
      return new Target(root, edt, "Configuration." + name, "Configuration");
    }
    List<String> parts = new ArrayList<>();
    for (Path part : root.relativize(file)) {
      parts.add(part.toString());
    }
    // У проекта EDT у каждого объекта свой каталог: Catalogs/Товары/Товары.mdo
    if (edt) {
      parts.remove(parts.size() - 1);
    } else {
      parts.set(parts.size() - 1, stem(file));
    }
    StringBuilder object = new StringBuilder();
    String kind = null;
    for (int i = 0; i + 1 < parts.size(); i += 2) {
      String type = kindOfDirectory(parts.get(i));
      if (type == null) {
        throw new IllegalArgumentException("Не объект метаданных: " + objectFile);
      }
      if (kind == null) {
        kind = type;
      }
      if (object.length() > 0) {
        object.append('.');
      }
      object.append(type).append('.').append(parts.get(i + 1));
    }
    if (kind == null) {
      throw new IllegalArgumentException("Не объект метаданных: " + objectFile);
    }
    return new Target(root, edt, object.toString(), kind);
  }

  private static String kindOfDirectory(String directory) {
    for (Map.Entry<String, String> entry : CfObjectPathResolver.subdirsByType().entrySet()) {
      if (entry.getValue().equals(directory)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static Path designerRoot(Path file) {
    for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve("Configuration.xml"))) {
        return dir;
      }
    }
    return null;
  }

  private static Path edtSourceRoot(Path file) {
    for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve("Configuration").resolve("Configuration.mdo"))) {
        return dir;
      }
    }
    return null;
  }

  private static String configurationName(Path configurationXml) throws IOException {
    try {
      return ConfigurationObjectNameReader.readName(configurationXml);
    } catch (javax.xml.stream.XMLStreamException e) {
      throw new IOException("Не прочитано имя конфигурации: " + e.getMessage(), e);
    }
  }

  /** Описания ролей конфигурации по имени. */
  private static List<Path> roleFiles(Target target) throws IOException {
    Path roles = target.sourceRoot().resolve(ROLES);
    if (!Files.isDirectory(roles)) {
      return List.of();
    }
    try (Stream<Path> children = Files.list(roles)) {
      if (target.edt()) {
        return children
          .filter(Files::isDirectory)
          .map(dir -> dir.resolve(dir.getFileName() + ".mdo"))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
      }
      return children
        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".xml"))
        .sorted()
        .toList();
    }
  }

  private static Path rightsFile(Path roleFile, boolean edt) {
    return edt ? roleFile.getParent().resolve(EDT_RIGHTS) : RoleRightsFile.rightsPath(roleFile);
  }

  /**
   * Почему права роли не правятся: роль на поддержке без изменения или правила поддержки не разобраны.
   * Правила поставки проекта EDT читаются один раз на все роли запроса.
   */
  private static final class RoleLocks {

    private final boolean edt;
    private final EdtSupportRules.Rules edtRules;

    RoleLocks(Target target) throws IOException {
      edt = target.edt();
      edtRules = edt && SupportRules.isEnforced() && EdtSupportRules.present(target.sourceRoot())
        ? EdtSupportRules.read(target.sourceRoot())
        : null;
    }

    String of(Path roleFile) throws IOException {
      if (edt) {
        return edtRules != null && "locked".equals(edtRules.effectiveState(EdtSupportRules.rootUuid(roleFile)))
          ? lockedRole(roleFile, edtRules.vendor)
          : null;
      }
      try {
        SupportRules.ensureEditable(roleFile);
        return null;
      } catch (IllegalStateException e) {
        return "locked".equals(SupportRules.objectState(roleFile))
          ? lockedRole(roleFile, SupportRules.rulesFor(roleFile).vendor)
          : e.getMessage();
      }
    }

    private static String lockedRole(Path roleFile, String vendor) {
      return "Роль " + stem(roleFile) + " на поддержке поставщика «" + vendor
        + "» без возможности изменения. Включите возможность изменения или снимите роль с поддержки.";
    }
  }

  private static String synonym(Path roleFile, boolean edt) throws IOException {
    if (!edt) {
      return LocalStrings.pick(ObjectHead.read(roleFile).synonym());
    }
    Map<String, String> byLanguage = new LinkedHashMap<>();
    for (EdtObjectReader.EdtNode entry : EdtObjectReader.read(roleFile).list("synonym")) {
      String key = entry.property("key");
      String value = entry.property("value");
      if (!key.isEmpty() && !value.isEmpty()) {
        byLanguage.put(key, value);
      }
    }
    return LocalStrings.pick(byLanguage);
  }

  private static String stem(Path file) {
    return file.getFileName().toString().replaceFirst("[.](xml|mdo)$", "");
  }

  /**
   * Порядок блоков в файле прав: по идентификатору объекта, как строке.
   *
   * <p>Стандартные реквизиты своего идентификатора не имеют и идут сразу за
   * владельцем, поэтому их место определяет идентификатор владельца.
   */
  static final class UuidOrder {

    private static final Pattern CHILD_HEAD = Pattern.compile(
      "<(\\w+) uuid=\"([^\"]+)\">\\s*<Properties>\\s*<Name>([^<]+)</Name>");

    private final Path root;
    private final boolean edt;
    private final Map<String, String> known = new HashMap<>();

    /**
     * @param root корень выгрузки или каталог {@code src} проекта EDT
     * @param edt проект EDT
     */
    UuidOrder(Path root, boolean edt) {
      this.root = root;
      this.edt = edt;
    }

    int insertionPoint(String text, String object) {
      String own = uuid(object);
      List<RightsText.Block> blocks = RightsText.blocks(text);
      if (blocks.isEmpty() || own == null) {
        return afterBlocks(text, blocks);
      }
      int low = 0;
      int high = blocks.size();
      while (low < high) {
        int mid = (low + high) >>> 1;
        String other = nearestUuid(blocks, mid, low, high);
        if (other == null) {
          break;
        }
        if (other.compareTo(own) < 0) {
          low = mid + 1;
        } else {
          high = mid;
        }
      }
      return low < blocks.size() ? blocks.get(low).start() : afterBlocks(text, blocks);
    }

    /** Идентификатор блока; у нечитаемого берётся ближайший соседний в пределах поиска. */
    private String nearestUuid(List<RightsText.Block> blocks, int mid, int low, int high) {
      for (int step = 0; mid - step >= low || mid + step < high; step++) {
        if (mid + step < high) {
          String value = uuid(blocks.get(mid + step).name());
          if (value != null) {
            return value;
          }
        }
        if (step > 0 && mid - step >= low) {
          String value = uuid(blocks.get(mid - step).name());
          if (value != null) {
            return value;
          }
        }
      }
      return null;
    }

    private static int afterBlocks(String text, List<RightsText.Block> blocks) {
      if (!blocks.isEmpty()) {
        return blocks.get(blocks.size() - 1).end();
      }
      int template = text.indexOf("<restrictionTemplate");
      if (template >= 0) {
        return RightsText.lineStart(text, template);
      }
      int closing = text.lastIndexOf("</Rights>");
      if (closing < 0) {
        throw new IllegalArgumentException("Файл прав без корневого элемента Rights.");
      }
      return RightsText.lineStart(text, closing);
    }

    String uuid(String object) {
      if (known.containsKey(object)) {
        return known.get(object);
      }
      String value = resolve(object);
      known.put(object, value);
      return value;
    }

    private String resolve(String object) {
      try {
        String[] parts = object.split("[.]");
        if (parts.length < 2) {
          return null;
        }
        if (parts.length >= 4 && "StandardAttribute".equals(parts[parts.length - 2])) {
          return uuid(String.join(".", Arrays.copyOf(parts, parts.length - 2)));
        }
        return edt ? edtUuid(parts) : designerUuid(parts);
      } catch (IOException | RuntimeException e) {
        return null;
      }
    }

    private String designerUuid(String[] parts) throws IOException {
      if ("Configuration".equals(parts[0])) {
        return head(root.resolve("Configuration.xml"));
      }
      String directory = CfObjectPathResolver.subdirsByType().get(parts[0]);
      if (directory == null) {
        return null;
      }
      Path file = root.resolve(directory).resolve(parts[1] + ".xml");
      String owner = parts[1];
      String region = null;
      String found = null;
      for (int index = 2; index + 1 < parts.length; index += 2) {
        // Подсистемы, перерасчёты, таблицы лежат своими файлами в каталоге владельца
        Path own = file.resolveSibling(owner).resolve(parts[index] + "s").resolve(parts[index + 1] + ".xml");
        if (Files.isRegularFile(own)) {
          file = own;
          owner = parts[index + 1];
          region = null;
          found = null;
          continue;
        }
        if (region == null) {
          if (!Files.isRegularFile(file)) {
            return null;
          }
          region = Files.readString(file, StandardCharsets.UTF_8);
        }
        Matcher matcher = CHILD_HEAD.matcher(region);
        found = null;
        while (matcher.find()) {
          if (matcher.group(1).equals(parts[index]) && matcher.group(3).equals(parts[index + 1])) {
            found = matcher.group(2).toLowerCase(Locale.ROOT);
            int close = region.indexOf("</" + parts[index] + ">", matcher.end());
            region = region.substring(matcher.start(), close < 0 ? region.length() : close);
            break;
          }
        }
        if (found == null) {
          return null;
        }
      }
      return found != null ? found : head(file);
    }

    /**
     * Идентификатор узла проекта EDT: вложенная подсистема лежит своим каталогом,
     * реквизиты, команды, перерасчёты и прочие узлы записаны в описании владельца
     * элементами {@code attributes}, {@code commands}, {@code recalculations}.
     */
    private String edtUuid(String[] parts) throws IOException {
      Path file = "Configuration".equals(parts[0])
        ? root.resolve(EdtLayout.CONFIGURATION_MDO)
        : edtObjectFile(parts[0], parts[1]);
      if (file == null) {
        return null;
      }
      EdtObjectReader.EdtNode node = null;
      for (int index = 2; index + 1 < parts.length; index += 2) {
        Path own = file.resolveSibling(parts[index] + "s").resolve(parts[index + 1]).resolve(parts[index + 1] + ".mdo");
        if (node == null && Files.isRegularFile(own)) {
          file = own;
          continue;
        }
        if (node == null) {
          node = EdtObjectReader.read(file);
        }
        node = edtChild(node, parts[index], parts[index + 1]);
        if (node == null) {
          return null;
        }
      }
      String value = node != null ? node.uuid() : EdtSupportRules.rootUuid(file);
      return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

    private Path edtObjectFile(String type, String name) {
      String directory = CfObjectPathResolver.subdirsByType().get(type);
      return directory == null ? null : root.resolve(directory).resolve(name).resolve(name + ".mdo");
    }

    private static EdtObjectReader.EdtNode edtChild(EdtObjectReader.EdtNode owner, String type, String name) {
      String kind = type + "s";
      for (EdtObjectReader.EdtNode child : owner.children()) {
        if (child.kind().equalsIgnoreCase(kind) && child.name().equals(name)) {
          return child;
        }
      }
      return null;
    }

    private static String head(Path file) {
      String value = ObjectHead.read(file).uuid();
      return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
  }
}
