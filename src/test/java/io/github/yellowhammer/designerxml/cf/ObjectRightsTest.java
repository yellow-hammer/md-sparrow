/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Права ролей на объект.
 *
 * <p>Фикстура {@code object-rights}: {@code cf} выгрузил конфигуратор, {@code edt} получен
 * импортом той же выгрузки в 1С:EDT, {@code after-edits-*} выгрузил конфигуратор после
 * загрузки файлов, исправленных шагами правок из тестов, {@code edt-after-edits-*} записала
 * 1С:EDT импортом выгрузок {@code after-edits-*}.
 */
class ObjectRightsTest {

  private static final Path FIXTURE = Path.of("src", "test", "resources", "object-rights").toAbsolutePath();
  private static final List<String> ROLES = List.of("Администратор", "Гость", "Кладовщик");
  private static final List<String> DELETE = List.of(
    "Delete",
    "InteractiveDelete",
    "InteractiveDeleteMarked",
    "InteractiveDeletePredefinedData",
    "InteractiveDeleteMarkedPredefinedData");
  private static final String STOREKEEPER_UUID = "f2eea809-e11c-305c-bf2a-eb01d363fd58";
  private static final String GOODS_UUID = "93b87574-f379-39e5-94a8-c258efd2a608";

  @TempDir Path tempDir;

  @AfterEach
  void enforceSupportAgain() {
    SupportRules.setEnforced(true);
  }

  /** Копия фикстуры в формате выгрузки конфигуратора ({@code cf}) или проекта 1С:EDT ({@code edt}). */
  private record Project(Path root, boolean edt) {

    Path object(String directory, String name) {
      return edt
        ? root.resolve("src").resolve(directory).resolve(name).resolve(name + ".mdo")
        : root.resolve(directory).resolve(name + ".xml");
    }

    Path configuration() {
      return edt ? root.resolve("src/Configuration/Configuration.mdo") : root.resolve("Configuration.xml");
    }

    /** Файл прав роли от корня проекта. */
    Path rights(String role) {
      return edt ? Path.of("src", "Roles", role, "Rights.rights") : Path.of("Roles", role, "Ext", "Rights.xml");
    }

    /** Каталог с ожидаемыми файлами прав после шагов правок. */
    Path expected(String name) {
      return FIXTURE.resolve(edt ? "edt-" + name : name);
    }
  }

  @Test
  void readsRightsOfEveryRoleOnObject() throws Exception {
    ObjectRights.Dto dto = ObjectRights.read(FIXTURE.resolve("cf/Catalogs/Товары.xml"));

    assertThat(dto.object).isEqualTo("Catalog.Товары");
    assertThat(dto.kind).isEqualTo("Catalog");
    assertThat(dto.rights).isEqualTo(RoleRightsCatalog.kind("Catalog").rights());
    assertThat(dto.requires).containsKey("InteractiveInsert");
    assertThat(dto.editable).isTrue();
    assertThat(dto.roles).extracting(role -> role.name).containsExactlyElementsOf(ROLES);

    ObjectRights.RoleDto admin = role(dto, "Администратор");
    assertThat(admin.setForNewObjects).isTrue();
    List<String> notDeleting = new ArrayList<>(dto.rights);
    notDeleting.removeAll(DELETE);
    assertThat(admin.granted).containsExactlyElementsOf(notDeleting);

    assertThat(role(dto, "Гость").granted).isEmpty();

    ObjectRights.RoleDto storekeeper = role(dto, "Кладовщик");
    assertThat(storekeeper.synonym).isEqualTo("Кладовщик склада");
    assertThat(storekeeper.setForNewObjects).isFalse();
    assertThat(storekeeper.granted).containsExactly("Read", "View");
    assertThat(storekeeper.readonlyReason).isNull();
  }

  @Test
  void readsRestrictionsAndChildRightsWithoutNeighbourObjects() throws Exception {
    ObjectRights.RoleDto storekeeper = role(ObjectRights.read(FIXTURE.resolve("cf/Catalogs/Товары.xml")), "Кладовщик");

    assertThat(storekeeper.restrictions).containsOnlyKeys("Read");
    List<Map<String, Object>> read = storekeeper.restrictions.get("Read");
    assertThat(read).hasSize(2);
    assertThat(read.get(0).get("fields")).isEqualTo(List.of());
    assertThat(read.get(0).get("condition")).isEqualTo(
      "#Если &ОграничениеПоСкладу #Тогда\n#ПоЗначениям(\"Справочник.Товары\", \"\", \"\", \"Склады\", \"Склад\")\n#КонецЕсли");
    assertThat(read.get(1).get("fields")).isEqualTo(List.of("Цена"));
    assertThat(read.get(1).get("condition")).isEqualTo("ГДЕ ЛОЖЬ");

    // Блок Catalog.ТоварыПоставщиков начинается с того же имени, но подчинённым не считается
    assertThat(storekeeper.children).extracting(child -> child.name).containsExactly("Attribute.Цена");
    assertThat(storekeeper.children.get(0).rights).containsExactly(Map.entry("View", false), Map.entry("Edit", false));
  }

