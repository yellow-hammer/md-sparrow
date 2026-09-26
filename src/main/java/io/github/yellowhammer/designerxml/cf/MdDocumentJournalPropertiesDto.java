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

import java.util.ArrayList;
import java.util.List;

/**
 * Поля {@code DocumentJournalProperties} для {@code cf-md-object-get/set}
 * ({@code kind=documentJournal}).
 * Enum-значения — имена Java-констант ({@code ADOPTED}).
 */
public final class MdDocumentJournalPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String defaultForm;
  public String auxiliaryForm;
  public String managerModule;
  public boolean includeHelpInContents;
  public String standardAttributesXml;
  @LocalString
  public String listPresentation;
  @LocalString
  public String extendedListPresentation;
  @LocalString
  public String explanation;
  public String additionalIndexes;
  /** Регистрируемые документы: ссылки вида {@code Document.ИмяДокумента}. */
  public List<String> registeredDocuments;

  public MdDocumentJournalPropertiesDto() {
    this.registeredDocuments = new ArrayList<>();
  }
}
