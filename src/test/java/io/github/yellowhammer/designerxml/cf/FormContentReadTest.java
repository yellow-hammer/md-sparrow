/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Чтение содержимого управляемой формы на реальных формах выгрузки. */
class FormContentReadTest {

  private static Path form(String name) {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path path = Path.of(root, "src", "cf", "Catalogs", "Валюты", "Forms", name, "Ext", "Form.xml");
    assertThat(path).exists();
    return path;
  }

  private static FormItemDto byName(List<FormItemDto> items, String name) {
    return items.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow(
      () -> new AssertionError("нет элемента " + name + " среди " + items.stream().map(i -> i.name).toList()));
  }

  @Test
  void читаетДеревоЭлементовВПорядкеФайла() throws Exception {
    FormContentDto dto = FormContentRead.read(form("ФормаЭлемента"), SchemaVersion.V2_20);

    assertThat(dto.items).extracting(i -> i.type).containsExactly(
      "AutoCommandBar", "UsualGroup", "UsualGroup", "LabelDecoration");

    FormItemDto header = byName(dto.items, "ГруппаШапка");
    assertThat(header.type).isEqualTo("UsualGroup");
    assertThat(header.title).isEqualTo("Шапка");
    assertThat(header.group).isEqualTo("Vertical");
    // Расширенная подсказка - такой же элемент формы, как в дереве конфигуратора, и идёт перед вложенными.
    assertThat(header.items).extracting(i -> i.type + ":" + i.name).containsExactly(
      "ExtendedTooltip:ГруппаШапкаРасширеннаяПодсказка",
      "InputField:НаименованиеПолное",
      "UsualGroup:ГруппаКодНаименование");

    FormItemDto code = byName(byName(header.items, "ГруппаКодНаименование").items, "Код");
    assertThat(code.type).isEqualTo("InputField");
    assertThat(code.dataPath).isEqualTo("Объект.Code");
    assertThat(code.width).isEqualTo("3");
    assertThat(code.events).extracting(e -> e.name + "=" + e.handler).containsExactly("OnChange=КодПриИзменении");
  }

  @Test
  void читаетДанныеФормы() throws Exception {
    FormContentDto dto = FormContentRead.read(form("ФормаЭлемента"), SchemaVersion.V2_20);

    assertThat(dto.events).extracting(e -> e.name + "=" + e.handler)
      .containsExactly("NotificationProcessing=ОбработкаОповещения", "OnCreateAtServer=ПриСозданииНаСервере");
    assertThat(dto.attributes).extracting(a -> a.name)
      .containsExactly("Объект", "ФормыВводаПрописей", "КодыВалютЗагружаемыхИзИнтернета", "ПредставлениеФормулы");
    assertThat(dto.attributes.get(0).main).isTrue();
    assertThat(dto.attributes.get(0).type.types).contains("cfg:CatalogObject.Валюты");
    assertThat(dto.commands).singleElement().satisfies(c -> {
      assertThat(c.name).isEqualTo("ПараметрыПрописиВалютыНаДругихЯзыках");
      assertThat(c.title).isEqualTo("На других языках...");
      assertThat(c.action).isEqualTo("ПараметрыПрописиВалютыНаДругихЯзыках");
    });
  }

  /** Колонки динамического списка идут по полям основной таблицы, а не по реквизитам формы. */
  @Test
  void читаетОсновнуюТаблицуДинамическогоСписка() throws Exception {
    FormContentDto dto = FormContentRead.read(form("ФормаСписка"), SchemaVersion.V2_20);

    assertThat(dto.attributes)
      .filteredOn(a -> "Список".equals(a.name))
      .singleElement()
      .satisfies(a -> assertThat(a.mainTable).isEqualTo("Catalog.Валюты"));
  }

  /** У обычного реквизита основной таблицы нет: её заводит только динамический список. */
  @Test
  void обычныйРеквизитОстаётсяБезОсновнойТаблицы() throws Exception {
    FormContentDto dto = FormContentRead.read(form("ФормаЭлемента"), SchemaVersion.V2_20);

    assertThat(dto.attributes).allSatisfy(a -> assertThat(a.mainTable).isNull());
  }

