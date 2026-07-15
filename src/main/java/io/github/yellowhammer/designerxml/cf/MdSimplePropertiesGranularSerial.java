/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

/**
 * Точечные замены прямых дочерних элементов {@code Properties} перечисления, константы и общего модуля.
 */
final class MdSimplePropertiesGranularSerial {

  private MdSimplePropertiesGranularSerial() {
  }

  static void appendEnumScalarChanges(
    MdEnumPropertiesDto b,
    MdEnumPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.bool("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("ChoiceMode", b.choiceMode, i.choiceMode);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
  }

  static void appendConstantScalarChanges(
    MdConstantPropertiesDto b,
    MdConstantPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    if (!MdFlatDtoSupport.equalsFlat(b.type, i.type, false)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Type", MdTypeDescriptionSerial.typeElement("Type", i.type)));
    }
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("DefaultForm", b.defaultForm, i.defaultForm);
    c.localStringRu("ExtendedPresentation", b.extendedPresentationRu, i.extendedPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.bool("PasswordMode", b.passwordMode, i.passwordMode);
    c.localStringRu("Format", b.formatRu, i.formatRu);
    c.localStringRu("EditFormat", b.editFormatRu, i.editFormatRu);
    c.localStringRu("ToolTip", b.toolTipRu, i.toolTipRu);
    c.bool("MarkNegatives", b.markNegatives, i.markNegatives);
    c.text("Mask", b.mask, i.mask);
    c.bool("MultiLine", b.multiLine, i.multiLine);
    c.bool("ExtendedEdit", b.extendedEdit, i.extendedEdit);
    c.enumText("FillChecking", b.fillChecking, i.fillChecking);
    c.enumText("ChoiceFoldersAndItems", b.choiceFoldersAndItems, i.choiceFoldersAndItems);
    c.enumText("QuickChoice", b.quickChoice, i.quickChoice);
    c.text("ChoiceForm", b.choiceForm, i.choiceForm);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.text("ValueManagerModule", b.valueManagerModule, i.valueManagerModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
  }

  static void appendCommonModuleScalarChanges(
    MdCommonModulePropertiesDto b,
    MdCommonModulePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("Global", b.global, i.global);
    c.bool("ClientManagedApplication", b.clientManagedApplication, i.clientManagedApplication);
    c.bool("Server", b.server, i.server);
    c.bool("ExternalConnection", b.externalConnection, i.externalConnection);
    c.bool("ClientOrdinaryApplication", b.clientOrdinaryApplication, i.clientOrdinaryApplication);
    c.bool("Client", b.client, i.client);
    c.bool("ServerCall", b.serverCall, i.serverCall);
    c.bool("Privileged", b.privileged, i.privileged);
    c.enumText("ReturnValuesReuse", b.returnValuesReuse, i.returnValuesReuse);
  }
}
