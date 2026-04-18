/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Структура объекта метаданных для дерева IDE.
 */
public final class MdObjectStructureDto {

  public String kind;
  public String internalName;
  public List<MdNodeDto> attributes;
  public List<MdTabularSectionDto> tabularSections;
  public List<String> forms;
  public List<String> commands;
  public List<String> templates;
  public List<String> values;
  public List<String> columns;
  public List<String> accountingFlags;
  public List<String> extDimensionAccountingFlags;
  public List<String> dimensions;
  public List<String> resources;
  public List<String> recalculations;
  public List<String> addressingAttributes;
  public List<String> operations;
  public List<String> urlTemplates;
  public List<String> channels;
  public List<String> tables;
  public List<String> cubes;
  public List<String> functions;

  public MdObjectStructureDto() {
    this.attributes = new ArrayList<>();
    this.tabularSections = new ArrayList<>();
    this.forms = new ArrayList<>();
    this.commands = new ArrayList<>();
    this.templates = new ArrayList<>();
    this.values = new ArrayList<>();
    this.columns = new ArrayList<>();
    this.accountingFlags = new ArrayList<>();
    this.extDimensionAccountingFlags = new ArrayList<>();
    this.dimensions = new ArrayList<>();
    this.resources = new ArrayList<>();
    this.recalculations = new ArrayList<>();
    this.addressingAttributes = new ArrayList<>();
    this.operations = new ArrayList<>();
    this.urlTemplates = new ArrayList<>();
    this.channels = new ArrayList<>();
    this.tables = new ArrayList<>();
    this.cubes = new ArrayList<>();
    this.functions = new ArrayList<>();
  }

  public static final class MdNodeDto {
    public String name;
    public String synonymRu;
    public String comment;

    public MdNodeDto() {
    }

    public MdNodeDto(String name, String synonymRu, String comment) {
      this.name = name;
      this.synonymRu = synonymRu;
      this.comment = comment;
    }
  }

  public static final class MdTabularSectionDto {
    public String name;
    public String synonymRu;
    public String comment;
    public List<MdNodeDto> attributes;

    public MdTabularSectionDto() {
      this.attributes = new ArrayList<>();
    }
  }
}
