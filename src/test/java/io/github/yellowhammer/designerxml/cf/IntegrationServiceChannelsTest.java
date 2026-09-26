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

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Каналы сервиса интеграции в выгрузке конфигуратора.
 *
 * <p>Фикстура {@code cf-integration-service} выгружена платформой 8.3.27: сервис
 * с двумя каналами, у отправки есть синоним, у получения обработчик в модуле.
 */
class IntegrationServiceChannelsTest {

  private static final Path SERVICE = Path.of(
    "src", "test", "resources", "cf-integration-service", "IntegrationServices", "СервисИнтеграции1.xml")
    .toAbsolutePath();

  @BeforeEach
  void forgetLanguages() {
    ConfigurationLanguage.forget();
  }

  @Test
  void каналыВходятВСтроение() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(SERVICE, SchemaVersion.V2_20);

    assertThat(dto.kind).isEqualTo("integrationService");
    assertThat(dto.channels).containsExactly("Отправка", "Получение");
  }

  @Test
  void каналыЧитаютсяСоСвойствами() throws Exception {
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(SERVICE, SchemaVersion.V2_20);

    assertThat(dto.channels).extracting(node -> node.name).containsExactly("Отправка", "Получение");
    assertThat(dto.channels).extracting(node -> node.synonym).containsExactly("Отправка заказов", "");
  }
}
