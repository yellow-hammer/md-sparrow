/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementDecl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Полный состав свойств у видов элементов формы: что вообще можно задать элементу и что подставит
 * платформа, если свойство не записано.
 *
 * <p>Набор снимается с модели формата, а не с конкретной формы: в файле лежат только изменённые
 * свойства, а редактору нужен весь список - как в палитре свойств конфигуратора.
 */
public final class FormItemPropertyDictionary {

  private static final String JAXB_PKG_PREFIX = "io.github.yellowhammer.designerxml.jaxb.";
  private static final String LOGFORM_PKG = ".v8_3_xcf_logform";

  /** Узлы, которые платформа держит рядом с {@code ChildItems}, а не внутри него. */
  private static final List<String> ATTACHED_ITEMS = List.of(
    "AutoCommandBar", "ContextMenu", "ExtendedTooltip",
    "SearchStringAddition", "SearchControlAddition", "ViewStatusAddition");

  /**
   * Умолчания, которые платформа применяет не так, как объявляет схема: {@code вид.свойство -> значение}.
   *
   * <p>Конфигуратор пишет в файл только изменённые свойства, поэтому записанное значение умолчанием
   * быть не может. У обычной группы {@code Group} схема объявляет {@code Vertical}, но именно его
   * конфигуратор и пишет, а {@code HorizontalIfPossible} не пишет никогда: горизонтальная группировка
   * и есть умолчание. У страницы наоборот, там схема права.
   */
  private static final Map<String, String> PLATFORM_DEFAULTS = Map.of("UsualGroup.Group", "HorizontalIfPossible");

  /** Узлы состава, а не свойства: они видны в дереве элементов, в палитре им делать нечего. */
  private static final List<String> STRUCTURE_NODES = new ArrayList<>(ATTACHED_ITEMS);

  static {
    STRUCTURE_NODES.add("ChildItems");
    STRUCTURE_NODES.add("Events");
  }

  private FormItemPropertyDictionary() {
  }

  /** Свойство вида элемента формы. */
  public static final class FormItemPropertyDto {
    /** Имя узла в XML: {@code Visible}, {@code DataPath}. */
    public String name;
    /** Вид значения: {@code boolean}, {@code number}, {@code string}, {@code enum}, {@code localString}, {@code complex}. */
    public String kind;
    /** Значение по умолчанию, если платформа его объявляет. */
    public String defaultValue;
    /** Допустимые значения перечисления. */
    public List<String> values;
    /** Свойство записано атрибутом стартового тега, а не отдельным узлом ({@code name}, {@code id}). */
    public Boolean attribute;
  }

  /**
   * Свойство вида элемента формы по имени узла.
   *
   * @param dictionary словарь из {@link #forVersion(SchemaVersion)}
   * @param itemType   вид элемента: {@code InputField}, {@code UsualGroup}
   * @param name       имя узла свойства
   */
  public static Optional<FormItemPropertyDto> find(
    Map<String, List<FormItemPropertyDto>> dictionary,
    String itemType,
    String name) {
    return dictionary.getOrDefault(itemType, List.of()).stream()
      .filter(p -> p.name.equals(name))
      .findFirst();
  }

  /**
   * Словарь: вид элемента формы -> его свойства.
   *
   * @param version версия формата выгрузки
   * @return отсортированный по виду элемента словарь
   */
  public static Map<String, List<FormItemPropertyDto>> forVersion(SchemaVersion version) {
    Map<String, Class<?>> kinds = itemClasses(version);
    // Узел, который сам является видом элемента, - это состав формы, а не свойство: он виден в дереве.
    List<String> structure = new ArrayList<>(kinds.keySet());
    structure.addAll(STRUCTURE_NODES);
    Map<String, List<FormItemPropertyDto>> out = new TreeMap<>();
    for (Map.Entry<String, Class<?>> kind : kinds.entrySet()) {
      List<FormItemPropertyDto> properties = properties(kind.getValue(), structure);
      for (FormItemPropertyDto property : properties) {
        String platform = PLATFORM_DEFAULTS.get(kind.getKey() + "." + property.name);
        if (platform != null) {
          property.defaultValue = platform;
        }
      }
      out.put(kind.getKey(), properties);
    }
    return out;
  }

