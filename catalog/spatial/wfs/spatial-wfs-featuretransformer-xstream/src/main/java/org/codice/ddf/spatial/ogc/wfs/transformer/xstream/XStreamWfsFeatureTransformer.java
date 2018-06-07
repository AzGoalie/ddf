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
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.io.xml.WstxDriver;
import com.thoughtworks.xstream.security.NoTypePermission;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.FeatureConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.FeatureConverterFactory;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.impl.GmlEnvelopeConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.impl.GmlGeometryConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.mapper.MetacardMapper;
import org.codice.ddf.spatial.ogc.wfs.catalog.metacardtype.registry.WfsMetacardTypeRegistry;
import org.codice.ddf.spatial.ogc.wfs.featuretransformer.FeatureTransformer;
import org.codice.ddf.spatial.ogc.wfs.featuretransformer.WfsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class XStreamWfsFeatureTransformer<T> implements FeatureTransformer<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(XStreamWfsFeatureTransformer.class);

  private List<FeatureConverterFactory> featureConverterFactories;

  private List<MetacardMapper> metacardMappers;

  protected WfsMetacardTypeRegistry metacardTypeRegistry;

  private XStream xStream;

  private List<QName> featureTypeQNameList;

  public XStreamWfsFeatureTransformer() {
    this.featureTypeQNameList = new ArrayList<>();
    xStream = new XStream(new WstxDriver());
    xStream.addPermission(NoTypePermission.NONE);
    xStream.allowTypeHierarchy(Metacard.class);
    xStream.setClassLoader(this.getClass().getClassLoader());
    xStream.registerConverter(new GmlGeometryConverter());
    xStream.registerConverter(new GmlEnvelopeConverter());
  }

  @Override
  public Optional<Metacard> apply(InputStream document, WfsMetadata<T> metadata) {
    xStream.alias(metadata.getFeatureMemberNodeName(), Metacard.class);
    Metacard metacard = null;

    for (T featureType : metadata.getDescriptors()) {
      if (!featureTypeQNameList.contains(getFeatureTypeName(featureType))) {
        lookupFeatureConverter(metadata, featureType).ifPresent(xStream::registerConverter);
        featureTypeQNameList.add(getFeatureTypeName(featureType));
      }
    }

    try {
      metacard = (Metacard) xStream.fromXML(document);
    } catch (XStreamException e) {
      LOGGER.trace("Failed to parse FeatureMember into Metacard", e);
      LOGGER.error("failed to parse", e);
    }

    return Optional.ofNullable(metacard);
  }

  protected Optional<FeatureConverter> lookupFeatureConverter(
      WfsMetadata<T> metadata, T featureType) {
    FeatureConverter featureConverter = null;
    Optional<MetacardType> metacardType = lookupFeatureMetacardType(metadata.getId(), featureType);
    if (metacardType.isPresent()) {
      featureConverter =
          lookupFeatureConverterFactories(featureType)
              .orElse(
                  metacardMapperFeatureConverter(
                      getFeatureTypeName(featureType), featureType, metadata.getCoordinateOrder()));

      featureConverter.setMetacardType(metacardType.get());
      featureConverter.setSourceId(metadata.getId());
    }
    return Optional.ofNullable(featureConverter);
  }

  private Optional<FeatureConverter> lookupFeatureConverterFactories(T featureType) {
    FeatureConverter featureConverter = null;
    if (featureConverterFactories != null) {
      for (FeatureConverterFactory factory : featureConverterFactories) {
        if (canConvertFeature(factory, featureType)) {
          featureConverter = factory.createConverter();
          break;
        }
      }
    }

    return Optional.ofNullable(featureConverter);
  }

  private FeatureConverter metacardMapperFeatureConverter(
      QName featureTypeName, T featureType, String coordinateOrder) {
    FeatureConverter featureConverter;
    MetacardMapper metacardMapper = lookupMetacardMapper(featureTypeName);
    if (metacardMapper != null) {
      featureConverter = getGenericFeatureConverter(metacardMapper);
    } else {
      featureConverter = getGenericFeatureConverter(featureType);
      featureConverter.setCoordinateOrder(coordinateOrder);
    }

    return featureConverter;
  }

  protected abstract Optional<MetacardType> lookupFeatureMetacardType(
      String sourceId, T featureType);

  protected abstract boolean canConvertFeature(
      FeatureConverterFactory featureConverterFactory, T featureType);

  protected MetacardMapper lookupMetacardMapper(QName featureType) {
    MetacardMapper metacardMapper = null;

    if (metacardMappers != null) {
      for (MetacardMapper mapper : metacardMappers) {
        if (mapper != null
            && StringUtils.equalsIgnoreCase(mapper.getFeatureType(), featureType.toString())) {
          logFeatureType(featureType, "Found {} for feature type {}.");
          metacardMapper = mapper;
          break;
        }
      }

      if (metacardMapper == null) {
        logFeatureType(featureType, "Unable to find a {} for feature type {}.");
      }
    }

    return metacardMapper;
  }

  protected abstract FeatureConverter getGenericFeatureConverter(MetacardMapper metacardMapper);

  protected abstract FeatureConverter getGenericFeatureConverter(T featureType);

  protected abstract QName getFeatureTypeName(T featureType);

  private void logFeatureType(QName featureType, String message) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(message, MetacardMapper.class.getSimpleName(), featureType);
    }
  }

  public void setFeatureConverterFactories(
      List<FeatureConverterFactory> featureConverterFactories) {
    this.featureConverterFactories = featureConverterFactories;
  }

  public void setMetacardMappers(List<MetacardMapper> metacardMappers) {
    this.metacardMappers = metacardMappers;
  }

  public void setMetacardTypeRegistry(WfsMetacardTypeRegistry metacardTypeRegistry) {
    this.metacardTypeRegistry = metacardTypeRegistry;
  }
}
