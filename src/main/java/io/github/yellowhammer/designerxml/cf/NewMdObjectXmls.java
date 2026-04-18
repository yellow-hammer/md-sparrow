/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;

final class NewMdObjectXmls {
  private NewMdObjectXmls() {
  }

  static String generate(MdObjectAddType type, String objectName, SchemaVersion version) throws JAXBException, IOException {
    return MdObjectXmlGenerator.generate(type, objectName, version);
  }
}

final class NewEnumXml {
  private NewEnumXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.ENUM, objectName, version);
  }
}

final class NewConstantXml {
  private NewConstantXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.CONSTANT, objectName, version);
  }
}

final class NewDocumentXml {
  private NewDocumentXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.DOCUMENT, objectName, version);
  }
}

final class NewReportXml {
  private NewReportXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.REPORT, objectName, version);
  }
}

final class NewDataProcessorXml {
  private NewDataProcessorXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.DATA_PROCESSOR, objectName, version);
  }
}

final class NewTaskXml {
  private NewTaskXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.TASK, objectName, version);
  }
}

final class NewChartOfAccountsXml {
  private NewChartOfAccountsXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.CHART_OF_ACCOUNTS, objectName, version);
  }
}

final class NewChartOfCharacteristicTypesXml {
  private NewChartOfCharacteristicTypesXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.CHART_OF_CHARACTERISTIC_TYPES, objectName, version);
  }
}

final class NewChartOfCalculationTypesXml {
  private NewChartOfCalculationTypesXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.CHART_OF_CALCULATION_TYPES, objectName, version);
  }
}

final class NewCommonModuleXml {
  private NewCommonModuleXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.COMMON_MODULE, objectName, version);
  }
}

final class NewSubsystemXml {
  private NewSubsystemXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.SUBSYSTEM, objectName, version);
  }
}

final class NewSessionParameterXml {
  private NewSessionParameterXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.SESSION_PARAMETER, objectName, version);
  }
}

final class NewExchangePlanXml {
  private NewExchangePlanXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.EXCHANGE_PLAN, objectName, version);
  }
}

final class NewCommonAttributeXml {
  private NewCommonAttributeXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.COMMON_ATTRIBUTE, objectName, version);
  }
}

final class NewCommonPictureXml {
  private NewCommonPictureXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.COMMON_PICTURE, objectName, version);
  }
}

final class NewDocumentNumeratorXml {
  private NewDocumentNumeratorXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.DOCUMENT_NUMERATOR, objectName, version);
  }
}

final class NewExternalDataSourceXml {
  private NewExternalDataSourceXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.EXTERNAL_DATA_SOURCE, objectName, version);
  }
}

final class NewRoleXml {
  private NewRoleXml() {}
  static String generate(String objectName, SchemaVersion version) throws JAXBException, IOException {
    return NewMdObjectXmls.generate(MdObjectAddType.ROLE, objectName, version);
  }
}
