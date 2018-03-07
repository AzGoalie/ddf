/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.spatial.ogc.wfs.transformer.xstream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ddf.catalog.data.Metacard;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import javax.xml.namespace.QName;
import net.opengis.wfs.v_1_1_0.FeatureTypeListType;
import net.opengis.wfs.v_1_1_0.FeatureTypeType;
import net.opengis.wfs.v_1_1_0.WFSCapabilitiesType;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.constants.Constants;
import org.codice.ddf.cxf.SecureCxfClientFactory;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsException;
import org.codice.ddf.spatial.ogc.wfs.catalog.mapper.MetacardMapper;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.DescribeFeatureTypeRequest;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.GetCapabilitiesRequest;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.Wfs;
import org.junit.Before;
import org.junit.Test;

public class XStreamWfsFeatureTransformerTest {
  private static final QName TEST_TYPE = new QName("TEST_ELEMENT");

  private XStreamWfsFeatureTransformer transformer;

  @Before
  public void setup() throws WfsException {
    SecureCxfClientFactory<Wfs> factory = mock(SecureCxfClientFactory.class);
    Wfs wfs = mockWfsClient();
    when(factory.getClient()).thenReturn(wfs);

    MetacardMapper metacardMapper = mock(MetacardMapper.class);
    when(metacardMapper.getFeatureType()).thenReturn(TEST_TYPE.toString());
    String localPart = TEST_TYPE.getLocalPart();
    when(metacardMapper.getMetacardAttribute("ext." + localPart + "." + localPart))
        .thenReturn("title");

    // TODO: Set proper feature converter factories

    this.transformer = new XStreamWfsFeatureTransformer();
    this.transformer.setFactory(factory);
    this.transformer.setMetacardToFeatureMapper(Collections.singletonList(metacardMapper));
    this.transformer.init();
  }

  @Test
  public void testRead() {
    InputStream inputStream =
        new BufferedInputStream(
            XStreamWfsFeatureTransformerTest.class.getResourceAsStream("/FeatureMember.xml"));
    Optional<Metacard> metacardOptional = transformer.apply(inputStream);
    assertThat(metacardOptional.isPresent(), equalTo(true));
    assertThat(metacardOptional.get().getAttribute("title"), notNullValue());
  }

  private Wfs mockWfsClient() throws WfsException {
    Wfs wfs = mock(Wfs.class);

    FeatureTypeType type = buildFeatureType(TEST_TYPE.getLocalPart(), "srs");

    FeatureTypeListType featureTypeListType = new FeatureTypeListType();
    featureTypeListType.setFeatureType(Collections.singletonList(type));

    WFSCapabilitiesType capabilitiesType = new WFSCapabilitiesType();
    capabilitiesType.setFeatureTypeList(featureTypeListType);
    when(wfs.getCapabilities(any(GetCapabilitiesRequest.class))).thenReturn(capabilitiesType);

    XmlSchema schema = new XmlSchema();
    schema
        .getElements()
        .put(
            type.getName(),
            buildSchemaElement(type.getName().getLocalPart(), schema, Constants.XSD_STRING));
    when(wfs.describeFeatureType(any(DescribeFeatureTypeRequest.class))).thenReturn(schema);

    return wfs;
  }

  private FeatureTypeType buildFeatureType(String localName, String srs) {
    FeatureTypeType type = new FeatureTypeType();
    type.setDefaultSRS(srs);
    type.setName(new QName(localName));

    return type;
  }

  private XmlSchemaElement buildSchemaElement(
      String elementName, XmlSchema schema, QName typeName) {
    XmlSchemaElement element = new XmlSchemaElement(schema, true);
    element.setSchemaType(new XmlSchemaSimpleType(schema, false));
    element.setSchemaTypeName(typeName);
    element.setName(elementName);

    return element;
  }
}
