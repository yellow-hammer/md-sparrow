/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
  public String listPresentationRu;
  public String extendedListPresentationRu;
  public String explanationRu;
  public String additionalIndexes;
  /** Регистрируемые документы: ссылки вида {@code Document.ИмяДокумента}. */
  public List<String> registeredDocuments;

  public MdDocumentJournalPropertiesDto() {
    this.registeredDocuments = new ArrayList<>();
  }
}