  /**
   * Виды элементов формы и их классы модели.
   *
   * <p>Соответствие «имя узла -> класс» объявлено в {@code ObjectFactory}: элементы внутри
   * {@code ChildItems} - подстановки одного абстрактного узла, поэтому по самому списку их не найти.
   */
  private static Map<String, Class<?>> itemClasses(SchemaVersion version) {
    Map<String, Class<?>> classes = new LinkedHashMap<>();
    Class<?> factory = logformClass(version, "ObjectFactory");
    for (Method method : factory.getMethods()) {
      XmlElementDecl decl = method.getAnnotation(XmlElementDecl.class);
      if (decl == null || method.getParameterCount() != 1) {
        continue;
      }
      Class<?> itemClass = method.getParameterTypes()[0];
      if (isFormItem(itemClass)) {
        classes.putIfAbsent(decl.name(), itemClass);
      }
    }
    // Командная панель, контекстное меню и подсказка лежат отдельными узлами рядом с ChildItems,
    // поэтому их классы берём по типу такого узла у самих элементов.
    for (Class<?> itemClass : List.copyOf(classes.values())) {
      for (Class<?> c = itemClass; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
        for (Field field : c.getDeclaredFields()) {
          XmlElement element = field.getAnnotation(XmlElement.class);
          if (element != null && ATTACHED_ITEMS.contains(element.name())) {
            classes.putIfAbsent(element.name(), field.getType());
          }
        }
      }
    }
    return classes;
  }

  private static boolean isFormItem(Class<?> candidate) {
    for (Class<?> c = candidate; c != null; c = c.getSuperclass()) {
      if ("FormItemBase".equals(c.getSimpleName())) {
        return true;
      }
    }
    return false;
  }

  private static Class<?> logformClass(SchemaVersion version, String simpleName) {
    String fqcn = JAXB_PKG_PREFIX + version.name().toLowerCase() + LOGFORM_PKG + "." + simpleName;
    try {
      return Class.forName(fqcn);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("нет класса " + fqcn, e);
    }
  }

  /** Свойства класса вместе с унаследованными: у элементов формы общая часть вынесена в базовые классы. */
  private static List<FormItemPropertyDto> properties(Class<?> itemClass, List<String> structure) {
    List<FormItemPropertyDto> out = new ArrayList<>();
    List<Class<?>> chain = new ArrayList<>();
    for (Class<?> c = itemClass; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
      chain.add(0, c);
    }
    for (Class<?> c : chain) {
      for (Field field : c.getDeclaredFields()) {
        FormItemPropertyDto property = property(field, structure);
        if (property != null && out.stream().noneMatch(p -> p.name.equals(property.name))) {
          out.add(property);
        }
      }
    }
    return out;
  }

  private static FormItemPropertyDto property(Field field, List<String> structure) {
    XmlElement element = field.getAnnotation(XmlElement.class);
    XmlAttribute attribute = field.getAnnotation(XmlAttribute.class);
    if (element == null && attribute == null) {
      return null;
    }
    String name = element != null ? element.name() : attribute.name();
    if (structure.contains(name)) {
      return null;
    }
    FormItemPropertyDto dto = new FormItemPropertyDto();
    dto.name = name;
    if ("##default".equals(dto.name)) {
      dto.name = field.getName();
    }
    if (element == null) {
      dto.attribute = Boolean.TRUE;
    }
    Class<?> type = field.getType();
    dto.kind = kind(type);
    if (type.isEnum()) {
      dto.values = new ArrayList<>();
      for (Object constant : type.getEnumConstants()) {
        dto.values.add(FormEnumText.of((Enum<?>) constant));
      }
    }
    if (element != null && !" ".equals(element.defaultValue())) {
      dto.defaultValue = element.defaultValue();
    }
    return dto;
  }

  private static String kind(Class<?> type) {
    if (type.isEnum()) {
      return "enum";
    }
    if (type.equals(Boolean.class) || type.equals(boolean.class)) {
      return "boolean";
    }
    if (type.equals(BigDecimal.class) || Number.class.isAssignableFrom(type)) {
      return "number";
    }
    if (type.equals(String.class)) {
      return "string";
    }
    // Заголовок подсказки и надписи - та же строка на языках, но с признаком форматирования.
    if ("FormattedStringType".equals(type.getSimpleName())) {
      return "formattedString";
    }
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      if ("LocalStringType".equals(c.getSimpleName())) {
        return "localString";
      }
    }
    return "complex";
  }
}
