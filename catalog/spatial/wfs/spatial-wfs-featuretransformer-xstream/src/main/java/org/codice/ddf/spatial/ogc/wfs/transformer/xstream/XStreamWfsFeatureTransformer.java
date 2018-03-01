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

import ddf.catalog.data.Metacard;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.xml.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.libs.geo.util.GeospatialUtil;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.FeatureMetacardType;
import org.codice.ddf.spatial.ogc.wfs.catalog.converter.FeatureConverter;
import org.codice.ddf.spatial.ogc.wfs.catalog.mapper.MetacardMapper;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.FeatureTransformer;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.converter.FeatureConverterFactoryV110;
import org.codice.ddf.spatial.ogc.wfs.v110.catalog.converter.impl.GenericFeatureConverterWfs11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XStreamWfsFeatureTransformer implements FeatureTransformer {
  private static final Logger LOGGER = LoggerFactory.getLogger(XStreamWfsFeatureTransformer.class);

  private final Supplier<String> coordinateOrderSupplier;

  private Supplier<String> idSupplier;

  private Supplier<String> wfsUrlSupplier;

  private List<FeatureConverterFactoryV110> featureConverterFactories;

  private List<MetacardMapper> metacardToFeatureMappers;

  public XStreamWfsFeatureTransformer(
      Supplier<String> idSupplier,
      Supplier<String> wfsUrlSupplier,
      Supplier<String> coordinateOrderSupplier) {
    this.idSupplier = idSupplier;
    this.wfsUrlSupplier = wfsUrlSupplier;
    this.coordinateOrderSupplier = coordinateOrderSupplier;
    this.metacardToFeatureMappers = Collections.emptyList();
  }

  public XStreamWfsFeatureTransformer() {
    this.idSupplier = () -> "";
    this.wfsUrlSupplier = () -> "";
    this.coordinateOrderSupplier = () -> GeospatialUtil.LAT_LON_ORDER;
    this.metacardToFeatureMappers = Collections.emptyList();
  }

  @Override
  public Optional<Metacard> apply(InputStream inputStream) {
    return Optional.empty();
  }

  public List<MetacardMapper> getMetacardToFeatureMapper() {
    return this.metacardToFeatureMappers;
  }

  public void setMetacardToFeatureMapper(List<MetacardMapper> mappers) {
    this.metacardToFeatureMappers = mappers;
  }

  private void lookupFeatureConverter(
      String ftSimpleName, FeatureMetacardType ftMetacard, String srs) {
    FeatureConverter featureConverter = null;

    /**
     * The list of feature converter factories injected into this class is a live list. So, feature
     * converter factories can be added and removed from the system while running.
     */
    if (org.apache.commons.collections.CollectionUtils.isNotEmpty(featureConverterFactories)) {
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
    // featureCollectionReader.registerConverter(featureConverter);
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

  public void setFeatureConverterFactoryList(List<FeatureConverterFactoryV110> factories) {
    this.featureConverterFactories = factories;
  }

  private void logFeatureType(QName featureType, String message) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(message, MetacardMapper.class.getSimpleName(), featureType);
    }
  }
}
