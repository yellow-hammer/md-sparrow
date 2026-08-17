/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Подписи стандартных команд формы, снятые с конфигуратора. */
class StandardCommandLabelsTest {

  @Test
  void подписаныКомандыПроведения() {
    assertThat(StandardCommandLabels.ofForm())
      .containsEntry("PostAndClose", "Провести и закрыть")
      .containsEntry("Post", "Провести")
      .containsEntry("UndoPosting", "Отменить проведение");
  }

  @Test
  void подписаныСлужебныеКоманды() {
    assertThat(StandardCommandLabels.ofForm())
      .containsEntry("CustomizeForm", "Изменить форму...")
      .containsEntry("Help", "Справка");
  }

  @Test
  void подписаныКомандыРаботыСОбъектом() {
    assertThat(StandardCommandLabels.ofForm())
      .containsEntry("SetDeletionMark", "Пометить на удаление / Снять пометку")
      .containsEntry("ShowInList", "Показать в списке")
      .containsEntry("Close", "Закрыть");
  }

  /** Имя команды в словаре стоит без приставки: её ставит вызывающая сторона. */
  @Test
  void имяКомандыБезПриставки() {
    assertThat(StandardCommandLabels.ofForm().keySet()).noneMatch(name -> name.contains("."));
  }

  @Test
  void несняткаяКомандаПодписиНеИмеет() {
    assertThat(StandardCommandLabels.ofForm()).doesNotContainKey("ТакойКомандыНет");
  }

  @Test
  void пустаяПодписьВСловарьНеПопадает() {
    assertThat(StandardCommandLabels.ofForm().values()).doesNotContain("");
  }

  @Test
  void известнаВерсияПлатформыСнятия() {
    assertThat(StandardCommandLabels.platformVersion()).matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void справаСтоятИзменениеФормыИСправка() {
    assertThat(StandardCommandLabels.atRight()).containsExactly("CustomizeForm", "Help");
  }

  /** Правая часть перечисляет команды, а не что-то своё: подпись у каждой должна найтись. */
  @Test
  void командыПравойЧастиПодписаны() {
    assertThat(StandardCommandLabels.ofForm().keySet())
      .containsAll(StandardCommandLabels.atRight());
  }

  @Test
  void словарьОтдаётсяОднимОбъектом() {
    FormStandardCommandsDto dto = StandardCommandLabels.dto();
    assertThat(dto.labels).containsEntry("Help", "Справка");
    assertThat(dto.atRight).contains("Help");
    assertThat(dto.platformVersion).isEqualTo(StandardCommandLabels.platformVersion());
  }
}
