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

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Сквозные тесты сборки графа метаданных на фикстуре {@code ssl_3_1}. */
class ProjectMetadataGraphBuilderTest {

  private static ProjectMetadataGraphDto graph;

  @BeforeAll
  static void buildOnce() throws Exception {
    graph = ProjectMetadataGraphBuilder.build(Ssl31SubmodulePaths.projectRoot());
  }

  @Test
  void graphHeaderMatchesTreeHeader() {
    assertThat(graph.projectRoot()).isNotBlank();
    assertThat(graph.mainSchemaVersion()).isNotBlank();
    assertThat(graph.mainSchemaVersionFlag()).matches("V\\d+(_\\d+)+");
    assertThat(graph.nodeCount()).isEqualTo(graph.nodes().size());
    assertThat(graph.edgeCount()).isEqualTo(graph.edges().size());
    assertThat(graph.nodes()).isNotEmpty();
    assertThat(graph.edges()).isNotEmpty();
  }

  @Test
  void everyEdgeReferencesKnownKindAndCardinality() {
    Set<String> validKinds = java.util.Arrays.stream(RelationKind.values())
      .map(RelationKind::wireName)
      .collect(Collectors.toSet());
    for (ProjectMetadataGraphDto.EdgeDto edge : graph.edges()) {
      assertThat(edge.kind()).isIn(validKinds);
      assertThat(edge.cardinality()).matches("\\d+\\.\\.(\\d+|\\*)");
      assertThat(edge.via()).isNotNull();
    }
  }

  @Test
  void demoCatalogHasOwnerAndSubsystemMembership() {
    var demoBank = nodeByKey("Catalog._ДемоБанковскиеСчета");
    assertThat(demoBank.objectType()).isEqualTo("Catalog");
    assertThat(demoBank.synonymRu()).contains("Банковские счета");
    var ownersEdges = edgesFrom("Catalog._ДемоБанковскиеСчета", RelationKind.CATALOG_OWNERS);
    assertThat(ownersEdges)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("Catalog._ДемоОрганизации", "Catalog._ДемоКонтрагенты");
  }

