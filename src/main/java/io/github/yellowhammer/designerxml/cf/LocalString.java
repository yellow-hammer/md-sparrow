/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Свойство хранится по строке на язык.
 *
 * <p>Синоним, представления, пояснение и подсказка записаны парами язык-значение,
 * а не одной строкой. Контракт несёт текст на языке конфигурации, и по этой
 * пометке чтение и запись понимают, что за строкой стоит язык. Раньше на это
 * указывал суффикс {@code Ru} в имени поля, но имя обещало русский, которого у
 * конфигурации может и не быть.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LocalString {
}
