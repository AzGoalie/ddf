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

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.mock;

import ddf.catalog.data.Metacard;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import org.codice.ddf.cxf.SecureCxfClientFactory;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.Wfs;
import org.junit.Before;
import org.junit.Test;

public class XStreamWfsFeatureTransformerTest {
  private XStreamWfsFeatureTransformer transformer;

  @Before
  public void setup() {
    this.transformer = new XStreamWfsFeatureTransformer();
    SecureCxfClientFactory<Wfs> factory = mock(SecureCxfClientFactory.class);

    // TODO: Set proper metacard mapper and feature converter factory's
    this.transformer.setFactory(factory);
    this.transformer.setFeatureConverterFactoryList(Collections.emptyList());
    this.transformer.setMetacardToFeatureMapper(Collections.emptyList());
    this.transformer.setMetacardTypeEnhancers(Collections.emptyList());
  }

  @Test
  public void testRead() {
    InputStream inputStream =
        new BufferedInputStream(
            XStreamWfsFeatureTransformerTest.class.getResourceAsStream("/FeatureMember.xml"));
    Optional<Metacard> metacardOptional = transformer.apply(inputStream);
    assertTrue(metacardOptional.isPresent());
  }
}
