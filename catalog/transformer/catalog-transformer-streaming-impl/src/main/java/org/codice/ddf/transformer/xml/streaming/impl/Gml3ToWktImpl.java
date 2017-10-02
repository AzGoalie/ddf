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
package org.codice.ddf.transformer.xml.streaming.impl;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTWriter;
import ddf.catalog.validation.ValidationException;
import ddf.catalog.validation.impl.ValidationExceptionImpl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import javax.xml.parsers.ParserConfigurationException;
import org.codice.ddf.transformer.xml.streaming.Gml3ToWkt;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.xml.Configuration;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class Gml3ToWktImpl implements Gml3ToWkt {
  private static final Logger LOGGER = LoggerFactory.getLogger(Gml3ToWkt.class);

  private final MathTransform latLonTransform;

  private final org.geotools.xml.Parser parser;

  private final WKTWriter wktWriter = new WKTWriter();

  public Gml3ToWktImpl(Configuration gmlConfiguration) {
    MathTransform transform = null;
    try {
      transform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, CRS.decode("EPSG:4326", false));
    } catch (FactoryException e) {
      LOGGER.warn("Couldn't create lat/lon transform");
    }

    latLonTransform = transform;

    parser = new org.geotools.xml.Parser(gmlConfiguration);
    parser.setStrict(false);
  }

  public String convert(String xml) throws ValidationException {
    try (InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      return convert(stream);
    } catch (IOException e) {
      LOGGER.debug("IO exception during conversion of {}", xml, e);
      throw new ValidationExceptionImpl(
          e, Collections.singletonList("IO exception during conversion"), new ArrayList<String>());
    }
  }

  public String convert(InputStream xml) throws ValidationException {
    return convert(xml, null);
  }

  public String convert(InputStream xml, Function<Object, String> unknownClassCallback)
      throws ValidationException {
    if (latLonTransform == null) {
      LOGGER.debug("Lat/Lon transform is null");
      throw new ValidationExceptionImpl("Unable to transform geometry due to null transform");
    }

    Object parsedObject = parseXml(xml);

    if (parsedObject instanceof Envelope) {
      parsedObject = JTS.toGeometry((Envelope) parsedObject);
    }

    if (parsedObject instanceof Geometry) {
      try {
        Geometry geometry = JTS.transform((Geometry) parsedObject, latLonTransform);
        return wktWriter.write(geometry);
      } catch (TransformException e) {
        LOGGER.debug("Failed to transform geometry to lon/lat", e);
        throw new ValidationExceptionImpl(
            e,
            Collections.singletonList("Cannot transform geometry to lon/lat"),
            new ArrayList<>());
      }
    } else if (unknownClassCallback != null) {
      String result = unknownClassCallback.apply(parsedObject);
      if (result == null) {
        LOGGER.debug("Unknown object parsed from GML and unable to convert to WKT");
        throw new ValidationExceptionImpl(
            "", Collections.singletonList("Couldn't not convert GML to WKT"), new ArrayList<>());
      }
      return result;
    } else {
      LOGGER.debug("Unknown object parsed from GML and unable to convert to WKT");
      throw new ValidationExceptionImpl(
          "", Collections.singletonList("Couldn't not convert GML to WKT"), new ArrayList<>());
    }
  }

  private Object parseXml(InputStream xml) throws ValidationException {
    try {
      return parser.parse(xml);
    } catch (ParserConfigurationException | IOException e) {
      LOGGER.debug("Failed to read gml InputStream", e);
      throw new ValidationExceptionImpl(
          e, Collections.singletonList("Cannot read gml"), new ArrayList<>());
    } catch (SAXException e) {
      LOGGER.debug("Failed to parse gml xml", e);
      throw new ValidationExceptionImpl(
          e, Collections.singletonList("Cannot parse gml xml"), new ArrayList<>());
    }
  }
}