  @Test
  void demoDocumentLinksToRegisterRecordsAndBasedOn() {
    var keys = edgesFrom("Document._ДемоПеремещениеТоваров", RelationKind.DOCUMENT_POSTING_REGISTERS)
      .stream()
      .map(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .toList();
    assertThat(keys).contains("AccumulationRegister._ДемоОстаткиТоваровВМестахХранения");
    var basedOn = edgesFrom("Document._ДемоПеремещениеТоваров", RelationKind.DOCUMENT_BASED_ON);
    assertThat(basedOn)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("Document._ДемоПоступлениеТоваров");
  }

  @Test
  void registerDimensionTypesPickUpDefinedTypeAndCharacteristic() {
    var dimensionEdges = edgesFrom("InformationRegister.ДатыЗапретаИзменения", RelationKind.REGISTER_DIMENSION_TYPE);
    assertThat(dimensionEdges)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("ChartOfCharacteristicTypes.РазделыДатЗапретаИзменения", "DefinedType.АдресатЗапретаИзменения");
  }

  @Test
  void subsystemMembershipFlowsBothWaysAsEdgeAndNodeAttribute() {
    var membershipEdges = edgesFrom("Subsystem.Организации", RelationKind.SUBSYSTEM_MEMBERSHIP);
    assertThat(membershipEdges)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("CommonModule.ОрганизацииСервер", "CommonModule.ОрганизацииПереопределяемый");
    var serverModule = nodeByKey("CommonModule.ОрганизацииСервер");
    assertThat(serverModule.subsystemKeys()).contains("Subsystem.Организации");
  }

  @Test
  void functionalOptionEmitsLocationAndContentEdges() {
    var location = edgesFrom("FunctionalOption.ИспользоватьДополнительныйЯзык1", RelationKind.FUNCTIONAL_OPTION_LOCATION);
    assertThat(location)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("Constant.ИспользоватьДополнительныйЯзык1");
    var affected = edgesFrom("FunctionalOption.ИспользоватьДополнительныйЯзык1", RelationKind.FUNCTIONAL_OPTION_AFFECTED);
    assertThat(affected)
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("CommonAttribute.НаименованиеЯзык1", "CommonAttribute.КомментарийЯзык1");
  }

  @Test
  void rolesContributeRoleObjectRightsEdges() {
    var roleNodes = graph.nodes().stream()
      .filter(n -> "Role".equals(n.objectType()))
      .toList();
    assertThat(roleNodes).isNotEmpty();
    long roleEdges = graph.edges().stream()
      .filter(e -> RelationKind.ROLE_OBJECT_RIGHTS.wireName().equals(e.kind()))
      .count();
    assertThat(roleEdges).isGreaterThan(0);
  }

  @Test
  void eventSubscriptionLinksToSourceAndHandler() {
    var subscriptionNode = nodeByKey("EventSubscription.ЗаписатьВерсиюОбъекта");
    assertThat(subscriptionNode.objectType()).isEqualTo("EventSubscription");

    var sourceEdges = edgesFrom("EventSubscription.ЗаписатьВерсиюОбъекта", RelationKind.SUBSCRIPTION_SOURCE);
    assertThat(sourceEdges)
      .as("Source должен содержать ссылку на DefinedType.ВерсионируемыеДанныеОбъект")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("DefinedType.ВерсионируемыеДанныеОбъект");

    var handlerEdges = edgesFrom("EventSubscription.ЗаписатьВерсиюОбъекта", RelationKind.SUBSCRIPTION_HANDLER);
    assertThat(handlerEdges)
      .as("Handler должен вести на общий модуль-обработчик")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("CommonModule.ВерсионированиеОбъектовСобытия");
  }

  @Test
  void eventSubscriptionWithDirectObjectTypesLinksToDocumentNodes() {
    var subscriptionNode = nodeByKey("EventSubscription._ДемоЗаписатьВерсиюДокумента");
    assertThat(subscriptionNode.objectType()).isEqualTo("EventSubscription");

    var sourceEdges = edgesFrom("EventSubscription._ДемоЗаписатьВерсиюДокумента", RelationKind.SUBSCRIPTION_SOURCE);
    assertThat(sourceEdges)
      .as("Source с cfg:DocumentObject.X должен вести на Document-узлы")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("Document._ДемоСчетНаОплатуПокупателю", "Document._ДемоПоступлениеТоваров");
  }

  @Test
  void scheduledJobLinksToHandlerModule() {
    var jobNode = nodeByKey("ScheduledJob.ОбновлениеАгрегатов");
    assertThat(jobNode.objectType()).isEqualTo("ScheduledJob");

    var handlerEdges = edgesFrom("ScheduledJob.ОбновлениеАгрегатов", RelationKind.SCHEDULED_JOB_HANDLER);
    assertThat(handlerEdges)
      .as("MethodName должен вести на CommonModule")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("CommonModule.УправлениеИтогамиИАгрегатамиСлужебный");
  }

  @Test
  void dataProcessorAttributesYieldTypeCompositeEdges() {
    nodeByKey("DataProcessor.ОценкаПроизводительности"); // бросает AssertionError, если узел отсутствует
    var typeEdges = edgesFrom("DataProcessor.ОценкаПроизводительности", RelationKind.TYPE_COMPOSITE);
    assertThat(typeEdges)
      .as("DataProcessor должен иметь typeComposite-рёбра от типизированных реквизитов")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("Catalog.КлючевыеОперации");
  }

  @Test
  void commonCommandParameterTypeLinksToObjects() {
    var cmdNode = nodeByKey("CommonCommand._ДемоОстаткиТоваровВМестахХранения");
    assertThat(cmdNode.objectType()).isEqualTo("CommonCommand");

    var paramEdges = edgesFrom("CommonCommand._ДемоОстаткиТоваровВМестахХранения",
      RelationKind.COMMAND_PARAMETER_TYPE);
    assertThat(paramEdges)
      .as("CommandParameterType должен давать рёбра на Document-объекты")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .contains("Document._ДемоПеремещениеТоваров", "Document._ДемоПоступлениеТоваров");
  }

  @Test
  void accountingRegisterLinksToChartOfAccounts() {
    var regNode = nodeByKey("AccountingRegister._ДемоЖурналПроводокБухгалтерскогоУчета");
    assertThat(regNode.objectType()).isEqualTo("AccountingRegister");

    var edges = edgesFrom("AccountingRegister._ДемоЖурналПроводокБухгалтерскогоУчета",
      RelationKind.REGISTER_CHART_OF_ACCOUNTS);
    assertThat(edges)
      .as("AccountingRegister должен иметь ровно одну связь registerChartOfAccounts")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("ChartOfAccounts._ДемоОсновной");
  }

  @Test
  void calculationRegisterLinksToChartOfCalculationTypes() {
    var regNode = nodeByKey("CalculationRegister._ДемоОсновныеНачисления");
    assertThat(regNode.objectType()).isEqualTo("CalculationRegister");

    var edges = edgesFrom("CalculationRegister._ДемоОсновныеНачисления",
      RelationKind.REGISTER_CHART_OF_CALCULATION_TYPES);
    assertThat(edges)
      .as("CalculationRegister должен иметь ровно одну связь registerChartOfCalculationTypes")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("ChartOfCalculationTypes._ДемоОсновныеНачисления");
  }

  @Test
  void chartOfAccountsLinksToExtDimensionTypes() {
    var coaNode = nodeByKey("ChartOfAccounts._ДемоОсновной");
    assertThat(coaNode.objectType()).isEqualTo("ChartOfAccounts");

    var edges = edgesFrom("ChartOfAccounts._ДемоОсновной", RelationKind.CHART_OF_ACCOUNTS_EXT_DIMENSIONS);
    assertThat(edges)
      .as("ChartOfAccounts должен иметь связь chartOfAccountsExtDimensions на ПВХ-виды субконто")
      .extracting(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .containsExactly("ChartOfCharacteristicTypes._ДемоВидыСубконто");
  }

  @Test
  void extensionSourcesAreIncludedInNodes() {
    var extensions = graph.nodes().stream()
      .map(ProjectMetadataGraphDto.NodeDto::sourceId)
      .filter(id -> !"main".equals(id) && !id.startsWith("external-"))
      .collect(Collectors.toSet());
    assertThat(extensions).isNotEmpty();
  }

  @Test
  void chartOfCharacteristicTypesLinksToCharacteristicExtValues() {
    var edges = edgesFrom("ChartOfCharacteristicTypes.ДополнительныеРеквизитыИСведения",
      RelationKind.CHARACTERISTIC_EXT_VALUES);
    assertThat(edges)
      .as("ДополнительныеРеквизитыИСведения должен иметь ссылку на справочник значений")
      .hasSize(1);
    assertThat(edges.get(0).targetKey())
      .startsWith("Catalog.");
  }

  private static ProjectMetadataGraphDto.NodeDto nodeByKey(String key) {
    return graph.nodes().stream()
      .filter(n -> key.equals(n.key()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("missing node " + key));
  }

  private static List<ProjectMetadataGraphDto.EdgeDto> edgesFrom(String sourceKey, RelationKind kind) {
    return graph.edges().stream()
      .filter(e -> sourceKey.equals(e.sourceKey()) && kind.wireName().equals(e.kind()))
      .toList();
  }
}