  @Test
  void readsRestrictionsOfRightThatIsNotGranted() throws Exception {
    ObjectRights.RoleDto storekeeper = role(ObjectRights.read(FIXTURE.resolve("cf/Documents/Заказ.xml")), "Кладовщик");

    assertThat(storekeeper.granted).containsExactly("Read");
    assertThat(storekeeper.restrictions).containsOnlyKeys("Insert");
    assertThat(storekeeper.restrictions.get("Insert").get(0).get("condition")).isEqualTo("ГДЕ ЛОЖЬ");
  }

  @Test
  void readsConfigurationRights() throws Exception {
    ObjectRights.Dto dto = ObjectRights.read(FIXTURE.resolve("cf/Configuration.xml"));

    assertThat(dto.object).isEqualTo("Configuration.Склад");
    assertThat(dto.kind).isEqualTo("Configuration");
    assertThat(role(dto, "Администратор").granted).containsExactlyElementsOf(dto.rights);
    assertThat(role(dto, "Гость").granted).isEmpty();
    assertThat(role(dto, "Кладовщик").granted).containsExactly("ThinClient");
  }

  @Test
  void readsEdtProjectLikeDesignerDump() throws Exception {
    ObjectRights.Dto designer = ObjectRights.read(FIXTURE.resolve("cf/Catalogs/Товары.xml"));
    ObjectRights.Dto edt = ObjectRights.read(FIXTURE.resolve("edt/src/Catalogs/Товары/Товары.mdo"));

    assertThat(new Gson().toJson(edt)).isEqualTo(new Gson().toJson(designer));
  }

