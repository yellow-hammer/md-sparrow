/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import java.util.List;

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
  void подписаныКомандыЗаписи() {
    assertThat(StandardCommandLabels.ofForm())
      .containsEntry("WriteAndClose", "Записать и закрыть")
      .containsEntry("Write", "Записать")
      .containsEntry("Reread", "Перечитать");
  }

  @Test
  void подписаныКомандыСтарта() {
    assertThat(StandardCommandLabels.ofForm())
      .containsEntry("StartAndClose", "Стартовать и закрыть")
      .containsEntry("Start", "Старт");
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

  @Test
  void подписанаИсторияИзменений() {
    assertThat(StandardCommandLabels.ofForm()).containsEntry("ChangeHistory", "История изменений");
  }

  @Test
  void подписаныКомандыТаблицыСписка() {
    assertThat(StandardCommandLabels.ofTable())
      .containsEntry("Create", "Создать")
      .containsEntry("Change", "Изменить")
      .containsEntry("Refresh", "Обновить")
      .containsEntry("Find", "Расширенный поиск")
      .containsEntry("OutputList", "Вывести список...");
  }

  @Test
  void подписаныКомандыТабличнойЧасти() {
    assertThat(StandardCommandLabels.ofTable())
      .containsEntry("Add", "Добавить")
      .containsEntry("Delete", "Удалить")
      .containsEntry("MoveUp", "Переместить вверх")
      .containsEntry("MoveDown", "Переместить вниз")
      .containsEntry("SortListAsc", "Сортировать по возрастанию");
  }

  /** Подписи у команды таблицы свои: одноимённая команда формы подписана иначе. */
  @Test
  void командаТаблицыПодписанаНеКакКомандаФормы() {
    assertThat(StandardCommandLabels.ofTable().get("Find"))
      .isNotEqualTo(StandardCommandLabels.ofForm().get("Find"));
  }

  /** Имя команды в словаре стоит без приставки: её ставит вызывающая сторона. */
  @Test
  void имяКомандыБезПриставки() {
    assertThat(StandardCommandLabels.ofForm().keySet()).noneMatch(name -> name.contains("."));
    assertThat(StandardCommandLabels.ofTable().keySet()).noneMatch(name -> name.contains("."));
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
    assertThat(dto.tableLabels).containsEntry("Add", "Добавить");
    assertThat(dto.atRight).contains("Help");
    assertThat(dto.autoCommandBar).containsKey("CatalogObject");
    assertThat(dto.tableCommandBar).containsKey("ObjectTablePart");
    assertThat(dto.platformVersion).isEqualTo(StandardCommandLabels.platformVersion());
  }

  @Test
  void панельСправочникаНачинаетсяКнопкойПоУмолчанию() {
    List<FormStandardCommandDto> buttons = StandardCommandLabels.ofAutoCommandBar().get("CatalogObject");

    assertThat(buttons).extracting(button -> button.command)
      .containsExactly("WriteAndClose", "Write", "Help");
    assertThat(buttons.get(0).defaultButton).isTrue();
  }

  /** У отчёта и обработки платформа своих кнопок в панель не кладёт: слева остаются только свои. */
  @Test
  void панелиОтчётаИОбработкиДержатОднуСправку() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("ReportObject"))
      .extracting(button -> button.command)
      .containsExactly("Help");
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("DataProcessorObject"))
      .extracting(button -> button.command)
      .containsExactly("Help");
  }

  @Test
  void панельТабличнойЧастиНачинаетсяДобавлением() {
    List<FormStandardCommandDto> buttons = StandardCommandLabels.ofTableCommandBar().get("ObjectTablePart");

    assertThat(buttons).first().extracting(button -> button.command).isEqualTo("Add");
    assertThat(buttons).hasSize(3);
  }

  /** У списка платформа кладёт в панель таблицы одну кнопку, и та нарисована значком. */
  @Test
  void панельТаблицыСпискаДержитОдинЗначок() {
    List<FormStandardCommandDto> buttons = StandardCommandLabels.ofTableCommandBar().get("DynamicList");

    assertThat(buttons).hasSize(1);
    assertThat(buttons).first().extracting(button -> button.representation).isEqualTo("Picture");
  }

  /** Безымянную кнопку набора платформа рисует значком: без этого её было бы нечем показать. */
  @Test
  void безымяннаяКнопкаНабораНарисованаЗначком() {
    assertThat(StandardCommandLabels.ofTableCommandBar().values())
      .allSatisfy(buttons -> assertThat(buttons)
        .filteredOn(button -> button.command == null && button.label == null)
        .allMatch(button -> "Picture".equals(button.representation)));
  }

  @Test
  void неснятыйВидТаблицыНабораНеИмеет() {
    assertThat(StandardCommandLabels.ofTableCommandBar()).doesNotContainKey("ТакогоВидаНет");
  }

  /** Названную команду панели таблицы подписывает свой словарь, а не словарь команд формы. */
  @Test
  void командыПанелиТаблицыПодписаныСвоимСловарём() {
    assertThat(StandardCommandLabels.ofTableCommandBar().values())
      .allSatisfy(buttons -> assertThat(buttons)
        .filteredOn(button -> button.command != null)
        .allMatch(button -> StandardCommandLabels.ofTable().containsKey(button.command)));
  }

  @Test
  void панельДокументаНачинаетсяПроведением() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("DocumentObject"))
      .extracting(button -> button.command)
      .startsWith("PostAndClose", "Write", "Post");
  }

  /** Команду назвать вышло не у каждой кнопки: у безымянной остаётся снятая подпись. */
  @Test
  void кнопкаБезИмениКомандыОстаётсяСоСвоейПодписью() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("DocumentObject"))
      .filteredOn(button -> button.command == null)
      .extracting(button -> button.label)
      .containsExactly("Создать на основании");
  }

  /** Набор стоит перед записанными кнопками, кроме той, что на снимке встала за ними. */
  @Test
  void заПисаннымиКнопкамиСтоитТолькоСозданиеНаОсновании() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().values())
      .allSatisfy(buttons -> assertThat(buttons)
        .filteredOn(button -> Boolean.TRUE.equals(button.afterOwnButtons))
        .extracting(button -> button.label)
        .allMatch("Создать на основании"::equals));
  }

  /** Разбивка идёт по виду главного реквизита формы, а не по виду объекта-владельца. */
  @Test
  void видыНабораНазваныПоГлавномуРеквизиту() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().keySet())
      .allMatch(kind -> kind.endsWith("Object") || kind.endsWith("RecordManager")
        || "DynamicList".equals(kind));
  }

  /** Задача и бизнес-процесс: набор снят по донору, у которого своих команд у объекта нет. */
  @Test
  void панелиЗадачиИБизнесПроцессаСняты() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("TaskObject"))
      .extracting(button -> button.command)
      .containsExactly("Write", null);
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("BusinessProcessObject"))
      .extracting(button -> button.command)
      .containsExactly("StartAndClose", "WriteAndClose");
  }

  /** У формы списка платформа кладёт слева один значок, а справку держит справа. */
  @Test
  void панельСпискаДержитЗначокИСправку() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().get("DynamicList"))
      .extracting(button -> button.command)
      .containsExactly(null, "Help");
  }

  @Test
  void неснятыйВидНабораНеИмеет() {
    assertThat(StandardCommandLabels.ofAutoCommandBar()).doesNotContainKey("ТакогоВидаНет");
  }

  /** Названную команду набора подписывает словарь: без подписи кнопка вышла бы с именем команды. */
  @Test
  void командыНабораПодписаны() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().values())
      .allSatisfy(buttons -> assertThat(buttons)
        .filteredOn(button -> button.command != null)
        .allMatch(button -> StandardCommandLabels.ofForm().containsKey(button.command)));
  }

  /** Названная команда подписи в наборе не держит: она берётся из словаря подписей. */
  @Test
  void названнаяКомандаПодписьюВНабореНеДублируется() {
    assertThat(StandardCommandLabels.ofAutoCommandBar().values())
      .allSatisfy(buttons -> assertThat(buttons)
        .noneMatch(button -> button.command != null && button.label != null));
  }
}
