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
package org.codice.ddf.spatial.ogc.wfs.catalog.message;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.data.Metacard;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.ext.MessageBodyReader;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.FeatureTransformer;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.WfsFeatureCollection;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.WfsObjectFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class WfsMessageBodyReaderTest {
  private static final int FEATURE_MEMBER_COUNT = 10;

  private CamelContext camelContext;

  private Endpoint endpoint;

  private MessageBodyReader<WfsFeatureCollection> messageBodyReader;

  private List<FeatureTransformer> transformerList;

  @Before
  public void setup() throws Exception {
    setupTransformers();
    SimpleRegistry registry = new SimpleRegistry();
    registry.put("wfsTransformerProcessor", new WfsTransformerProcessor(transformerList));

    WfsObjectFactory wfsObjectFactory = new WfsObjectFactoryImpl();
    Function<List<Metacard>, WfsFeatureCollection> factoryFunction = WfsFeatureCollectionImpl::new;
    wfsObjectFactory.addMessageBodyHandler(WfsFeatureCollection.class, factoryFunction);
    registry.put("wfsObjectFactory", wfsObjectFactory);

    this.camelContext = new DefaultCamelContext(registry);
    camelContext.addRoutes(new WfsRouteBuilder());
    camelContext.setTracing(true);

    endpoint = camelContext.getEndpoint(WfsRouteBuilder.ENDPOINT_URL);
    messageBodyReader = ProxyHelper.createProxy(endpoint, MessageBodyReader.class);
    camelContext.start();
  }

  private void setupTransformers() {
    transformerList = new ArrayList<>();
    FeatureTransformer mockTransformer = mock(FeatureTransformer.class);
    Optional optional = Optional.of(mock(Metacard.class));
    when(mockTransformer.apply(any(InputStream.class))).thenReturn(optional);
    transformerList.add(mockTransformer);
  }

  @Test
  public void testReadFrom() throws Exception {
    InputStream inputStream =
        new BufferedInputStream(
            WfsMessageBodyReaderTest.class.getResourceAsStream("/GeneralGIN.xml"));
    WfsFeatureCollection wfsFeatureCollection =
        messageBodyReader.readFrom(WfsFeatureCollection.class, null, null, null, null, inputStream);
    ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(transformerList.get(0), times(FEATURE_MEMBER_COUNT)).apply(argumentCaptor.capture());

    for (int i = 0; i < FEATURE_MEMBER_COUNT; i++) {
      assertThat(argumentCaptor.getAllValues().get(i), notNullValue());
    }

    assertThat(wfsFeatureCollection.getMembers(), hasSize(10));
  }

  @Test
  public void testIsReadable() {
    boolean isReadable = messageBodyReader.isReadable(Integer.class, null, null, null);
    assertFalse(isReadable);
    isReadable = messageBodyReader.isReadable(WfsFeatureCollection.class, null, null, null);
    assertTrue(isReadable);
  }
}
