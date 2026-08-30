/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Подписи значений перечислимых свойств. */
class EnumValueLabelsTest {

  @Test
  void подписаныВсеЗначенияВсехПоддержанныхФорматов() {
    var unnamed = new TreeSet<String>();
    for (SchemaVersion version : SchemaVersion.values()) {
      for (Map.Entry<String, List<String>> property : MdObjectPropertyEnums.forVersion(version).entrySet()) {
        for (String value : property.getValue()) {
          if (EnumValueLabels.labelOf(value).equals(value)) {
            unnamed.add(property.getKey() + " = " + value);
          }
        }
      }
    }

    assertThat(unnamed).as("значения без подписи").isEmpty();
  }

  @Test
  void режимСовместимостиПодписываетсяПравилом() {
    // Их три десятка, и с каждой несовместимой версией платформы прибавляется.
    assertThat(EnumValueLabels.labelOf("VERSION_8_3_27")).isEqualTo("Версия 8.3.27");
    assertThat(EnumValueLabels.labelOf("VERSION_8_5_1")).isEqualTo("Версия 8.5.1");
    assertThat(EnumValueLabels.labelOf("VERSION_8_2")).isEqualTo("Версия 8.2");
  }

  @Test
  void уСвойстваЗначениеМожетНазыватьсяИначе() {
    String property = "chartOfCalculationTypes.dependenceOnCalculationTypes";

    assertThat(EnumValueLabels.labelOf(property, "DONT_USE")).isEqualTo("Не зависит");
    assertThat(EnumValueLabels.labelOf("DONT_USE")).isEqualTo("Не использовать");
  }

  @Test
  void неизвестноеЗначениеОстаётсяСобой() {
    assertThat(EnumValueLabels.labelOf("СОВСЕМ_НОВОЕ")).isEqualTo("СОВСЕМ_НОВОЕ");
    assertThat(EnumValueLabels.labelOf(null)).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void uiLabelsCoverRightsGroupsAndStandardCommands() {
    // Эти подписи задаёт платформа, а не XSD: потребитель берёт их у библиотеки
    org.assertj.core.api.Assertions.assertThat(UiLabels.rights())
      .containsEntry("Read", "Чтение")
      .containsEntry("InteractiveClearDeletionMark", "Интерактивное снятие пометки удаления");
    org.assertj.core.api.Assertions.assertThat(UiLabels.commandGroups())
      .containsEntry("NavigationPanelSeeAlso", "Панель навигации: См. также")
      .containsEntry("ActionsPanelCreate", "Панель действий: Создать");
    org.assertj.core.api.Assertions.assertThat(UiLabels.objectStandardCommands())
      .containsEntry("OpenList", "Открыть список");
    // Ссылочный тип подписывается тем же видом: Catalog и CatalogRef - «Справочник»
    org.assertj.core.api.Assertions.assertThat(UiLabels.objectKinds())
      .containsEntry("Catalog", "Справочник")
      .containsEntry("CatalogRef", "Справочник")
      .containsEntry("InformationRegister", "Регистр сведений");
  }
}