  @Test
  void читаетТаблицуИКоманднуюПанель() throws Exception {
    FormContentDto dto = FormContentRead.read(form("ФормаСписка"), SchemaVersion.V2_20);

    FormItemDto table = byName(dto.items, "Валюты");
    assertThat(table.type).isEqualTo("Table");
    assertThat(table.dataPath).isEqualTo("Список");
    assertThat(table.items).extracting(i -> i.type)
      .contains("ContextMenu", "AutoCommandBar", "SearchStringAddition", "InputField", "LabelField");

    FormItemDto rate = byName(table.items, "Курс");
    assertThat(rate.dataPath).isEqualTo("Список.Курс");

    FormItemDto commandBar = byName(dto.items, "КоманднаяПанель");
    assertThat(commandBar.type).isEqualTo("CommandBar");
    assertThat(byName(commandBar.items, "Создать").type).isEqualTo("Popup");
    assertThat(dto.attributes).extracting(a -> a.name).containsExactly("Список");
  }

  /** Модель формы собрана для каждого формата: иначе форма читается не везде, а выборочно. */
  @Test
  void модельФормыЕстьВоВсехВерсиях() throws Exception {
    for (SchemaVersion version : SchemaVersion.values()) {
      String formClass = "io.github.yellowhammer.designerxml.jaxb."
        + version.name().toLowerCase() + ".v8_3_xcf_logform.Form";
      assertThat(Class.forName(formClass)).describedAs(version.name()).isNotNull();
    }
  }

  /** Форма пишется с теми же пространствами имён, что у конфигуратора: своё - по умолчанию, без ns14. */
  @Test
  void пишетПространстваИмёнКакПлатформа() throws Exception {
    Object jaxb = DesignerXml.read(form("ФормаЭлемента"), SchemaVersion.V2_20);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DesignerXml.marshal(SchemaVersion.V2_20, jaxb, out, WriteOptions.defaults());

    String xml = out.toString(java.nio.charset.StandardCharsets.UTF_8);
    String root = xml.substring(xml.indexOf("<Form"), xml.indexOf('>', xml.indexOf("<Form")) + 1);
    assertThat(root).contains("xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"");
    assertThat(root).contains("xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"");
    assertThat(root).contains("xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\"");
    assertThat(root).contains("xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\"");
  }

  /**
   * Модель JAXB покрывает форму целиком: после чтения и записи в файле нет пропавших узлов.
   * Значения по умолчанию платформа не пишет, а JAXB пишет, поэтому сверяем только пропажи.
   */
  @Test
  void модельНеТеряетУзлыФормы() throws Exception {
    for (String name : List.of("ФормаСписка", "ФормаЭлемента")) {
      Path path = form(name);
      Object jaxb = DesignerXml.read(path, SchemaVersion.V2_20);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DesignerXml.marshal(SchemaVersion.V2_20, jaxb, out, WriteOptions.defaults());

      Map<String, Integer> before = nodeCounts(Files.readAllBytes(path));
      Map<String, Integer> after = nodeCounts(out.toByteArray());
      for (Map.Entry<String, Integer> node : before.entrySet()) {
        assertThat(after.getOrDefault(node.getKey(), 0))
          .describedAs("%s: %s", name, node.getKey())
          .isGreaterThanOrEqualTo(node.getValue());
      }
    }
  }

  private static Map<String, Integer> nodeCounts(byte[] xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    Map<String, Integer> counts = new LinkedHashMap<>();
    walk(doc.getDocumentElement(), "", counts);
    return counts;
  }

  private static void walk(Element element, String prefix, Map<String, Integer> counts) {
    String path = prefix + "/" + element.getLocalName();
    counts.merge(path, 1, Integer::sum);
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element el) {
        walk(el, path, counts);
      }
    }
  }
}
