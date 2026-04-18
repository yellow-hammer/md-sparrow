/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class MdObjectXmlGenerator {
  private static final Pattern PROPERTIES_BLOCK = Pattern.compile("(?s)<Properties>.*?</Properties>");
  private static final Pattern SYNONYM_WITH_ITEMS = Pattern.compile(
    "(?s)<Synonym>\\s*<v8:item>.*?</v8:item>\\s*</Synonym>");

  private MdObjectXmlGenerator() {
  }

  static String generate(MdObjectAddType type, String objectName, SchemaVersion version)
    throws JAXBException, IOException {
    try {
      String pkgBase = version == SchemaVersion.V2_20
        ? "io.github.yellowhammer.designerxml.jaxb.v2_20"
        : "io.github.yellowhammer.designerxml.jaxb.v2_21";

      Class<?> objectFactoryClass = Class.forName(pkgBase + ".mdclasses.ObjectFactory");
      Object factory = objectFactoryClass.getDeclaredConstructor().newInstance();

      Object metaDataObject = invoke(factory, "createMetaDataObject");
      invoke(metaDataObject, "setVersion", version.metadataObjectVersionAttribute());

      Object objectNode = invoke(factory, "create" + type.configurationXmlTag());
      invoke(objectNode, "setUuid", GoldenUuid.from("newMdObject|" + version + "|" + type + "|" + objectName, "object.uuid"));

      fillInternalInfo(pkgBase, objectNode, type, objectName, version);
      fillProperties(factory, objectNode, type, objectName, pkgBase);
      fillChildObjects(factory, objectNode, type);

      invoke(metaDataObject, "set" + type.configurationXmlTag(), objectNode);
      @SuppressWarnings("unchecked")
      JAXBElement<?> root = (JAXBElement<?>) invoke(factory, "createMetaDataObject", metaDataObject);
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DesignerXml.marshal(version, root, buf, WriteOptions.defaults());
      String text = buf.toString(StandardCharsets.UTF_8);
      String withDeterministicUuids = DistinctUuidRewrite.remapDeterministic(
        text, "newMdObject|" + version + "|" + type + "|" + objectName);
      String normalized = GoldenXmlPostProcessor.normalizeMetaDataObjectXml(withDeterministicUuids, version);
      String withProfile = applyTypeProfile(type, normalized, objectName, version);
      try (ByteArrayInputStream in = new ByteArrayInputStream(withProfile.getBytes(StandardCharsets.UTF_8))) {
        DesignerXml.unmarshal(version, in);
      }
      return withProfile;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot generate JAXB object for " + type, e);
    }
  }

  private static void fillInternalInfo(
    String pkgBase,
    Object objectNode,
    MdObjectAddType type,
    String objectName,
    SchemaVersion version) throws ReflectiveOperationException {
    if (type.generatedTypeCategories().isEmpty()) {
      return;
    }
    String readablePkg = pkgBase + ".v8_3_xcf_readable";
    String enumsPkg = pkgBase + ".v8_3_xcf_enums";
    Class<?> internalInfoClass = Class.forName(readablePkg + ".InternalInfo");
    Class<?> generatedTypeClass = Class.forName(readablePkg + ".GeneratedType");
    Class<?> categoriesEnumClass = Class.forName(enumsPkg + ".TypeCategories");

    Object internalInfo = internalInfoClass.getDeclaredConstructor().newInstance();
    @SuppressWarnings("unchecked")
    java.util.List<Object> generatedTypeList =
      (java.util.List<Object>) invoke(internalInfo, "getGeneratedType");
    for (String category : type.generatedTypeCategories()) {
      Object gt = generatedTypeClass.getDeclaredConstructor().newInstance();
      invoke(gt, "setName", generatedTypeName(type, category, objectName));
      Object categoryEnum = resolveTypeCategoryEnum(categoriesEnumClass, category);
      invoke(gt, "setCategory", categoryEnum);
      String seed = "newMdObject|" + version + "|" + type + "|" + objectName + "|generated|" + category;
      invoke(gt, "setTypeId", GoldenUuid.from(seed, "typeId"));
      invoke(gt, "setValueId", GoldenUuid.from(seed, "valueId"));
      generatedTypeList.add(gt);
    }
    tryInvoke(objectNode, "setInternalInfo", internalInfo);
  }

  private static void fillProperties(
    Object factory,
    Object objectNode,
    MdObjectAddType type,
    String objectName,
    String pkgBase) throws ReflectiveOperationException {
    Object properties = invoke(factory, "create" + type.configurationXmlTag() + "Properties");
    tryInvoke(properties, "setName", objectName);
    trySetEmptySynonym(properties, pkgBase);
    tryInvoke(properties, "setComment", "");
    tryInvoke(properties, "setUseStandardCommands", Boolean.TRUE);
    tryInvoke(properties, "setQuickChoice", Boolean.FALSE);
    tryInvoke(properties, "setChoiceMode", "BothWays");
    invoke(objectNode, "setProperties", properties);
  }

  private static void trySetEmptySynonym(Object properties, String pkgBase) throws ReflectiveOperationException {
    for (Method m : properties.getClass().getMethods()) {
      if (!m.getName().equals("setSynonym") || m.getParameterCount() != 1) {
        continue;
      }
      Class<?> synType = m.getParameterTypes()[0];
      if (!synType.getName().equals(pkgBase + ".v8_1_data_core.LocalStringType")) {
        continue;
      }
      Class<?> itemType = Class.forName(pkgBase + ".v8_1_data_core.LocalStringItemType");
      Object ls = synType.getDeclaredConstructor().newInstance();
      Object item = itemType.getDeclaredConstructor().newInstance();
      invoke(item, "setLang", "ru");
      invoke(item, "setContent", "");
      @SuppressWarnings("unchecked")
      java.util.List<Object> items = (java.util.List<Object>) invoke(ls, "getItem");
      items.add(item);
      m.invoke(properties, ls);
      return;
    }
  }

  private static void fillChildObjects(Object factory, Object objectNode, MdObjectAddType type)
    throws ReflectiveOperationException {
    String methodName = "create" + type.configurationXmlTag() + "ChildObjects";
    try {
      Object child = invoke(factory, methodName);
      tryInvoke(objectNode, "setChildObjects", child);
    } catch (NoSuchMethodException ignored) {
      // not every object has ChildObjects
    }
  }

  private static Object invoke(Object target, String method, Object... args) throws ReflectiveOperationException {
    Method m = findMethod(target.getClass(), method, args);
    if (m == null) {
      throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
    }
    return m.invoke(target, args);
  }

  private static void tryInvoke(Object target, String method, Object... args) throws ReflectiveOperationException {
    Method m = findMethod(target.getClass(), method, args);
    if (m != null) {
      m.invoke(target, args);
    }
  }

  private static Method findMethod(Class<?> clazz, String name, Object[] args) {
    for (Method method : clazz.getMethods()) {
      if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
        continue;
      }
      Class<?>[] params = method.getParameterTypes();
      boolean ok = true;
      for (int i = 0; i < params.length; i++) {
        if (args[i] == null) {
          continue;
        }
        if (!wrap(params[i]).isAssignableFrom(args[i].getClass())) {
          ok = false;
          break;
        }
      }
      if (ok) {
        return method;
      }
    }
    return null;
  }

  private static Class<?> wrap(Class<?> c) {
    if (!c.isPrimitive()) {
      return c;
    }
    if (c == boolean.class) {
      return Boolean.class;
    }
    if (c == int.class) {
      return Integer.class;
    }
    if (c == long.class) {
      return Long.class;
    }
    if (c == double.class) {
      return Double.class;
    }
    if (c == float.class) {
      return Float.class;
    }
    if (c == short.class) {
      return Short.class;
    }
    if (c == byte.class) {
      return Byte.class;
    }
    if (c == char.class) {
      return Character.class;
    }
    return c;
  }

  private static String applyTypeProfile(
    MdObjectAddType type,
    String xml,
    String objectName,
    SchemaVersion version) {
    String withSynonym = SYNONYM_WITH_ITEMS.matcher(xml).replaceAll("<Synonym/>");
    return switch (type) {
      case CONSTANT -> replaceProperties(withSynonym, constantProperties(objectName));
      case DOCUMENT -> replaceProperties(withSynonym, documentProperties(objectName));
      case REPORT -> replaceProperties(withSynonym, reportProperties(objectName));
      case DATA_PROCESSOR -> replaceProperties(withSynonym, dataProcessorProperties(objectName));
      case ENUM -> replaceProperties(withSynonym, enumProperties(objectName));
      case EXTERNAL_DATA_SOURCE -> replaceProperties(withSynonym, externalDataSourceProperties(objectName));
      case TASK -> replaceProperties(withSynonym, taskProperties(objectName));
      case COMMON_MODULE -> replaceProperties(withSynonym, commonModuleProperties(objectName));
      case SESSION_PARAMETER -> replaceProperties(withSynonym, sessionParameterProperties(objectName));
      case EXCHANGE_PLAN -> normalizeExchangePlan(
        replaceProperties(withSynonym, exchangePlanProperties(objectName)),
        objectName,
        version);
      case COMMON_ATTRIBUTE -> replaceProperties(withSynonym, commonAttributeProperties(objectName));
      case COMMON_PICTURE -> replaceProperties(withSynonym, commonPictureProperties(objectName));
      case DOCUMENT_NUMERATOR -> replaceProperties(withSynonym, documentNumeratorProperties(objectName));
      case SUBSYSTEM -> replaceProperties(withSynonym, subsystemProperties(objectName));
      case CHART_OF_ACCOUNTS -> replaceProperties(withSynonym, chartOfAccountsProperties(objectName));
      case CHART_OF_CHARACTERISTIC_TYPES -> replaceProperties(
        withSynonym,
        chartOfCharacteristicTypesProperties(objectName));
      case CHART_OF_CALCULATION_TYPES -> replaceProperties(
        withSynonym,
        chartOfCalculationTypesProperties(objectName));
      default -> withSynonym;
    };
  }

  private static String replaceProperties(String xml, String replacement) {
    return PROPERTIES_BLOCK.matcher(xml).replaceFirst(replacement);
  }

  private static String constantProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<Type>
      \t\t\t\t<v8:Type>xs:string</v8:Type>
      \t\t\t\t<v8:StringQualifiers>
      \t\t\t\t\t<v8:Length>10</v8:Length>
      \t\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>
      \t\t\t\t</v8:StringQualifiers>
      \t\t\t</Type>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<DefaultForm/>
      \t\t\t<ExtendedPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<PasswordMode>false</PasswordMode>
      \t\t\t<Format/>
      \t\t\t<EditFormat/>
      \t\t\t<ToolTip/>
      \t\t\t<MarkNegatives>false</MarkNegatives>
      \t\t\t<Mask/>
      \t\t\t<MultiLine>false</MultiLine>
      \t\t\t<ExtendedEdit>false</ExtendedEdit>
      \t\t\t<MinValue xsi:nil="true"/>
      \t\t\t<MaxValue xsi:nil="true"/>
      \t\t\t<FillChecking>DontCheck</FillChecking>
      \t\t\t<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>
      \t\t\t<ChoiceParameterLinks/>
      \t\t\t<ChoiceParameters/>
      \t\t\t<QuickChoice>Auto</QuickChoice>
      \t\t\t<ChoiceForm/>
      \t\t\t<LinkByType/>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String documentProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<Numerator/>
      \t\t\t<NumberType>String</NumberType>
      \t\t\t<NumberLength>9</NumberLength>
      \t\t\t<NumberAllowedLength>Variable</NumberAllowedLength>
      \t\t\t<NumberPeriodicity>Nonperiodical</NumberPeriodicity>
      \t\t\t<CheckUnique>true</CheckUnique>
      \t\t\t<Autonumbering>true</Autonumbering>
      \t\t\t<Characteristics/>
      \t\t\t<BasedOn/>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>Document.%s.StandardAttribute.Number</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<CreateOnInput>Use</CreateOnInput>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<Posting>Allow</Posting>
      \t\t\t<RealTimePosting>Allow</RealTimePosting>
      \t\t\t<RegisterRecordsDeletion>AutoDeleteOnUnpost</RegisterRecordsDeletion>
      \t\t\t<RegisterRecordsWritingOnPost>WriteSelected</RegisterRecordsWritingOnPost>
      \t\t\t<SequenceFilling>AutoFill</SequenceFilling>
      \t\t\t<RegisterRecords/>
      \t\t\t<PostInPrivilegedMode>true</PostInPrivilegedMode>
      \t\t\t<UnpostInPrivilegedMode>true</UnpostInPrivilegedMode>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName, objectName);
  }

  private static String reportProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<DefaultForm/>
      \t\t\t<AuxiliaryForm/>
      \t\t\t<MainDataCompositionSchema/>
      \t\t\t<DefaultSettingsForm/>
      \t\t\t<AuxiliarySettingsForm/>
      \t\t\t<DefaultVariantForm/>
      \t\t\t<VariantsStorage/>
      \t\t\t<SettingsStorage/>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<ExtendedPresentation/>
      \t\t\t<Explanation/>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String dataProcessorProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<DefaultForm/>
      \t\t\t<AuxiliaryForm/>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<ExtendedPresentation/>
      \t\t\t<Explanation/>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String enumProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>false</UseStandardCommands>
      \t\t\t<Characteristics/>
      \t\t\t<QuickChoice>true</QuickChoice>
      \t\t\t<ChoiceMode>BothWays</ChoiceMode>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String externalDataSourceProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<DataLockControlMode>Automatic</DataLockControlMode>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String taskProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<NumberType>String</NumberType>
      \t\t\t<NumberLength>9</NumberLength>
      \t\t\t<NumberAllowedLength>Variable</NumberAllowedLength>
      \t\t\t<CheckUnique>true</CheckUnique>
      \t\t\t<Autonumbering>true</Autonumbering>
      \t\t\t<TaskNumberAutoPrefix>DontUse</TaskNumberAutoPrefix>
      \t\t\t<DescriptionLength>25</DescriptionLength>
      \t\t\t<Addressing/>
      \t\t\t<MainAddressingAttribute/>
      \t\t\t<CurrentPerformer/>
      \t\t\t<BasedOn/>
      \t\t\t<Characteristics/>
      \t\t\t<DefaultPresentation>AsDescription</DefaultPresentation>
      \t\t\t<EditType>InDialog</EditType>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>Task.%s.StandardAttribute.Description</xr:Field>
      \t\t\t\t<xr:Field>Task.%s.StandardAttribute.Number</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<CreateOnInput>DontUse</CreateOnInput>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName, objectName, objectName);
  }

  private static String commonModuleProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<Global>false</Global>
      \t\t\t<ClientManagedApplication>false</ClientManagedApplication>
      \t\t\t<Server>true</Server>
      \t\t\t<ExternalConnection>false</ExternalConnection>
      \t\t\t<ClientOrdinaryApplication>false</ClientOrdinaryApplication>
      \t\t\t<ServerCall>false</ServerCall>
      \t\t\t<Privileged>false</Privileged>
      \t\t\t<ReturnValuesReuse>DontUse</ReturnValuesReuse>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String sessionParameterProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<Type>
      \t\t\t\t<v8:Type>xs:string</v8:Type>
      \t\t\t\t<v8:StringQualifiers>
      \t\t\t\t\t<v8:Length>10</v8:Length>
      \t\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>
      \t\t\t\t</v8:StringQualifiers>
      \t\t\t</Type>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String exchangePlanProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<CodeLength>9</CodeLength>
      \t\t\t<CodeAllowedLength>Variable</CodeAllowedLength>
      \t\t\t<DescriptionLength>25</DescriptionLength>
      \t\t\t<DefaultPresentation>AsDescription</DefaultPresentation>
      \t\t\t<EditType>InDialog</EditType>
      \t\t\t<QuickChoice>false</QuickChoice>
      \t\t\t<ChoiceMode>BothWays</ChoiceMode>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>ExchangePlan.%s.StandardAttribute.Description</xr:Field>
      \t\t\t\t<xr:Field>ExchangePlan.%s.StandardAttribute.Code</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<Characteristics/>
      \t\t\t<BasedOn/>
      \t\t\t<DistributedInfoBase>false</DistributedInfoBase>
      \t\t\t<IncludeConfigurationExtensions>false</IncludeConfigurationExtensions>
      \t\t\t<CreateOnInput>DontUse</CreateOnInput>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName, objectName, objectName);
  }

  private static String commonAttributeProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<Type>
      \t\t\t\t<v8:Type>xs:string</v8:Type>
      \t\t\t\t<v8:StringQualifiers>
      \t\t\t\t\t<v8:Length>10</v8:Length>
      \t\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>
      \t\t\t\t</v8:StringQualifiers>
      \t\t\t</Type>
      \t\t\t<PasswordMode>false</PasswordMode>
      \t\t\t<Format/>
      \t\t\t<EditFormat/>
      \t\t\t<ToolTip/>
      \t\t\t<MarkNegatives>false</MarkNegatives>
      \t\t\t<Mask/>
      \t\t\t<MultiLine>false</MultiLine>
      \t\t\t<ExtendedEdit>false</ExtendedEdit>
      \t\t\t<MinValue xsi:nil="true"/>
      \t\t\t<MaxValue xsi:nil="true"/>
      \t\t\t<FillFromFillingValue>false</FillFromFillingValue>
      \t\t\t<FillValue xsi:type="xs:string"/>
      \t\t\t<FillChecking>DontCheck</FillChecking>
      \t\t\t<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>
      \t\t\t<ChoiceParameterLinks/>
      \t\t\t<ChoiceParameters/>
      \t\t\t<QuickChoice>Auto</QuickChoice>
      \t\t\t<CreateOnInput>Auto</CreateOnInput>
      \t\t\t<ChoiceForm/>
      \t\t\t<LinkByType/>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<Content/>
      \t\t\t<AutoUse>DontUse</AutoUse>
      \t\t\t<DataSeparation>DontUse</DataSeparation>
      \t\t\t<SeparatedDataUse>Independently</SeparatedDataUse>
      \t\t\t<DataSeparationValue/>
      \t\t\t<DataSeparationUse/>
      \t\t\t<ConditionalSeparation/>
      \t\t\t<UsersSeparation>DontUse</UsersSeparation>
      \t\t\t<AuthenticationSeparation>DontUse</AuthenticationSeparation>
      \t\t\t<ConfigurationExtensionsSeparation>DontUse</ConfigurationExtensionsSeparation>
      \t\t\t<Indexing>DontIndex</Indexing>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<DataHistory>Use</DataHistory>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String commonPictureProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<AvailabilityForChoice>false</AvailabilityForChoice>
      \t\t\t<AvailabilityForAppearance>false</AvailabilityForAppearance>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String documentNumeratorProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<NumberType>String</NumberType>
      \t\t\t<NumberLength>9</NumberLength>
      \t\t\t<NumberAllowedLength>Variable</NumberAllowedLength>
      \t\t\t<NumberPeriodicity>Nonperiodical</NumberPeriodicity>
      \t\t\t<CheckUnique>true</CheckUnique>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String subsystemProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<IncludeHelpInContents>true</IncludeHelpInContents>
      \t\t\t<IncludeInCommandInterface>true</IncludeInCommandInterface>
      \t\t\t<UseOneCommand>false</UseOneCommand>
      \t\t\t<Explanation/>
      \t\t\t<Picture/>
      \t\t\t<Content/>
      \t\t</Properties>""".formatted(objectName);
  }

  private static String chartOfAccountsProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<BasedOn/>
      \t\t\t<ExtDimensionTypes/>
      \t\t\t<MaxExtDimensionCount>0</MaxExtDimensionCount>
      \t\t\t<CodeMask/>
      \t\t\t<CodeLength>9</CodeLength>
      \t\t\t<DescriptionLength>25</DescriptionLength>
      \t\t\t<CodeSeries>WholeChartOfAccounts</CodeSeries>
      \t\t\t<CheckUnique>true</CheckUnique>
      \t\t\t<DefaultPresentation>AsCode</DefaultPresentation>
      \t\t\t<Characteristics/>
      \t\t\t<PredefinedDataUpdate>Auto</PredefinedDataUpdate>
      \t\t\t<EditType>InDialog</EditType>
      \t\t\t<QuickChoice>false</QuickChoice>
      \t\t\t<ChoiceMode>BothWays</ChoiceMode>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>ChartOfAccounts.%s.StandardAttribute.Description</xr:Field>
      \t\t\t\t<xr:Field>ChartOfAccounts.%s.StandardAttribute.Code</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<CreateOnInput>DontUse</CreateOnInput>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<AutoOrderByCode>false</AutoOrderByCode>
      \t\t\t<OrderLength>0</OrderLength>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t</Properties>""".formatted(objectName, objectName, objectName);
  }

  private static String chartOfCharacteristicTypesProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<CharacteristicExtValues/>
      \t\t\t<Type>
      \t\t\t\t<v8:Type>xs:string</v8:Type>
      \t\t\t\t<v8:StringQualifiers>
      \t\t\t\t\t<v8:Length>10</v8:Length>
      \t\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>
      \t\t\t\t</v8:StringQualifiers>
      \t\t\t</Type>
      \t\t\t<Hierarchical>false</Hierarchical>
      \t\t\t<FoldersOnTop>true</FoldersOnTop>
      \t\t\t<CodeLength>9</CodeLength>
      \t\t\t<CodeAllowedLength>Variable</CodeAllowedLength>
      \t\t\t<DescriptionLength>25</DescriptionLength>
      \t\t\t<CodeSeries>WholeCharacteristicKind</CodeSeries>
      \t\t\t<CheckUnique>true</CheckUnique>
      \t\t\t<Autonumbering>true</Autonumbering>
      \t\t\t<DefaultPresentation>AsDescription</DefaultPresentation>
      \t\t\t<Characteristics/>
      \t\t\t<PredefinedDataUpdate>Auto</PredefinedDataUpdate>
      \t\t\t<EditType>InDialog</EditType>
      \t\t\t<QuickChoice>false</QuickChoice>
      \t\t\t<ChoiceMode>BothWays</ChoiceMode>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>ChartOfCharacteristicTypes.%s.StandardAttribute.Description</xr:Field>
      \t\t\t\t<xr:Field>ChartOfCharacteristicTypes.%s.StandardAttribute.Code</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<CreateOnInput>DontUse</CreateOnInput>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultFolderForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<DefaultFolderChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryFolderForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<AuxiliaryFolderChoiceForm/>
      \t\t\t<BasedOn/>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName, objectName, objectName);
  }

  private static String chartOfCalculationTypesProperties(String objectName) {
    return """
      <Properties>
      \t\t\t<Name>%s</Name>
      \t\t\t<Synonym/>
      \t\t\t<Comment/>
      \t\t\t<UseStandardCommands>true</UseStandardCommands>
      \t\t\t<CodeLength>9</CodeLength>
      \t\t\t<DescriptionLength>40</DescriptionLength>
      \t\t\t<CodeType>String</CodeType>
      \t\t\t<CodeAllowedLength>Variable</CodeAllowedLength>
      \t\t\t<DefaultPresentation>AsDescription</DefaultPresentation>
      \t\t\t<EditType>InDialog</EditType>
      \t\t\t<QuickChoice>false</QuickChoice>
      \t\t\t<ChoiceMode>BothWays</ChoiceMode>
      \t\t\t<InputByString>
      \t\t\t\t<xr:Field>ChartOfCalculationTypes.%s.StandardAttribute.Description</xr:Field>
      \t\t\t\t<xr:Field>ChartOfCalculationTypes.%s.StandardAttribute.Code</xr:Field>
      \t\t\t</InputByString>
      \t\t\t<SearchStringModeOnInputByString>Begin</SearchStringModeOnInputByString>
      \t\t\t<FullTextSearchOnInputByString>DontUse</FullTextSearchOnInputByString>
      \t\t\t<ChoiceDataGetModeOnInputByString>Directly</ChoiceDataGetModeOnInputByString>
      \t\t\t<CreateOnInput>DontUse</CreateOnInput>
      \t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>
      \t\t\t<DefaultObjectForm/>
      \t\t\t<DefaultListForm/>
      \t\t\t<DefaultChoiceForm/>
      \t\t\t<AuxiliaryObjectForm/>
      \t\t\t<AuxiliaryListForm/>
      \t\t\t<AuxiliaryChoiceForm/>
      \t\t\t<BasedOn/>
      \t\t\t<DependenceOnCalculationTypes>DontUse</DependenceOnCalculationTypes>
      \t\t\t<BaseCalculationTypes/>
      \t\t\t<ActionPeriodUse>false</ActionPeriodUse>
      \t\t\t<Characteristics/>
      \t\t\t<PredefinedDataUpdate>Auto</PredefinedDataUpdate>
      \t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>
      \t\t\t<DataLockFields/>
      \t\t\t<DataLockControlMode>Managed</DataLockControlMode>
      \t\t\t<FullTextSearch>Use</FullTextSearch>
      \t\t\t<ObjectPresentation/>
      \t\t\t<ExtendedObjectPresentation/>
      \t\t\t<ListPresentation/>
      \t\t\t<ExtendedListPresentation/>
      \t\t\t<Explanation/>
      \t\t\t<DataHistory>DontUse</DataHistory>
      \t\t\t<UpdateDataHistoryImmediatelyAfterWrite>false</UpdateDataHistoryImmediatelyAfterWrite>
      \t\t\t<ExecuteAfterWriteDataHistoryVersionProcessing>false</ExecuteAfterWriteDataHistoryVersionProcessing>
      \t\t</Properties>""".formatted(objectName, objectName, objectName);
  }

  private static String normalizeExchangePlan(String xml, String objectName, SchemaVersion version) {
    if (xml.contains("<xr:ThisNode>")) {
      return xml;
    }
    String thisNode = GoldenUuid.from(
      "newMdObject|" + version + "|" + MdObjectAddType.EXCHANGE_PLAN + "|" + objectName + "|thisNode",
      "valueId");
    return xml.replace("<InternalInfo>\n", "<InternalInfo>\n\t\t\t<xr:ThisNode>" + thisNode + "</xr:ThisNode>\n");
  }

  private static String generatedTypeName(MdObjectAddType type, String category, String objectName) {
    if (type == MdObjectAddType.CHART_OF_CHARACTERISTIC_TYPES && "Characteristic".equals(category)) {
      return "Characteristic." + objectName;
    }
    if (type == MdObjectAddType.CHART_OF_CALCULATION_TYPES
      && (category.startsWith("DisplacingCalculationTypes")
      || category.startsWith("BaseCalculationTypes")
      || category.startsWith("LeadingCalculationTypes"))) {
      return category + "." + objectName;
    }
    return type.configurationXmlTag() + category + "." + objectName;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object resolveTypeCategoryEnum(Class<?> categoriesEnumClass, String category)
    throws ReflectiveOperationException {
    try {
      Method fromValue = categoriesEnumClass.getMethod("fromValue", String.class);
      return fromValue.invoke(null, category);
    } catch (NoSuchMethodException ignored) {
      return Enum.valueOf((Class<Enum>) categoriesEnumClass.asSubclass(Enum.class), category.toUpperCase());
    }
  }
}
