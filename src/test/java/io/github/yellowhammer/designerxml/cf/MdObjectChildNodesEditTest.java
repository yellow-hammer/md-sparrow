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
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Запись свойств узлов состава в выгрузке конфигуратора: команд, каналов, признаков учёта,
 * граф, реквизитов адресации, шаблонов URL, операций, измерений и ресурсов.
 *
 * <p>{@code cf-child-nodes} выгружена платформой 8.3.27. {@code cf-child-nodes-edited}
 * выгружена ею же после загрузки файлов с теми же правками: запись должна совпасть с ней
 * байт в байт.
 */
class MdObjectChildNodesEditTest {

  private static final Path BEFORE = Path.of("src", "test", "resources", "cf-child-nodes");
  private static final Path AFTER = Path.of("src", "test", "resources", "cf-child-nodes-edited");

  @TempDir
  Path root;

  @BeforeEach
  void copyFixture() throws IOException {
    ConfigurationLanguage.forget();
    try (Stream<Path> files = Files.walk(BEFORE)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path target = root.resolve(BEFORE.relativize(file).toString());
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
      }
    }
  }

  @Test
  void commandSynonymCommentAndToolTipReachFile() throws Exception {
    String file = "Catalogs/Справочник1.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto command = node(dto.commands, "Команда1");
    command.synonym = "Открыть первую";
    command.comment = "Команда проверки";
    command.toolTip = "Открывает форму";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void channelSynonymAndCommentReachFile() throws Exception {
    String file = "IntegrationServices/СервисИнтеграции1.xml";
    MdObjectPropertiesDto dto = read(file);
    node(dto.channels, "Получение").synonym = "Получение ответов";
    node(dto.channels, "Отправка").comment = "Очередь заказов";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void accountingFlagsPaletteReachesFile() throws Exception {
    String file = "ChartsOfAccounts/ПланСчетов1.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto flag = node(dto.accountingFlags, "Валютный");
    flag.synonym = "Валютный учёт";
    flag.toolTip = "Учёт в валюте";
    flag.fillChecking = "SHOW_ERROR";
    flag.dataHistory = "DONT_USE";
    MdNamedPropertyDto extDimensionFlag = node(dto.extDimensionAccountingFlags, "Суммовой");
    extDimensionFlag.comment = "Сумма по субконто";
    extDimensionFlag.quickChoice = "DONT_USE";
    extDimensionFlag.createOnInput = "USE";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void journalColumnSynonymAndIndexingReachFile() throws Exception {
    String file = "DocumentJournals/Журнал1.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto column = node(dto.columns, "Колонка1");
    column.synonym = "Колонка";
    column.indexing = "INDEX";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void addressingAttributeTypeAndFlagsReachFile() throws Exception {
    String file = "Tasks/Задача1.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto attribute = node(dto.addressingAttributes, "Исполнитель");
    attribute.synonym = "Исполнитель задачи";
    attribute.fullTextSearch = "DONT_USE";
    attribute.indexing = "INDEX";
    attribute.choiceHistoryOnInput = "DONT_USE";
    attribute.type.stringQualifiers.length = "100";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void urlTemplateSynonymAndCommentReachFile() throws Exception {
    // У шаблона свои методы с такими же свойствами: правка не должна уйти в них
    String file = "HTTPServices/Биллинг.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto template = node(dto.urlTemplates, "СчетНаОплату");
    template.synonym = "Счёт на оплату";
    template.comment = "Выставление счёта";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void operationSynonymAndCommentReachFile() throws Exception {
    String file = "WebServices/InterfaceVersion.xml";
    MdObjectPropertiesDto dto = read(file);
    MdNamedPropertyDto operation = node(dto.operations, "GetVersions");
    operation.synonym = "Версии интерфейса";
    operation.comment = "Поддерживаемые версии";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void informationRegisterDimensionAndResourceReachFile() throws Exception {
    String file = "InformationRegisters/РегистрСведений1.xml";
    MdObjectPropertiesDto dto = read(file);
    node(dto.dimensions, "Подразделение").comment = "Код подразделения";
    node(dto.resources, "Сумма").synonym = "Сумма";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void accountingRegisterDimensionAndResourceReachFile() throws Exception {
    // У регистра бухгалтерии нет своего моста свойств: измерения и ресурсы пишутся общим путём
    String file = "AccountingRegisters/РегистрБухгалтерии1.xml";
    MdObjectPropertiesDto dto = read(file);
    node(dto.dimensions, "Подразделение").synonym = "Подразделение";
    node(dto.resources, "Сумма").comment = "Сумма проводки";

    write(file, dto);

    assertWrittenLikePlatform(file);
  }

  @Test
  void paletteJsonWritesChannel() throws Exception {
    // Палитра присылает DTO, прочитанный из JSON: без пустых полей и со всеми узлами объекта
    String file = "IntegrationServices/СервисИнтеграции1.xml";
    Gson gson = new Gson();
    MdObjectPropertiesDto dto = gson.fromJson(gson.toJson(read(file)), MdObjectPropertiesDto.class);
    node(dto.channels, "Получение").synonym = "Получение ответов";
    node(dto.channels, "Отправка").comment = "Очередь заказов";

    write(file, gson.fromJson(gson.toJson(dto), MdObjectPropertiesDto.class));

    assertWrittenLikePlatform(file);
  }

  @Test
  void payloadWithoutChildListsKeepsNodes() throws Exception {
    String file = "Catalogs/Справочник1.xml";
    byte[] before = Files.readAllBytes(root.resolve(file));
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = "catalog";
    dto.internalName = "Справочник1";
    dto.commands = null;

    write(file, dto);

    assertThat(Files.readAllBytes(root.resolve(file))).isEqualTo(before);
  }

  @Test
  void unchangedNodesLeaveFileAsIs() throws Exception {
    String file = "Tasks/Задача1.xml";
    byte[] before = Files.readAllBytes(root.resolve(file));

    write(file, read(file));

    assertThat(Files.readAllBytes(root.resolve(file))).isEqualTo(before);
  }

  @Test
  void recalculationIsRefusedInRegisterFile(@TempDir Path temp) throws Exception {
    // В составе регистра расчёта от перерасчёта только имя, свойства в Recalculations/<Имя>.xml
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/CalculationRegisters/_ДемоОсновныеНачисления.xml");
    Path copy = temp.resolve(source.getFileName().toString());
    Files.copy(source, copy);
    byte[] before = Files.readAllBytes(copy);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    node(dto.recalculations, "ПерерасчетОсновныхНачислений").synonym = "Перерасчёт";

    assertThatThrownBy(() -> MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Свойства перерасчёта «ПерерасчетОсновныхНачислений» правятся в отдельном файле");
    assertThat(Files.readAllBytes(copy)).isEqualTo(before);
  }

  @Test
  void payloadReadBeforeCommandAddIsRefused() throws Exception {
    String file = "Catalogs/Справочник1.xml";
    MdObjectPropertiesDto dto = read(file);
    MdObjectChildMutations.addCommand(root.resolve(file), SchemaVersion.V2_20, "Команда2");
    byte[] before = Files.readAllBytes(root.resolve(file));
    dto.synonym = "Справочник";

    assertThatThrownBy(() -> write(file, dto))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Состав команд объекта изменился после чтения, перечитайте свойства");
    assertThat(Files.readAllBytes(root.resolve(file))).isEqualTo(before);
  }

  private MdObjectPropertiesDto read(String file) throws Exception {
    return MdObjectPropertiesEdit.readDto(root.resolve(file), SchemaVersion.V2_20);
  }

  private void write(String file, MdObjectPropertiesDto dto) throws Exception {
    MdObjectPropertiesEdit.writeDto(root.resolve(file), SchemaVersion.V2_20, dto);
  }

  private void assertWrittenLikePlatform(String file) throws IOException {
    assertThat(Files.readString(root.resolve(file), StandardCharsets.UTF_8))
      .isEqualTo(Files.readString(AFTER.resolve(file), StandardCharsets.UTF_8));
  }

  private static MdNamedPropertyDto node(List<MdNamedPropertyDto> nodes, String name) {
    return nodes.stream()
      .filter(item -> name.equals(item.name))
      .findFirst()
      .orElseThrow(() -> new AssertionError("нет узла " + name));
  }
}
