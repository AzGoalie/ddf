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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.WstxDriver;
import com.thoughtworks.xstream.security.NoTypePermission;
import ddf.catalog.data.Metacard;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import net.opengis.wfs.v_1_1_0.FeatureTypeType;
import net.opengis.wfs.v_1_1_0.WFSCapabilitiesType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ws.commons.schema.XmlSchema;
import org.codice.ddf.cxf.SecureCxfClientFactory;
import org.codice.ddf.libs.geo.util.GeospatialUtil;
import org.codice.ddf.spatial.ogc.wfs.catalog.MetacardTypeEnhancer;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.FeatureMetacardType;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsException;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsFeatureCollection;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.FeatureConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.impl.GmlEnvelopeConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.impl.GmlGeometryConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.mapper.MetacardMapper;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.FeatureTransformer;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.DescribeFeatureTypeRequest;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.GetCapabilitiesRequest;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.Wfs;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.common.Wfs11Constants;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.converter.FeatureConverterFactoryV110;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.converter.impl.GenericFeatureConverterWfs11;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.source.WfsResponseExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XStreamWfsFeatureTransformer implements FeatureTransformer {
  private static final Logger LOGGER = LoggerFactory.getLogger(XStreamWfsFeatureTransformer.class);

  private static final String WFS_ERROR_MESSAGE = "Error received from Wfs Server.";

  private static final String SOURCE_MSG = " Source '";

  private final Supplier<String> coordinateOrderSupplier;

  private Supplier<String> idSupplier;

  private Supplier<String> wfsUrlSupplier;

  private List<FeatureConverterFactoryV110> featureConverterFactories;

  private List<MetacardMapper> metacardToFeatureMappers;

  private List<MetacardTypeEnhancer> metacardTypeEnhancers;

  private SecureCxfClientFactory<Wfs> factory;

  private XStream xstream;

  public XStreamWfsFeatureTransformer(
      Supplier<String> idSupplier,
      Supplier<String> wfsUrlSupplier,
      Supplier<String> coordinateOrderSupplier) {
    this.idSupplier = idSupplier;
    this.wfsUrlSupplier = wfsUrlSupplier;
    this.coordinateOrderSupplier = coordinateOrderSupplier;
    this.metacardToFeatureMappers = Collections.emptyList();

    initializeXstream();
  }

  public XStreamWfsFeatureTransformer() {
    this.idSupplier = () -> "";
    this.wfsUrlSupplier = () -> "";
    this.coordinateOrderSupplier = () -> GeospatialUtil.LAT_LON_ORDER;
    this.metacardToFeatureMappers = Collections.emptyList();
    initializeXstream();
  }

  private void initializeXstream() {
    xstream = new XStream(new WstxDriver());
    xstream.addPermission(NoTypePermission.NONE);
    xstream.setClassLoader(this.getClass().getClassLoader());
    xstream.registerConverter(new GmlGeometryConverter());
    xstream.registerConverter(new GmlEnvelopeConverter());
    xstream.alias("FeatureCollection", WfsFeatureCollection.class);

    WFSCapabilitiesType capabilitiesType = getCapabilities();

    if (capabilitiesType != null) {
      for (FeatureTypeType type : capabilitiesType.getFeatureTypeList().getFeatureType()) {
        String simpleName = type.getName().getLocalPart();

        MetacardTypeEnhancer metacardTypeEnhancer =
            metacardTypeEnhancers
                .stream()
                .filter(me -> me.getFeatureName() != null)
                .filter(me -> me.getFeatureName().equalsIgnoreCase(simpleName))
                .findAny()
                .orElse(FeatureMetacardType.DEFAULT_METACARD_TYPE_ENHANCER);

        FeatureMetacardType metacardType =
            new FeatureMetacardType(
                getSchema(type),
                type.getName(),
                new ArrayList<>(), // TODO: Should this be an empty list?
                Wfs11Constants.GML_3_1_1_NAMESPACE,
                metacardTypeEnhancer);
        lookupFeatureConverter(simpleName, metacardType, type.getDefaultSRS());
      }
    }
  }

  private XmlSchema getSchema(FeatureTypeType type) {
    XmlSchema schema = null;
    try {
      Wfs client = factory.getClient();
      schema = client.describeFeatureType(new DescribeFeatureTypeRequest(type.getName()));
      if (schema == null) {
        schema =
            client.describeFeatureType(
                new DescribeFeatureTypeRequest(
                    new QName(
                        type.getName().getNamespaceURI(), type.getName().getLocalPart(), "")));
      }

    } catch (WfsException | IllegalArgumentException wfse) {
      LOGGER.debug(WFS_ERROR_MESSAGE, wfse);
    } catch (WebApplicationException wae) {
      LOGGER.debug(handleWebApplicationException(wae), wae);
    }

    return schema;
  }

  @Override
  public Optional<Metacard> apply(InputStream inputStream) {
    xstream.allowTypeHierarchy(Metacard.class);
    Metacard metacard = null;
    try {
      metacard = (Metacard) xstream.fromXML(inputStream);
    } catch (Exception e) {
      LOGGER.debug("Failed to parse FeatureMember into a Metacard", e);
    }

    return Optional.ofNullable(metacard);
  }

  public void setMetacardToFeatureMapper(List<MetacardMapper> mappers) {
    this.metacardToFeatureMappers = mappers;
  }

  public void setFeatureConverterFactoryList(List<FeatureConverterFactoryV110> factories) {
    this.featureConverterFactories = factories;
  }

  public void setFactory(SecureCxfClientFactory<Wfs> factory) {
    this.factory = factory;
  }

  public void setMetacardTypeEnhancers(List<MetacardTypeEnhancer> metacardTypeEnhancers) {
    this.metacardToFeatureMappers = metacardToFeatureMappers;
  }

  public List<MetacardMapper> getMetacardToFeatureMapper() {
    return this.metacardToFeatureMappers;
  }

  private void lookupFeatureConverter(
      String ftSimpleName, FeatureMetacardType ftMetacard, String srs) {
    FeatureConverter featureConverter = null;

    /**
     * The list of feature converter factories injected into this class is a live list. So, feature
     * converter factories can be added and removed from the system while running.
     */
    if (CollectionUtils.isNotEmpty(featureConverterFactories)) {
      for (FeatureConverterFactoryV110 featureConverterFactory : featureConverterFactories) {
        if (ftSimpleName.equalsIgnoreCase(featureConverterFactory.getFeatureType())) {
          featureConverter = featureConverterFactory.createConverter();
          break;
        }
      }
    }

    // Found a specific feature converter
    if (featureConverter != null) {
      LOGGER.debug(
          "WFS Source {}: Features of type: {} will be converted using {}",
          idSupplier.get(),
          ftSimpleName,
          featureConverter.getClass().getSimpleName());
    } else {
      LOGGER.debug(
          "WfsSource {}: Unable to find a feature specific converter; {} will be converted using the GenericFeatureConverter",
          idSupplier.get(),
          ftSimpleName);

      // Since we have no specific converter, we will check to see if we have a mapper to do
      // feature property to metacard attribute mappings.
      MetacardMapper featurePropertyToMetacardAttributeMapper =
          lookupMetacardAttributeToFeaturePropertyMapper(ftMetacard.getFeatureType());

      if (featurePropertyToMetacardAttributeMapper != null) {
        featureConverter =
            new GenericFeatureConverterWfs11(featurePropertyToMetacardAttributeMapper);
        LOGGER.debug(
            "WFS Source {}: Created {} for feature type {} with feature property to metacard attribute mapper.",
            idSupplier.get(),
            featureConverter.getClass().getSimpleName(),
            ftSimpleName);
      } else {
        featureConverter = new GenericFeatureConverterWfs11(srs);
        featureConverter.setCoordinateOrder(coordinateOrderSupplier.get());
        LOGGER.debug(
            "WFS Source {}: Created {} for feature type {} with no feature property to metacard attribute mapper.",
            idSupplier.get(),
            featureConverter.getClass().getSimpleName(),
            ftSimpleName);
      }
    }

    featureConverter.setSourceId(idSupplier.get());
    featureConverter.setMetacardType(ftMetacard);
    featureConverter.setWfsUrl(wfsUrlSupplier.get());

    // Add the Feature Type name as an alias for xstream
    LOGGER.debug(
        "Registering feature converter {} for feature type {}.",
        featureConverter.getClass().getSimpleName(),
        ftSimpleName);
    xstream.registerConverter(featureConverter);
  }

  private MetacardMapper lookupMetacardAttributeToFeaturePropertyMapper(QName featureType) {
    MetacardMapper metacardAttributeToFeaturePropertyMapper = null;

    if (this.metacardToFeatureMappers != null) {
      for (MetacardMapper mapper : this.metacardToFeatureMappers) {
        if (mapper != null && StringUtils.equals(mapper.getFeatureType(), featureType.toString())) {
          logFeatureType(featureType, "Found {} for feature type {}.");
          metacardAttributeToFeaturePropertyMapper = mapper;
          break;
        }
      }

      if (metacardAttributeToFeaturePropertyMapper == null) {
        logFeatureType(featureType, "Unable to find a {} for feature type {}.");
      }
    }

    return metacardAttributeToFeaturePropertyMapper;
  }

  private void logFeatureType(QName featureType, String message) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(message, MetacardMapper.class.getSimpleName(), featureType);
    }
  }

  private WFSCapabilitiesType getCapabilities() {
    WFSCapabilitiesType capabilities = null;
    Wfs wfs = factory.getClient();
    try {
      capabilities = wfs.getCapabilities(new GetCapabilitiesRequest());
    } catch (WfsException wfse) {
      LOGGER.debug(
          WFS_ERROR_MESSAGE + " Received HTTP code '{}' from server for source with id='{}'",
          wfse.getHttpStatus(),
          idSupplier.get(),
          wfse);
    } catch (WebApplicationException wae) {
      LOGGER.debug(handleWebApplicationException(wae), wae);
    } catch (Exception e) {
      String message = handleClientException(e);
      LOGGER.error("Failed to get capabilities: {}", message);
    }
    return capabilities;
  }

  private String handleClientException(Exception ce) {
    String msg;
    Throwable cause = ce.getCause();
    String sourceId = idSupplier.get();
    if (cause instanceof WebApplicationException) {
      msg = handleWebApplicationException((WebApplicationException) cause);
    } else if (cause instanceof IllegalArgumentException) {
      msg =
          WFS_ERROR_MESSAGE
              + SOURCE_MSG
              + sourceId
              + "'. The URI '"
              + wfsUrlSupplier.get()
              + "' does not specify a valid protocol or could not be correctly parsed. "
              + ce.getMessage();
    } else if (cause instanceof SSLHandshakeException) {
      msg =
          WFS_ERROR_MESSAGE
              + SOURCE_MSG
              + sourceId
              + "' with URL '"
              + wfsUrlSupplier.get()
              + "': "
              + ce.getMessage();
    } else if (cause instanceof ConnectException) {
      msg = WFS_ERROR_MESSAGE + SOURCE_MSG + sourceId + "' may not be running.\n" + ce.getMessage();
    } else {
      msg = WFS_ERROR_MESSAGE + SOURCE_MSG + sourceId + "'\n" + ce;
    }
    LOGGER.debug(msg, ce);
    return msg;
  }

  private String handleWebApplicationException(WebApplicationException wae) {
    Response response = wae.getResponse();
    WfsException wfsException = new WfsResponseExceptionMapper().fromResponse(response);

    return "Error received from WFS Server " + idSupplier.get() + "\n" + wfsException.getMessage();
  }
}