  /** Выдача с нужными правами, снятие с зависящими, возврат к умолчанию роли, новый блок по месту. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void editsRightsTheWayPlatformSavesThem(String format) throws Exception {
    Project project = copyFixture(format);

    ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(
      edit("Гость", "InteractiveInsert", true),
      edit("Кладовщик", "Update", true),
      edit("Администратор", "Delete", true)));
    ObjectRights.apply(project.object("Catalogs", "ТоварыПоставщиков"), List.of(edit("Кладовщик", "Read", false)));
    ObjectRights.apply(project.object("Catalogs", "Склады"), List.of(edit("Администратор", "View", false)));
    ObjectRights.apply(project.configuration(), List.of(edit("Гость", "ThinClient", true)));
    ObjectRights.apply(project.object("Documents", "Заказ"), List.of(
      edit("Кладовщик", "InteractivePosting", true),
      edit("Гость", "View", false)));

    assertRightsLike(project, "after-edits-1");
  }

  /** Снятое право остаётся со своими ограничениями, роль без блоков, новый блок в пустой роли. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void editsRightsDownToEmptyRoleTheWayPlatformSavesThem(String format) throws Exception {
    Project project = copyFixture(format);

    ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(edit("Кладовщик", "Read", false)));
    ObjectRights.apply(project.object("Documents", "Заказ"), List.of(
      edit("Гость", "Read", false),
      edit("Кладовщик", "View", true)));
    ObjectRights.apply(project.object("Catalogs", "Склады"), List.of(edit("Гость", "InputByString", true)));
    ObjectRights.apply(project.configuration(), List.of(edit("Администратор", "DataAdministration", false)));

    assertRightsLike(project, "after-edits-2");
  }

  /** Снятое право с ограничениями переживает следующие правки блока, в том числе оставаясь в нём одно. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void keepsRestrictedRightWhateverItsValue(String format) throws Exception {
    Project project = copyFixture(format);
    Path rights = project.root().resolve(project.rights("Кладовщик"));

    ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(edit("Кладовщик", "Read", false)));
    byte[] withoutRead = Files.readAllBytes(rights);
    ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(edit("Кладовщик", "View", false)));
    assertThat(Files.readAllBytes(rights)).isEqualTo(withoutRead);
    ObjectRights.apply(project.object("Documents", "Заказ"), List.of(edit("Кладовщик", "Read", false)));

    assertRightsLike(project, "after-edits-3");
  }

  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void keepsFileUntouchedWhenRightAlreadyHasValue(String format) throws Exception {
    Project project = copyFixture(format);
    Path rights = project.root().resolve(project.rights("Кладовщик"));
    byte[] before = Files.readAllBytes(rights);

    ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(edit("Кладовщик", "View", true)));

    assertThat(Files.readAllBytes(rights)).isEqualTo(before);
  }

  /** Отказ на одной роли оставляет файлы всех ролей как были. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void writesNothingWhenOneRoleFails(String format) throws Exception {
    Project project = copyFixture(format);
    Path goods = project.object("Catalogs", "Товары");
    Path guest = project.root().resolve(project.rights("Гость"));
    byte[] before = Files.readAllBytes(guest);

    assertThatThrownBy(() -> ObjectRights.apply(goods, List.of(
        edit("Гость", "Read", true),
        edit("Удалённая", "Read", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Удалённая");
    assertThat(Files.readAllBytes(guest)).isEqualTo(before);

    lock(project, STOREKEEPER_UUID);
    assertThatThrownBy(() -> ObjectRights.apply(goods, List.of(
        edit("Гость", "Read", true),
        edit("Кладовщик", "Update", true))))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("Роль Кладовщик на поддержке поставщика «Поставщик» без возможности изменения.");
    assertThat(Files.readAllBytes(guest)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void namesRoleClosedBySupport(String format) throws Exception {
    Project project = copyFixture(format);
    lock(project, STOREKEEPER_UUID);

    ObjectRights.Dto dto = ObjectRights.read(project.object("Catalogs", "Товары"));

    assertThat(dto.editable).isTrue();
    assertThat(role(dto, "Кладовщик").readonlyReason)
      .startsWith("Роль Кладовщик на поддержке поставщика «Поставщик» без возможности изменения.");
    assertThat(role(dto, "Гость").readonlyReason).isNull();

    SupportRules.setEnforced(false);
    assertThat(role(ObjectRights.read(project.object("Catalogs", "Товары")), "Кладовщик").readonlyReason).isNull();
  }

  /** Права лежат в файлах ролей: объект на поддержке без изменения правке прав не мешает. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void editsRightsOfObjectClosedBySupportThroughCli(String format) throws Exception {
    Project project = copyFixture(format);
    lock(project, GOODS_UUID);
    Path params = tempDir.resolve("params.json");
    Files.writeString(params, new Gson().toJson(Map.of(
      "op", "cf-object-rights-set",
      "objectXml", project.object("Catalogs", "Товары").toString(),
      "payloadJson", "{\"edits\":[{\"role\":\"Гость\",\"right\":\"InteractiveInsert\",\"value\":true}]}")),
      StandardCharsets.UTF_8);

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    assertThat(Files.readString(project.root().resolve(project.rights("Гость")), StandardCharsets.UTF_8))
      .contains("<name>Catalog.Товары</name>", "<name>InteractiveInsert</name>");
  }

  @Test
  void rejectsRightOutsideObjectKind() throws Exception {
    Project project = copyFixture("cf");

    assertThatThrownBy(() -> ObjectRights.apply(project.object("Catalogs", "Товары"), List.of(edit("Гость", "Posting", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Posting");
  }

  /** Стандартный реквизит берёт идентификатор владельца, даже если владельца ещё не спрашивали. */
  @ParameterizedTest
  @ValueSource(strings = {"cf", "edt"})
  void resolvesIdentifiersOfObjectNodes(String format) {
    Project project = project(FIXTURE.resolve(format), format);
    Path root = project.edt() ? project.root().resolve("src") : project.root();

    assertThat(new ObjectRights.UuidOrder(root, project.edt()).uuid("Catalog.Товары.StandardAttribute.Code"))
      .isEqualTo(GOODS_UUID);
    ObjectRights.UuidOrder order = new ObjectRights.UuidOrder(root, project.edt());
    assertThat(order.uuid("Catalog.Товары.Attribute.Цена")).isEqualTo("427391ca-edc3-4684-adfd-089e8458e886");
    assertThat(order.uuid("Configuration.Склад")).isEqualTo("4c8dcf50-46db-36c5-9c03-8d8348faee4a");
    assertThat(order.uuid("Catalog.Товары.Attribute.Нет")).isNull();
  }

  /** Место нового блока ищется по идентификаторам: на всех ролях выгрузки ssl31 они идут по порядку. */
  @Test
  void blocksOfSsl31RolesGoInOrderOfIdentifiers() throws Exception {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf");
    List<Path> roles;
    try (Stream<Path> files = Files.list(cf.resolve("Roles"))) {
      roles = files.filter(path -> path.toString().endsWith(".xml")).sorted().map(RoleRightsFile::rightsPath).toList();
    }
    assertBlocksGoInOrderOfIdentifiers(new ObjectRights.UuidOrder(cf, false), roles);
  }

