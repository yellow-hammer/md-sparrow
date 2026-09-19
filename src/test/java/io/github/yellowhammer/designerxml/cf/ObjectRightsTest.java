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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * загрузки файлов, исправленных шагами правок из тестов.
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

  @TempDir Path tempDir;

  @Test
  void readsRightsOfEveryRoleOnObject() throws Exception {
    ObjectRights.Dto dto = ObjectRights.read(FIXTURE.resolve("cf/Catalogs/Товары.xml"));

    assertThat(dto.object).isEqualTo("Catalog.Товары");
    assertThat(dto.kind).isEqualTo("Catalog");
    assertThat(dto.rights).isEqualTo(RoleRightsCatalog.kind("Catalog").rights());
    assertThat(dto.requires).containsKey("InteractiveInsert");
    assertThat(dto.editable).isTrue();
    assertThat(dto.readonlyReason).isNull();
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

    assertThat(edt.object).isEqualTo(designer.object);
    assertThat(edt.rights).isEqualTo(designer.rights);
    assertThat(new Gson().toJson(edt.roles)).isEqualTo(new Gson().toJson(designer.roles));
    assertThat(edt.editable).isFalse();
    assertThat(edt.readonlyReason).isNotBlank();
  }

  /** Выдача с нужными правами, снятие с зависящими, возврат к умолчанию роли, новый блок по месту. */
  @Test
  void editsRightsTheWayConfiguratorSavesThem() throws Exception {
    Path cf = copyFixture();

    ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(
      edit("Гость", "InteractiveInsert", true),
      edit("Кладовщик", "Update", true),
      edit("Администратор", "Delete", true)));
    ObjectRights.apply(cf.resolve("Catalogs/ТоварыПоставщиков.xml"), List.of(edit("Кладовщик", "Read", false)));
    ObjectRights.apply(cf.resolve("Catalogs/Склады.xml"), List.of(edit("Администратор", "View", false)));
    ObjectRights.apply(cf.resolve("Configuration.xml"), List.of(edit("Гость", "ThinClient", true)));
    ObjectRights.apply(cf.resolve("Documents/Заказ.xml"), List.of(
      edit("Кладовщик", "InteractivePosting", true),
      edit("Гость", "View", false)));

    assertRightsLike(cf, "after-edits-1");
  }

  /** Снятое право остаётся со своими ограничениями, роль без блоков, новый блок в пустой роли. */
  @Test
  void editsRightsDownToEmptyRoleTheWayConfiguratorSavesThem() throws Exception {
    Path cf = copyFixture();

    ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(edit("Кладовщик", "Read", false)));
    ObjectRights.apply(cf.resolve("Documents/Заказ.xml"), List.of(
      edit("Гость", "Read", false),
      edit("Кладовщик", "View", true)));
    ObjectRights.apply(cf.resolve("Catalogs/Склады.xml"), List.of(edit("Гость", "InputByString", true)));
    ObjectRights.apply(cf.resolve("Configuration.xml"), List.of(edit("Администратор", "DataAdministration", false)));

    assertRightsLike(cf, "after-edits-2");
  }

  /** Снятое право с ограничениями переживает следующие правки блока, в том числе оставаясь в нём одно. */
  @Test
  void keepsRestrictedRightWhateverItsValue() throws Exception {
    Path cf = copyFixture();
    Path rights = cf.resolve("Roles/Кладовщик/Ext/Rights.xml");

    ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(edit("Кладовщик", "Read", false)));
    byte[] withoutRead = Files.readAllBytes(rights);
    ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(edit("Кладовщик", "View", false)));
    assertThat(Files.readAllBytes(rights)).isEqualTo(withoutRead);
    ObjectRights.apply(cf.resolve("Documents/Заказ.xml"), List.of(edit("Кладовщик", "Read", false)));

    assertRightsLike(cf, "after-edits-3");
  }

  @Test
  void keepsFileUntouchedWhenRightAlreadyHasValue() throws Exception {
    Path cf = copyFixture();
    Path rights = cf.resolve("Roles/Кладовщик/Ext/Rights.xml");
    byte[] before = Files.readAllBytes(rights);

    ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(edit("Кладовщик", "View", true)));

    assertThat(Files.readAllBytes(rights)).isEqualTo(before);
  }

  /** Отказ на одной роли оставляет файлы всех ролей как были. */
  @Test
  void writesNothingWhenOneRoleFails() throws Exception {
    Path cf = copyFixture();
    Path guest = cf.resolve("Roles/Гость/Ext/Rights.xml");
    byte[] before = Files.readAllBytes(guest);

    assertThatThrownBy(() -> ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(
        edit("Гость", "Read", true),
        edit("Удалённая", "Read", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Удалённая");
    assertThat(Files.readAllBytes(guest)).isEqualTo(before);

    lockRole(cf, "f2eea809-e11c-305c-bf2a-eb01d363fd58");
    assertThatThrownBy(() -> ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(
        edit("Гость", "Read", true),
        edit("Кладовщик", "Update", true))))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("Роль Кладовщик на поддержке поставщика «Поставщик» без возможности изменения.");
    assertThat(Files.readAllBytes(guest)).isEqualTo(before);
  }

  @Test
  void namesRoleClosedBySupport() throws Exception {
    Path cf = copyFixture();
    lockRole(cf, "f2eea809-e11c-305c-bf2a-eb01d363fd58");

    ObjectRights.Dto dto = ObjectRights.read(cf.resolve("Catalogs/Товары.xml"));

    assertThat(role(dto, "Кладовщик").readonlyReason)
      .startsWith("Роль Кладовщик на поддержке поставщика «Поставщик» без возможности изменения.");
    assertThat(role(dto, "Гость").readonlyReason).isNull();
  }

  @Test
  void rejectsRightOutsideObjectKind() throws Exception {
    Path cf = copyFixture();

    assertThatThrownBy(() -> ObjectRights.apply(cf.resolve("Catalogs/Товары.xml"), List.of(edit("Гость", "Posting", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Posting");
  }

  @Test
  void refusesToEditEdtProject() {
    assertThatThrownBy(() -> ObjectRights.apply(
        FIXTURE.resolve("edt/src/Catalogs/Товары/Товары.mdo"), List.of(edit("Гость", "Read", true))))
      .isInstanceOf(IllegalArgumentException.class);
  }

  /** Стандартный реквизит берёт идентификатор владельца, даже если владельца ещё не спрашивали. */
  @Test
  void resolvesStandardAttributeBeforeItsOwner() {
    ObjectRights.UuidOrder order = new ObjectRights.UuidOrder(FIXTURE.resolve("cf"));

    assertThat(order.uuid("Catalog.Товары.StandardAttribute.Code")).isEqualTo("93b87574-f379-39e5-94a8-c258efd2a608");
  }

  /** Место нового блока ищется по идентификаторам: на всех ролях выгрузки ssl31 они идут по порядку. */
  @Test
  void blocksOfSsl31RolesGoInOrderOfIdentifiers() throws Exception {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf");
    ObjectRights.UuidOrder order = new ObjectRights.UuidOrder(cf);
    int checked = 0;
    List<Path> roles;
    try (Stream<Path> files = Files.list(cf.resolve("Roles"))) {
      roles = files.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
    }
    for (Path role : roles) {
      String text = Files.readString(RoleRightsFile.rightsPath(role), StandardCharsets.UTF_8);
      String previous = "";
      for (RightsText.Block block : RightsText.blocks(text)) {
        String uuid = order.uuid(block.name());
        assertThat(uuid).as(role.getFileName() + ": " + block.name()).isNotNull();
        assertThat(uuid.compareTo(previous)).as(role.getFileName() + ": " + block.name()).isGreaterThanOrEqualTo(0);
        previous = uuid;
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(1000);
  }

  private static ObjectRights.RoleDto role(ObjectRights.Dto dto, String name) {
    return dto.roles.stream().filter(role -> role.name.equals(name)).findFirst().orElseThrow();
  }

  /** Правила поддержки с одной ролью поставщика без возможности изменения. */
  private static void lockRole(Path cf, String uuid) throws IOException {
    String rules = "﻿{6,0,1,"
      + "dddddddd-4444-4444-8444-444444444444,0,"
      + "eeeeeeee-5555-4555-8555-555555555555,"
      + "\"1.0.0.1\",\"Поставщик\",\"Поставка\",1,"
      + "0,0," + uuid + "," + uuid + ","
      + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}";
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

  /** Файлы прав ролей как в ожидаемом каталоге; роли, которых там нет, остаются как в {@code cf}. */
  private static void assertRightsLike(Path cf, String expected) throws IOException {
    for (String role : ROLES) {
      Path relative = Path.of("Roles", role, "Ext", "Rights.xml");
      Path file = FIXTURE.resolve(expected).resolve(relative);
      if (!Files.isRegularFile(file)) {
        file = FIXTURE.resolve("cf").resolve(relative);
      }
      assertThat(Files.readString(cf.resolve(relative), StandardCharsets.UTF_8))
        .as(role)
        .isEqualTo(Files.readString(file, StandardCharsets.UTF_8));
    }
  }

  private Path copyFixture() throws IOException {
    Path target = tempDir.resolve("cf");
    copyTree(FIXTURE.resolve("cf"), target);
    return target;
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
