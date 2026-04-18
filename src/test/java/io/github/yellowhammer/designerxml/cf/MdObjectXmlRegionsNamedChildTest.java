/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;

import static org.assertj.core.api.Assertions.assertThat;

/** Регионы реквизита — короткий XML в строке, без обхода каталогов выгрузки. */
class MdObjectXmlRegionsNamedChildTest {

  private static final String MINIMAL_ATTR_XML = """
    <?xml version="1.0" encoding="UTF-8"?>
    <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
    <Catalog uuid="00000000-0000-0000-0000-000000000001">
      <InternalInfo/>
      <ChildObjects>
        <Attribute uuid="00000000-0000-0000-0000-000000000002">
          <InternalInfo/>
          <Properties>
            <Name>РекА</Name>
            <Synonym><v8:item xmlns:v8="http://v8.1c.ru/8.1/data/core"><v8:lang>ru</v8:lang><v8:content>Син</v8:content></v8:item></Synonym>
            <Comment/>
            <Type><v8:Type>xs:string</v8:Type></Type>
          </Properties>
        </Attribute>
      </ChildObjects>
    </Catalog>
    </MetaDataObject>
    """;

  @Test
  void findSynonymRegionByAttributeName() throws XMLStreamException {
    MdObjectXmlRegions.Region r =
      MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
        MINIMAL_ATTR_XML, "Catalog", "Attribute", "РекА", "Synonym");
    assertThat(r.isValid()).isTrue();
    String frag = MINIMAL_ATTR_XML.substring(r.start(), r.end());
    assertThat(frag).contains("<Synonym>");
    assertThat(frag).contains("Син");
  }

  @Test
  void findCommentRegionByAttributeName() throws XMLStreamException {
    MdObjectXmlRegions.Region r =
      MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
        MINIMAL_ATTR_XML, "Catalog", "Attribute", "РекА", "Comment");
    assertThat(r.isValid()).isTrue();
    assertThat(MINIMAL_ATTR_XML.substring(r.start(), r.end())).isEqualTo("<Comment/>");
  }
}