  /** Так же у проекта 1С:EDT: идентификаторы берутся из описаний объектов. */
  @Test
  void blocksOfSsl31EdtRolesGoInOrderOfIdentifiers() throws Exception {
    Path src = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    List<Path> roles;
    try (Stream<Path> dirs = Files.list(src.resolve("Roles"))) {
      roles = dirs.map(dir -> dir.resolve("Rights.rights")).filter(Files::isRegularFile).sorted().toList();
    }
    assertBlocksGoInOrderOfIdentifiers(new ObjectRights.UuidOrder(src, true), roles);
  }

  private static void assertBlocksGoInOrderOfIdentifiers(ObjectRights.UuidOrder order, List<Path> rightsFiles)
      throws IOException {
    int checked = 0;
    for (Path file : rightsFiles) {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String previous = "";
      for (RightsText.Block block : RightsText.blocks(text)) {
        String uuid = order.uuid(block.name());
        assertThat(uuid).as(file + ": " + block.name()).isNotNull();
        assertThat(uuid.compareTo(previous)).as(file + ": " + block.name()).isGreaterThanOrEqualTo(0);
        previous = uuid;
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(1000);
  }

  private static ObjectRights.RoleDto role(ObjectRights.Dto dto, String name) {
    return dto.roles.stream().filter(role -> role.name.equals(name)).findFirst().orElseThrow();
  }

  /** Правила поддержки с одним объектом поставщика без возможности изменения. */
  private static void lock(Project project, String uuid) throws IOException {
    if (project.edt()) {
      String rules = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<distributionSupport:DistributionSupport"
        + " xmlns:distributionSupport=\"http://g5.1c.ru/v8/dt/distribution/model\" version=\"6\" fileState=\"Normal\">\n"
        + "  <parentConfigurationInfos id=\"dddddddd-4444-4444-8444-444444444444\" storeMode=\"Self\""
        + " configRelease=\"1.0.0.1\" providerName=\"Поставщик\" configName=\"Поставка\""
        + " configVersion=\"eeeeeeee-5555-4555-8555-555555555555\">\n"
        + "    <items userId=\"" + uuid + "\" parentId=\"" + uuid + "\" userMode=\"ChangesNotAllowed\" used=\"true\"/>\n"
        + "  </parentConfigurationInfos>\n"
        + "</distributionSupport:DistributionSupport>\n";
      Files.writeString(project.root().resolve("src/Configuration/Configuration.distr"), rules, StandardCharsets.UTF_8);
      return;
    }
    String rules = "﻿{6,0,1,"
      + "dddddddd-4444-4444-8444-444444444444,0,"
      + "eeeeeeee-5555-4555-8555-555555555555,"
      + "\"1.0.0.1\",\"Поставщик\",\"Поставка\",1,"
      + "0,0," + uuid + "," + uuid + ","
      + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}";
    Path cf = project.root();
    Files.createDirectories(SupportRules.vendorPayloadDir(cf));
    Files.writeString(SupportRules.vendorPayloadDir(cf).resolve("Поставка.cf"), "поставка");
    Files.writeString(SupportRules.rulesPath(cf), rules, StandardCharsets.UTF_8);
  }

  private static ObjectRights.Edit edit(String role, String right, boolean value) {
    ObjectRights.Edit edit = new ObjectRights.Edit();
    edit.role = role;
    edit.right = right;
    edit.value = value;
    return edit;
  }

  /** Файлы прав ролей как в ожидаемом каталоге; роли, которых там нет, остаются как в фикстуре. */
  private static void assertRightsLike(Project project, String expected) throws IOException {
    Path source = FIXTURE.resolve(project.edt() ? "edt" : "cf");
    for (String role : ROLES) {
      Path relative = project.rights(role);
      Path file = project.expected(expected).resolve(relative);
      if (!Files.isRegularFile(file)) {
        file = source.resolve(relative);
      }
      assertThat(Files.readString(project.root().resolve(relative), StandardCharsets.UTF_8))
        .as(role)
        .isEqualTo(Files.readString(file, StandardCharsets.UTF_8));
    }
  }

  private Project copyFixture(String format) throws IOException {
    Path target = tempDir.resolve(format);
    copyTree(FIXTURE.resolve(format), target);
    return project(target, format);
  }

  private static Project project(Path root, String format) {
    return new Project(root, "edt".equals(format));
  }

  private static void copyTree(Path source, Path target) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(target);
      try (Stream<Path> children = Files.list(source)) {
        for (Path child : children.toList()) {
          copyTree(child, target.resolve(child.getFileName().toString()));
        }
      }
      return;
    }
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
  }
}
