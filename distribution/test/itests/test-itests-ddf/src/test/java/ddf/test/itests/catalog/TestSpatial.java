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
package ddf.test.itests.catalog;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Condition.get;
import static com.xebialabs.restito.semantics.Condition.parameter;
import static ddf.catalog.Constants.DEFAULT_PAGE_SIZE;
import static org.codice.ddf.itests.common.catalog.CatalogTestCommons.ingestCswRecord;
import static org.codice.ddf.itests.common.catalog.CatalogTestCommons.ingestMetacards;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.specification.RequestSpecification;
import com.xebialabs.restito.semantics.Action;
import com.xebialabs.restito.server.StubServer;
import ddf.catalog.data.types.Location;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.http.HttpStatus;
import org.codice.ddf.itests.common.AbstractIntegrationTest;
import org.codice.ddf.itests.common.XmlSearch;
import org.codice.ddf.itests.common.annotations.ConditionalIgnoreRule;
import org.codice.ddf.itests.common.annotations.ConditionalIgnoreRule.ConditionalIgnore;
import org.codice.ddf.itests.common.annotations.SkipUnstableTest;
import org.codice.ddf.test.common.LoggingUtils;
import org.codice.ddf.test.common.annotations.BeforeExam;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.osgi.framework.FrameworkUtil;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TestSpatial extends AbstractIntegrationTest {

  private static final String CSW_RESPONSE_COUNTRY_CODE = "GBR";

  private static final String CSW_RESOURCE_ROOT = "/TestSpatial/";

  private static final String CSW_QUERY_RESOURCES = CSW_RESOURCE_ROOT + "csw/request/query/";

  private static final String CSW_PAGING_METACARD =
      CSW_RESOURCE_ROOT + "csw/record/CswPagingRecord.xml";

  private static final String CSW_METACARD = "CswRecord.xml";

  private static final String GEOJSON_NEAR_METACARD = "GeoJson near";

  private static final String GEOJSON_FAR_METACARD = "GeoJson far";

  private static final String PLAINXML_NEAR_METACARD = "PlainXml near";

  private static final String PLAINXML_FAR_METACARD = "PlainXml far";

  private static final String TEXT_XML_UTF_8 = "text/xml;charset=UTF-8";

  private static final String WFS_11_SYMBOLIC_NAME = "spatial-wfs-v1_1_0-source";

  private static final String WFS_11_FACTORY_PID = "Wfs_v110_Federated_Source";

  private static final String WFS_11_SOURCE_ID = "WFS 1.1 Source";

  private static final String WFS_11_CONTEXT = "/mockWfs/11";

  private String restitoStubServerPath;

  private StubServer server;

  private static Map<String, String> savedCswQueries = new HashMap<>();

  private static Map<String, String> metacardIds = new HashMap<>();

  private final ImmutableMap<String, ExpectedResultPair[]> cswExpectedResults =
      ImmutableMap.<String, ExpectedResultPair[]>builder()
          .put(
              "CswAfterDateQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswBeforeDateQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_FAR_METACARD)
              })
          .put(
              "CswContainingWktLineStringQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_FAR_METACARD)
              })
          .put(
              "CswContainingWktPolygonQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_FAR_METACARD)
              })
          .put(
              "CswDuringDatesQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_FAR_METACARD)
              })
          .put(
              "CswEqualToTextQuery",
              new ExpectedResultPair[] {new ExpectedResultPair(ResultType.TITLE, CSW_METACARD)})
          .put(
              "CswIntersectingWktLineStringQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswIntersectingWktPolygonQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswLikeTextQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswNearestToWktLineStringQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswNearestToWktPolygonQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD),
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswOverLappingDatesQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswWithinBufferWktLineStringQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswWithinBufferWktPolygonQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswWithinWktPolygonQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswCompoundLikeTextAndIntersectingWktLineString",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswCompoundAfterDateAndIntersectingWktPolygon",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_NEAR_METACARD)
              })
          .put(
              "CswCompoundBeforeDateAndLikeText",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_FAR_METACARD)
              })
          .put(
              "CswCompoundNotBeforeDateAndLikeText",
              new ExpectedResultPair[] {new ExpectedResultPair(ResultType.TITLE, CSW_METACARD)})
          .put(
              "CswLogicalOperatorContextualNotQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD)
              })
          .put(
              "CswLogicalOperatorContextualOrQuery",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_NEAR_METACARD),
                new ExpectedResultPair(ResultType.TITLE, GEOJSON_FAR_METACARD)
              })
          .put(
              "CswXPathExpressionQueryWithAttributeSelector",
              new ExpectedResultPair[] {
                new ExpectedResultPair(ResultType.TITLE, PLAINXML_FAR_METACARD)
              })
          .put(
              "CswXPathExpressionQuery",
              new ExpectedResultPair[] {new ExpectedResultPair(ResultType.TITLE, CSW_METACARD)})
          .put(
              "CswFuzzyTextQuery",
              new ExpectedResultPair[] {new ExpectedResultPair(ResultType.TITLE, CSW_METACARD)})
          .put(
              "CswCompoundNot",
              new ExpectedResultPair[] {new ExpectedResultPair(ResultType.COUNT, "0")})
          .build();

  @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

  /** Loads the resource queries into memory. */
  public static Map<String, String> loadResourceQueries(
      String resourcesPath, Map<String, String> savedQueries) {

    // gets a list of resources available within the resource bundle
    Enumeration<URL> queryResourcePaths =
        FrameworkUtil.getBundle(AbstractIntegrationTest.class)
            .getBundleContext()
            .getBundle()
            .findEntries(resourcesPath, "*", false);

    while (queryResourcePaths.hasMoreElements()) {
      String queryResourcePath = queryResourcePaths.nextElement().getPath();
      if (!queryResourcePath.endsWith("/")) {
        String queryName = queryResourcePath.substring(queryResourcePath.lastIndexOf("/") + 1);
        savedQueries.put(removeFileExtension(queryName), getFileContent(queryResourcePath));
      }
    }
    return savedQueries;
  }

  private static String removeFileExtension(String file) {
    return file.contains(".") ? file.substring(0, file.lastIndexOf(".")) : file;
  }

  @BeforeExam
  public void beforeExam() throws Exception {
    try {
      waitForSystemReady();

      setupMockServer();

      getCatalogBundle().waitForFederatedSource(WFS_11_SOURCE_ID);
      //      getServiceManager().waitForSourcesToBeAvailable();

      loadResourceQueries(CSW_QUERY_RESOURCES, savedCswQueries);
      getServiceManager().startFeature(true, "spatial-wps");
      getServiceManager().startFeature(true, "sample-process");
    } catch (Exception e) {
      LoggingUtils.failWithThrowableStacktrace(e, "Failed to start required apps: ");
    }
  }

  private void setupMockServer() throws IOException {
    DynamicPort restitoStubServerPort =
        new DynamicPort("org.codice.ddf.system.restito_stub_server_port", 6);
    restitoStubServerPath = DynamicUrl.INSECURE_ROOT + restitoStubServerPort.getPort();
    server = new StubServer(Integer.parseInt(restitoStubServerPort.getPort())).run();
    setupWfs11();
  }

  private void setupWfs11() throws IOException {
    FederatedSourceProperties wfs11SourceProperties =
        new FederatedSourceProperties(
            WFS_11_SOURCE_ID, WFS_11_CONTEXT, WFS_11_SYMBOLIC_NAME, WFS_11_FACTORY_PID, "wfsUrl");

    whenHttp(server)
        .match(
            get(WFS_11_CONTEXT),
            parameter("service", "WFS"),
            parameter("version", "1.1.0"),
            parameter("request", "GetCapabilities"))
        .then(
            Action.success(),
            Action.stringContent(
                "<wfs:WFS_Capabilities \n"
                    + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n"
                    + "  xmlns:gml=\"http://www.opengis.net/gml\" \n"
                    + "  xmlns:wfs=\"http://www.opengis.net/wfs\" \n"
                    + "  xmlns:ows=\"http://www.opengis.net/ows\" \n"
                    + "  xmlns:ogc=\"http://www.opengis.net/ogc\" \n"
                    + "  xmlns:xlink=\"https://www.w3.org/1999/xlink\" version=\"1.1.0\" xsi:schemaLocation=\"http://www.opengis.net/wfs http://localhost:8080/geoserver/schemas/wfs/1.1.0/wfs.xsd\" updateSequence=\"152\">\n"
                    + "  <ows:ServiceIdentification>\n"
                    + "    <ows:Title>GeoServer Web Feature Service</ows:Title>\n"
                    + "    <ows:Abstract>This is the reference implementation of WFS 1.0.0 and WFS 1.1.0, supports all WFS operations including Transaction.</ows:Abstract>\n"
                    + "    <ows:Keywords>\n"
                    + "      <ows:Keyword>WFS</ows:Keyword>\n"
                    + "      <ows:Keyword>WMS</ows:Keyword>\n"
                    + "      <ows:Keyword>GEOSERVER</ows:Keyword>\n"
                    + "    </ows:Keywords>\n"
                    + "    <ows:ServiceType>WFS</ows:ServiceType>\n"
                    + "    <ows:ServiceTypeVersion>1.1.0</ows:ServiceTypeVersion>\n"
                    + "    <ows:Fees>NONE</ows:Fees>\n"
                    + "    <ows:AccessConstraints>NONE</ows:AccessConstraints>\n"
                    + "  </ows:ServiceIdentification>\n"
                    + "  <ows:ServiceProvider>\n"
                    + "    <ows:ProviderName>The Ancient Geographers</ows:ProviderName>\n"
                    + "    <ows:ServiceContact>\n"
                    + "      <ows:IndividualName>Claudius Ptolomaeus</ows:IndividualName>\n"
                    + "      <ows:PositionName>Chief Geographer</ows:PositionName>\n"
                    + "      <ows:ContactInfo>\n"
                    + "        <ows:Phone>\n"
                    + "          <ows:Voice/>\n"
                    + "          <ows:Facsimile/>\n"
                    + "        </ows:Phone>\n"
                    + "        <ows:Address>\n"
                    + "          <ows:DeliveryPoint/>\n"
                    + "          <ows:City>Alexandria</ows:City>\n"
                    + "          <ows:AdministrativeArea/>\n"
                    + "          <ows:PostalCode/>\n"
                    + "          <ows:Country>Egypt</ows:Country>\n"
                    + "          <ows:ElectronicMailAddress>claudius.ptolomaeus@gmail.com</ows:ElectronicMailAddress>\n"
                    + "        </ows:Address>\n"
                    + "      </ows:ContactInfo>\n"
                    + "    </ows:ServiceContact>\n"
                    + "  </ows:ServiceProvider>\n"
                    + "  <ows:OperationsMetadata>\n"
                    + "    <ows:Operation name=\"GetCapabilities\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"AcceptVersions\">\n"
                    + "        <ows:Value>1.0.0</ows:Value>\n"
                    + "        <ows:Value>1.1.0</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"AcceptFormats\">\n"
                    + "        <ows:Value>text/xml</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"Sections\">\n"
                    + "        <ows:Value>ServiceIdentification</ows:Value>\n"
                    + "        <ows:Value>ServiceProvider</ows:Value>\n"
                    + "        <ows:Value>OperationsMetadata</ows:Value>\n"
                    + "        <ows:Value>FeatureTypeList</ows:Value>\n"
                    + "        <ows:Value>Filter_Capabilities</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"DescribeFeatureType\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"outputFormat\">\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.1.1</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"GetFeature\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"resultType\">\n"
                    + "        <ows:Value>results</ows:Value>\n"
                    + "        <ows:Value>hits</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"outputFormat\">\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.1.1</ows:Value>\n"
                    + "        <ows:Value>GML2</ows:Value>\n"
                    + "        <ows:Value>KML</ows:Value>\n"
                    + "        <ows:Value>SHAPE-ZIP</ows:Value>\n"
                    + "        <ows:Value>application/gml+xml; version=3.2</ows:Value>\n"
                    + "        <ows:Value>application/json</ows:Value>\n"
                    + "        <ows:Value>application/vnd.google-earth.kml xml</ows:Value>\n"
                    + "        <ows:Value>application/vnd.google-earth.kml+xml</ows:Value>\n"
                    + "        <ows:Value>csv</ows:Value>\n"
                    + "        <ows:Value>gml3</ows:Value>\n"
                    + "        <ows:Value>gml32</ows:Value>\n"
                    + "        <ows:Value>json</ows:Value>\n"
                    + "        <ows:Value>text/xml; subtype=gml/2.1.2</ows:Value>\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.2</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Constraint name=\"LocalTraverseXLinkScope\">\n"
                    + "        <ows:Value>2</ows:Value>\n"
                    + "      </ows:Constraint>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"GetGmlObject\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"LockFeature\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"releaseAction\">\n"
                    + "        <ows:Value>ALL</ows:Value>\n"
                    + "        <ows:Value>SOME</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"GetFeatureWithLock\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"resultType\">\n"
                    + "        <ows:Value>results</ows:Value>\n"
                    + "        <ows:Value>hits</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"outputFormat\">\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.1.1</ows:Value>\n"
                    + "        <ows:Value>GML2</ows:Value>\n"
                    + "        <ows:Value>KML</ows:Value>\n"
                    + "        <ows:Value>SHAPE-ZIP</ows:Value>\n"
                    + "        <ows:Value>application/gml+xml; version=3.2</ows:Value>\n"
                    + "        <ows:Value>application/json</ows:Value>\n"
                    + "        <ows:Value>application/vnd.google-earth.kml xml</ows:Value>\n"
                    + "        <ows:Value>application/vnd.google-earth.kml+xml</ows:Value>\n"
                    + "        <ows:Value>csv</ows:Value>\n"
                    + "        <ows:Value>gml3</ows:Value>\n"
                    + "        <ows:Value>gml32</ows:Value>\n"
                    + "        <ows:Value>json</ows:Value>\n"
                    + "        <ows:Value>text/xml; subtype=gml/2.1.2</ows:Value>\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.2</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "    </ows:Operation>\n"
                    + "    <ows:Operation name=\"Transaction\">\n"
                    + "      <ows:DCP>\n"
                    + "        <ows:HTTP>\n"
                    + "          <ows:Get xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "          <ows:Post xlink:href=\"http://localhost:8080/geoserver/wfs\"/>\n"
                    + "        </ows:HTTP>\n"
                    + "      </ows:DCP>\n"
                    + "      <ows:Parameter name=\"inputFormat\">\n"
                    + "        <ows:Value>text/xml; subtype=gml/3.1.1</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"idgen\">\n"
                    + "        <ows:Value>GenerateNew</ows:Value>\n"
                    + "        <ows:Value>UseExisting</ows:Value>\n"
                    + "        <ows:Value>ReplaceDuplicate</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "      <ows:Parameter name=\"releaseAction\">\n"
                    + "        <ows:Value>ALL</ows:Value>\n"
                    + "        <ows:Value>SOME</ows:Value>\n"
                    + "      </ows:Parameter>\n"
                    + "    </ows:Operation>\n"
                    + "  </ows:OperationsMetadata>\n"
                    + "  <FeatureTypeList>\n"
                    + "    <Operations>\n"
                    + "      <Operation>Query</Operation>\n"
                    + "      <Operation>Insert</Operation>\n"
                    + "      <Operation>Update</Operation>\n"
                    + "      <Operation>Delete</Operation>\n"
                    + "      <Operation>Lock</Operation>\n"
                    + "    </Operations>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>tiger:poly_landmarks</Name>\n"
                    + "      <Title>Manhattan (NY) landmarks</Title>\n"
                    + "      <Abstract>Manhattan landmarks, identifies water, lakes, parks, interesting buildilngs</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>landmarks</ows:Keyword>\n"
                    + "        <ows:Keyword>DS_poly_landmarks</ows:Keyword>\n"
                    + "        <ows:Keyword>manhattan</ows:Keyword>\n"
                    + "        <ows:Keyword>poly_landmarks</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-74.047185 40.679648</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-73.90782 40.882078</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>tiger:poi</Name>\n"
                    + "      <Title>Manhattan (NY) points of interest</Title>\n"
                    + "      <Abstract>Points of interest in New York, New York (on Manhattan). One of the attributes contains the name of a file with a picture of the point of interest.</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>poi</ows:Keyword>\n"
                    + "        <ows:Keyword>Manhattan</ows:Keyword>\n"
                    + "        <ows:Keyword>DS_poi</ows:Keyword>\n"
                    + "        <ows:Keyword>points_of_interest</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-74.0118315772888 40.70754683896324</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-74.00857344353275 40.711945649065406</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>tiger:tiger_roads</Name>\n"
                    + "      <Title>Manhattan (NY) roads</Title>\n"
                    + "      <Abstract>Highly simplified road layout of Manhattan in New York..</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>DS_tiger_roads</ows:Keyword>\n"
                    + "        <ows:Keyword>tiger_roads</ows:Keyword>\n"
                    + "        <ows:Keyword>roads</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-74.02722 40.684221</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-73.907005 40.878178</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>sf:archsites</Name>\n"
                    + "      <Title>Spearfish archeological sites</Title>\n"
                    + "      <Abstract>Sample data from GRASS, archeological sites location, Spearfish, South Dakota, USA</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>archsites</ows:Keyword>\n"
                    + "        <ows:Keyword>spearfish</ows:Keyword>\n"
                    + "        <ows:Keyword>sfArchsites</ows:Keyword>\n"
                    + "        <ows:Keyword>archeology</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:26713</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-103.8725637911543 44.37740330855979</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-103.63794182141925 44.48804280772808</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>sf:bugsites</Name>\n"
                    + "      <Title>Spearfish bug locations</Title>\n"
                    + "      <Abstract>Sample data from GRASS, bug sites location, Spearfish, South Dakota, USA</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>spearfish</ows:Keyword>\n"
                    + "        <ows:Keyword>sfBugsites</ows:Keyword>\n"
                    + "        <ows:Keyword>insects</ows:Keyword>\n"
                    + "        <ows:Keyword>bugsites</ows:Keyword>\n"
                    + "        <ows:Keyword>tiger_beetles</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:26713</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-103.86796131703647 44.373938816704396</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-103.63773523234195 44.43418821380063</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>sf:restricted</Name>\n"
                    + "      <Title>Spearfish restricted areas</Title>\n"
                    + "      <Abstract>Sample data from GRASS, restricted areas, Spearfish, South Dakota, USA</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>spearfish</ows:Keyword>\n"
                    + "        <ows:Keyword>restricted</ows:Keyword>\n"
                    + "        <ows:Keyword>areas</ows:Keyword>\n"
                    + "        <ows:Keyword>sfRestricted</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:26713</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-103.85057172920756 44.39436387625042</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-103.74741494853805 44.48215752041131</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>sf:roads</Name>\n"
                    + "      <Title>Spearfish roads</Title>\n"
                    + "      <Abstract>Sample data from GRASS, road layout, Spearfish, South Dakota, USA</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>sfRoads</ows:Keyword>\n"
                    + "        <ows:Keyword>spearfish</ows:Keyword>\n"
                    + "        <ows:Keyword>roads</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:26713</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-103.87741691493184 44.37087275281798</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-103.62231404880659 44.50015918338962</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>sf:streams</Name>\n"
                    + "      <Title>Spearfish streams</Title>\n"
                    + "      <Abstract>Sample data from GRASS, streams, Spearfish, South Dakota, USA</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>spearfish</ows:Keyword>\n"
                    + "        <ows:Keyword>sfStreams</ows:Keyword>\n"
                    + "        <ows:Keyword>streams</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:26713</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-103.87789019829768 44.372335260095554</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-103.62287788915457 44.502218486214815</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>topp:tasmania_cities</Name>\n"
                    + "      <Title>Tasmania cities</Title>\n"
                    + "      <Abstract>Cities in Tasmania (actually, just the capital)</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>cities</ows:Keyword>\n"
                    + "        <ows:Keyword>Tasmania</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>145.19754 -43.423512</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>148.27298000000002 -40.852802</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>topp:tasmania_roads</Name>\n"
                    + "      <Title>Tasmania roads</Title>\n"
                    + "      <Abstract>Main Tasmania roads</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>Roads</ows:Keyword>\n"
                    + "        <ows:Keyword>Tasmania</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>145.19754 -43.423512</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>148.27298000000002 -40.852802</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>topp:tasmania_state_boundaries</Name>\n"
                    + "      <Title>Tasmania state boundaries</Title>\n"
                    + "      <Abstract>Tasmania state boundaries</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>boundaries</ows:Keyword>\n"
                    + "        <ows:Keyword>tasmania_state_boundaries</ows:Keyword>\n"
                    + "        <ows:Keyword>Tasmania</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>143.83482400000003 -43.648056</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>148.47914100000003 -39.573891</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>topp:tasmania_water_bodies</Name>\n"
                    + "      <Title>Tasmania water bodies</Title>\n"
                    + "      <Abstract>Tasmania water bodies</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>Lakes</ows:Keyword>\n"
                    + "        <ows:Keyword>Bodies</ows:Keyword>\n"
                    + "        <ows:Keyword>Australia</ows:Keyword>\n"
                    + "        <ows:Keyword>Water</ows:Keyword>\n"
                    + "        <ows:Keyword>Tasmania</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>145.97161899999998 -43.031944</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>147.219696 -41.775558</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>topp:states</Name>\n"
                    + "      <Title>USA Population</Title>\n"
                    + "      <Abstract>This is some census data on the states.</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>census</ows:Keyword>\n"
                    + "        <ows:Keyword>united</ows:Keyword>\n"
                    + "        <ows:Keyword>boundaries</ows:Keyword>\n"
                    + "        <ows:Keyword>state</ows:Keyword>\n"
                    + "        <ows:Keyword>states</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-124.731422 24.955967</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>-66.969849 49.371735</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "    <FeatureType>\n"
                    + "      <Name>tiger:giant_polygon</Name>\n"
                    + "      <Title>World rectangle</Title>\n"
                    + "      <Abstract>A simple rectangular polygon covering most of the world, it's only used for the purpose of providing a background (WMS bgcolor could be used instead)</Abstract>\n"
                    + "      <ows:Keywords>\n"
                    + "        <ows:Keyword>DS_giant_polygon</ows:Keyword>\n"
                    + "        <ows:Keyword>giant_polygon</ows:Keyword>\n"
                    + "      </ows:Keywords>\n"
                    + "      <DefaultSRS>urn:x-ogc:def:crs:EPSG:4326</DefaultSRS>\n"
                    + "      <ows:WGS84BoundingBox>\n"
                    + "        <ows:LowerCorner>-180.0 -90.0</ows:LowerCorner>\n"
                    + "        <ows:UpperCorner>180.0 90.0</ows:UpperCorner>\n"
                    + "      </ows:WGS84BoundingBox>\n"
                    + "    </FeatureType>\n"
                    + "  </FeatureTypeList>\n"
                    + "  <ogc:Filter_Capabilities>\n"
                    + "    <ogc:Spatial_Capabilities>\n"
                    + "      <ogc:GeometryOperands>\n"
                    + "        <ogc:GeometryOperand>gml:Envelope</ogc:GeometryOperand>\n"
                    + "        <ogc:GeometryOperand>gml:Point</ogc:GeometryOperand>\n"
                    + "        <ogc:GeometryOperand>gml:LineString</ogc:GeometryOperand>\n"
                    + "        <ogc:GeometryOperand>gml:Polygon</ogc:GeometryOperand>\n"
                    + "      </ogc:GeometryOperands>\n"
                    + "      <ogc:SpatialOperators>\n"
                    + "        <ogc:SpatialOperator name=\"Disjoint\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Equals\"/>\n"
                    + "        <ogc:SpatialOperator name=\"DWithin\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Beyond\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Intersects\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Touches\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Crosses\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Within\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Contains\"/>\n"
                    + "        <ogc:SpatialOperator name=\"Overlaps\"/>\n"
                    + "        <ogc:SpatialOperator name=\"BBOX\"/>\n"
                    + "      </ogc:SpatialOperators>\n"
                    + "    </ogc:Spatial_Capabilities>\n"
                    + "    <ogc:Scalar_Capabilities>\n"
                    + "      <ogc:LogicalOperators/>\n"
                    + "      <ogc:ComparisonOperators>\n"
                    + "        <ogc:ComparisonOperator>LessThan</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>GreaterThan</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>LessThanEqualTo</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>GreaterThanEqualTo</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>EqualTo</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>NotEqualTo</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>Like</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>Between</ogc:ComparisonOperator>\n"
                    + "        <ogc:ComparisonOperator>NullCheck</ogc:ComparisonOperator>\n"
                    + "      </ogc:ComparisonOperators>\n"
                    + "      <ogc:ArithmeticOperators>\n"
                    + "        <ogc:SimpleArithmetic/>\n"
                    + "        <ogc:Functions>\n"
                    + "          <ogc:FunctionNames>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">abs</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">abs_2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">abs_3</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">abs_4</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">acos</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">AddCoverages</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">Affine</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">Aggregate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Area</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">area2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">AreaGrid</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">asin</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">atan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">atan2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">attributeCount</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">BandMerge</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">BandSelect</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-6\">BarnesSurface</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">between</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">boundary</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">boundaryDimension</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">boundedBy</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Bounds</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">buffer</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">BufferFeatureCollection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">bufferWithSegments</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"7\">Categorize</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">ceil</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">centroid</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">classify</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">ClassifyByRange</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">Clip</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">CollectGeometries</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Average</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Bounds</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">Collection_Count</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Max</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Median</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Min</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Nearest</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Sum</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Collection_Unique</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">Concatenate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">contains</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">Contour</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">contrast</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">convert</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">convexHull</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">ConvolveCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">cos</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">Count</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">CoverageClassStats</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">CropCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">crosses</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">darken</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">dateFormat</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">dateParse</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">desaturate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">difference</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">dimension</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">disjoint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">disjoint3D</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">distance</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">distance3D</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">double2bool</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">endAngle</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">endPoint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">env</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">envelope</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">EqualInterval</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">equalsExact</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">equalsExactTolerance</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">equalTo</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">exp</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">exteriorRing</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">Feature</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">FeatureClassStats</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">floor</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">geometry</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">geometryType</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">geomFromWKT</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">geomLength</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">getGeometryN</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">getX</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">getY</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">getz</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">grayscale</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">greaterEqualThan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">greaterThan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-3\">Grid</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-5\">Heatmap</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">hsl</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">id</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">IEEEremainder</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">if_then_else</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">in</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"11\">in10</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">in2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"4\">in3</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"5\">in4</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"6\">in5</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"7\">in6</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"8\">in7</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"9\">in8</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"10\">in9</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">InclusionFeatureCollection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">int2bbool</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">int2ddouble</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">interiorPoint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">interiorRingN</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-5\">Interpolate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">intersection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">IntersectionFeatureCollection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">intersects</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">intersects3D</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isClosed</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">isCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isEmpty</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isInstanceOf</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">isLike</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isNull</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">isometric</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isRing</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isSimple</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">isValid</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">isWithinDistance</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">isWithinDistance3D</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">Jenks</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">length</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">lessEqualThan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">lessThan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">lighten</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">list</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">listMultiply</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">log</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"4\">LRSGeocode</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-4\">LRSMeasure</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"5\">LRSSegment</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">max</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">max_2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">max_3</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">max_4</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">min</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">min_2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">min_3</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">min_4</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">mincircle</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">minimumdiameter</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">minrectangle</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">mix</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">modulo</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">MultiplyCoverages</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">Nearest</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">NormalizeCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">not</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">notEqualTo</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">numberFormat</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"5\">numberFormat2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">numGeometries</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">numInteriorRing</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">numPoints</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">octagonalenvelope</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">offset</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">overlaps</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">parameter</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">parseBoolean</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">parseDouble</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">parseInt</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">parseLong</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">pi</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">PointBuffers</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">pointN</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-7\">PointStacker</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">PolygonExtraction</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">pow</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">property</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">PropertyExists</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">Quantile</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">Query</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"0\">random</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">RangeLookup</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">RasterAsPointCollection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">RasterZonalStatistics</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-6\">RasterZonalStatistics2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"5\">Recode</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">RectangularClip</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">relate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">relatePattern</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">Reproject</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-3\">rescaleToPixels</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">rint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">round</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">round_2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">roundDouble</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">saturate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-5\">ScaleCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">setCRS</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">shade</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">Simplify</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">sin</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-2\">Snap</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">spin</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">sqrt</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">StandardDeviation</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">startAngle</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">startPoint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">strCapitalize</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strConcat</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strEndsWith</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strEqualsIgnoreCase</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strIndexOf</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"4\">stringTemplate</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strLastIndexOf</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">strLength</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strMatches</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">strPosition</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"4\">strReplace</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strStartsWith</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">strSubstring</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">strSubstringStart</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">strToLowerCase</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">strToUpperCase</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">strTrim</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">strTrim2</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-1\">strURLEncode</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">StyleCoverage</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">symDifference</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">tan</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">tint</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">toDegrees</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">toRadians</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">touches</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">toWKT</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">Transform</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">TransparencyFill</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">union</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">UnionFeatureCollection</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">Unique</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">UniqueInterval</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"-4\">VectorToRaster</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"3\">VectorZonalStatistics</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"1\">vertices</ogc:FunctionName>\n"
                    + "            <ogc:FunctionName nArgs=\"2\">within</ogc:FunctionName>\n"
                    + "          </ogc:FunctionNames>\n"
                    + "        </ogc:Functions>\n"
                    + "      </ogc:ArithmeticOperators>\n"
                    + "    </ogc:Scalar_Capabilities>\n"
                    + "    <ogc:Id_Capabilities>\n"
                    + "      <ogc:FID/>\n"
                    + "      <ogc:EID/>\n"
                    + "    </ogc:Id_Capabilities>\n"
                    + "  </ogc:Filter_Capabilities>\n"
                    + "</wfs:WFS_Capabilities>"));

    getServiceManager().createManagedService(WFS_11_FACTORY_PID, wfs11SourceProperties);
  }

  @After
  public void tearDown() throws Exception {
    clearCatalog();
  }

  @Test
  public void testCswPagingQuery() throws Exception {
    // Set to internal paging size
    int pageSize = 500;
    Set<String> ingestIds = ingestPagingRecords(pageSize + 11);
    int ingestCount = ingestIds.size();
    assertThat("Ingest count not equal to expected", ingestCount, is(pageSize + 11));

    ImmutableList<Integer> maxSizes =
        ImmutableList.of(
            DEFAULT_PAGE_SIZE - 5,
            DEFAULT_PAGE_SIZE,
            pageSize - 5,
            pageSize,
            pageSize + 7,
            ingestCount,
            ingestCount + 1);

    for (Integer maxSize : maxSizes) {
      String query = getPagingMaxRecordsQuery(maxSize);
      String cswResponse = sendCswQuery(query);

      int expectedResults = (maxSize <= ingestCount) ? maxSize : ingestCount;

      assertTrue(
          "The responses contained a different number of matches; expected " + ingestCount,
          hasExpectedMatchCount(
              cswResponse, new ExpectedResultPair(ResultType.COUNT, ingestCount + "")));

      assertTrue(
          "The responses contained a different result count; expected " + expectedResults,
          hasExpectedResultCount(
              cswResponse, new ExpectedResultPair(ResultType.COUNT, expectedResults + "")));
    }
  }

  @Test
  public void testCswAfterDateQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswAfterDateQuery");
  }

  @Test
  public void testCswBeforeDateQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswBeforeDateQuery");
  }

  @Test
  public void testCswContainingWktLineStringQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswContainingWktLineStringQuery");
  }

  @Test
  public void testCswContainingWktPolygonQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswContainingWktPolygonQuery");
  }

  @Test
  public void testCswDuringDatesQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswDuringDatesQuery");
  }

  @Test
  public void testCswEqualToTextQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswEqualToTextQuery");
  }

  @Test
  public void testCswIntersectingWktLineStringQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswIntersectingWktLineStringQuery");
  }

  @Test
  public void testCswIntersectingWktPolygonQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswIntersectingWktPolygonQuery");
  }

  @Test
  public void testCswLikeTextQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswLikeTextQuery");
  }

  @Test
  public void testCswNearestToWktLineStringQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswNearestToWktLineStringQuery");
  }

  @Test
  public void testCswNearestToWktPolygonQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswNearestToWktPolygonQuery");
  }

  @Test
  public void testCswOverLappingDatesQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswOverLappingDatesQuery");
  }

  @Test
  public void testCswWithinBufferWktLineStringQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswWithinBufferWktLineStringQuery");
  }

  @Test
  public void testCswWithinBufferWktPolygonQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswWithinBufferWktPolygonQuery");
  }

  @Test
  public void testCswWithinWktPolygonQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswWithinWktPolygonQuery");
  }

  @Test
  public void testCswCompoundAfterDateAndIntersectingWktPolygon() throws Exception {

    performQueryAndValidateExpectedResults("CswCompoundAfterDateAndIntersectingWktPolygon");
  }

  @Test
  public void testCswCompoundBeforeDateAndLikeText() throws Exception {

    performQueryAndValidateExpectedResults("CswCompoundBeforeDateAndLikeText");
  }

  @Test
  public void testCswCompoundNotBeforeDateAndLikeText() throws Exception {

    performQueryAndValidateExpectedResults("CswCompoundNotBeforeDateAndLikeText");
  }

  @Test
  public void testCswCompoundLikeTextAndIntersectingWktLineString() throws Exception {

    performQueryAndValidateExpectedResults("CswCompoundLikeTextAndIntersectingWktLineString");
  }

  @Test
  public void testCswLogicalOperatorContextualNotQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswLogicalOperatorContextualNotQuery");
  }

  @Test
  public void testCswLogicalOperatorContextualOrQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswLogicalOperatorContextualOrQuery");
  }

  @Test
  public void testCswXPathExpressionQueryWithAttributeSelector() throws Exception {

    performQueryAndValidateExpectedResults("CswXPathExpressionQueryWithAttributeSelector");
  }

  @Test
  public void testCswXPathExpressionQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswXPathExpressionQuery");
  }

  @Test
  public void testCswCompoundNot() throws Exception {

    performQueryAndValidateExpectedResults("CswCompoundNot");
  }

  @Test
  public void testCswFuzzyTextQuery() throws Exception {

    performQueryAndValidateExpectedResults("CswFuzzyTextQuery");
  }

  @Test
  @ConditionalIgnore(condition = SkipUnstableTest.class) // DDF-3032
  public void testGeoCoderPlugin() throws Exception {
    getServiceManager().startFeature(true, "webservice-gazetteer");
    String id = ingestCswRecord(getFileContent(CSW_RECORD_RESOURCE_PATH + "/CswRecord2"));

    String queryUrl = REST_PATH.getUrl() + "sources/ddf.distribution/" + id + "?format=xml";

    when()
        .get(queryUrl)
        .then()
        .log()
        .all()
        .and()
        .assertThat()
        .body(
            hasXPath(
                "/metacard/string[@name='" + Location.COUNTRY_CODE + "']/value/text()",
                equalTo(CSW_RESPONSE_COUNTRY_CODE)));
  }

  @Test
  public void testWpsGetCapabilities() throws Exception {
    given()
        .get(SERVICE_ROOT.getUrl() + "/wps?service=WPS&request=GetCapabilities")
        .then()
        .log()
        .all()
        .assertThat()
        .body(
            hasXPath(
                "count(/*[local-name()='Capabilities']/*[local-name()='Contents']/*[local-name()='ProcessSummary'])",
                Matchers.is("5")),
            hasXPath(
                "/*[local-name()='Capabilities']/*[local-name()='Contents']/*[local-name()='ProcessSummary']/*[local-name()='Identifier' and text()='geojson']"));
  }

  @Test
  public void testWpsDescribeProcess() throws Exception {
    given()
        .get(SERVICE_ROOT.getUrl() + "/wps?service=WPS&request=DescribeProcess&identifier=geojson")
        .then()
        .log()
        .all()
        .assertThat()
        .body(
            hasXPath(
                "count(/*[local-name()='ProcessOfferings']/*[local-name()='ProcessOffering'])",
                Matchers.is("1")),
            hasXPath(
                "/*[local-name()='ProcessOfferings']/*[local-name()='ProcessOffering']/*[local-name()='Process']/*[local-name()='Identifier' and text()='geojson']"));
  }

  @Test
  public void testWpsExecute() throws Exception {
    ingestMetacards(metacardIds);
    final String requestXml =
        getFileContent(
            "ExecuteRequest.xml",
            ImmutableMap.of(
                "id", "csw:Record", "metacardId", metacardIds.get(PLAINXML_NEAR_METACARD)),
            AbstractIntegrationTest.class);
    given()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML)
        .body(requestXml)
        .post(SERVICE_ROOT.getUrl() + "/wps?service=WPS&request=Execute")
        .then()
        .log()
        .all()
        .assertThat()
        .body(
            hasXPath(
                "count(/*[local-name()='Result']/*[local-name()='Output'])", Matchers.is("1")));
  }

  @Test
  public void testWfs11() throws InterruptedException {
    String a = "";
    String b = "";
  }

  /**
   * Ingests data, performs and validates the query returns the correct results.
   *
   * @param queryType - The query that is performed
   */
  private void performQueryAndValidateExpectedResults(String queryType) throws Exception {
    ingestMetacards(metacardIds);

    String cswQuery = savedCswQueries.get(queryType);

    String cswResponse = sendCswQuery(cswQuery);

    hasExpectedResults(cswResponse, cswExpectedResults.get(queryType));
  }

  private String sendCswQuery(String query) {
    RequestSpecification queryRequest = given().body(query);

    queryRequest = queryRequest.header("Content-Type", TEXT_XML_UTF_8);

    return queryRequest
        .when()
        .log()
        .all()
        .post(CSW_PATH.getUrl())
        .then()
        .log()
        .all()
        .assertThat()
        .statusCode(equalTo(HttpStatus.SC_OK))
        .extract()
        .response()
        .getBody()
        .asString();
  }

  /**
   * Validates that the results from the query are correct.
   *
   * @param queryResult - The result obtained from sending the query
   * @param expectedValues - The values expected within the results
   */
  private void hasExpectedResults(String queryResult, ExpectedResultPair[] expectedValues)
      throws Exception {
    if (expectedValues[0].type == ResultType.COUNT) {
      assertTrue(
          "The responses contained a different count",
          hasExpectedResultCount(queryResult, expectedValues[0]));
    } else if (expectedValues[0].type == ResultType.TITLE) {
      // assertion done within the method
      hasExpectedMetacardsReturned(queryResult, expectedValues);
    } else {
      assertTrue("The expected values are an invalid type", false);
    }
  }

  /**
   * Validates that the query returned the expected metacards.
   *
   * @param queryResult - The result obtained from sending the query
   * @param expectedValues - The values expected within the result
   */
  private boolean hasExpectedMetacardsReturned(
      String queryResult, ExpectedResultPair[] expectedValues) throws Exception {
    boolean testPassed = false;

    for (int i = 0; i < expectedValues.length; i++) {
      assertTrue(
          "Metacard: " + expectedValues[i].value + " not found in result.",
          testPassed = queryResult.contains(metacardIds.get(expectedValues[i].value)));
    }
    return testPassed;
  }

  /**
   * Validates that the query matched the expected result count.
   *
   * @param queryResult - The result obtained from sending the query
   * @param expectedValue - The values expected within the result
   * @return true if the {@code numberOfRecordsMatched} matches the expected value
   * @throws Exception if an error occurs parsing the XML response
   */
  private boolean hasExpectedMatchCount(String queryResult, ExpectedResultPair expectedValue)
      throws Exception {

    String originalCount = XmlSearch.evaluate("//@numberOfRecordsMatched", queryResult);

    return originalCount.equals(expectedValue.value);
  }

  /**
   * Validates that the query returned the expected result count.
   *
   * @param queryResult - The result obtained from sending the query
   * @param expectedValue - The values expected within the result
   * @return true if the {@code numberOfRecordsReturned} matches the expected value
   * @throws Exception if an error occurs parsing the XML response
   */
  private boolean hasExpectedResultCount(String queryResult, ExpectedResultPair expectedValue)
      throws Exception {

    String originalCount = XmlSearch.evaluate("//@numberOfRecordsReturned", queryResult);

    return originalCount.equals(expectedValue.value);
  }

  private String getPagingMaxRecordsQuery(int maxRecords) {
    String rawCswQuery = savedCswQueries.get("CswPagingTestLikeQuery");
    StrSubstitutor strSubstitutor =
        new StrSubstitutor(ImmutableMap.of("maxRecords", "" + maxRecords));

    strSubstitutor.setVariablePrefix(RESOURCE_VARIABLE_DELIMETER);
    strSubstitutor.setVariableSuffix(RESOURCE_VARIABLE_DELIMETER);
    return strSubstitutor.replace(rawCswQuery);
  }

  private Set<String> ingestPagingRecords(int number) {
    String rawCswRecord = getFileContent(CSW_PAGING_METACARD);

    Set<String> pagingIds = new HashSet<>();
    for (int i = 1; i <= number; i++) {
      String identifier = UUID.randomUUID().toString().replaceAll("-", "");
      String cswPagingRecord = substitutePagingParams(rawCswRecord, i, identifier);

      String id = ingestCswRecord(cswPagingRecord);

      pagingIds.add(id);
      metacardIds.put(id, id);
    }

    return pagingIds;
  }

  private String substitutePagingParams(String rawCswRecord, int testNum, String identifier) {
    StrSubstitutor strSubstitutor =
        new StrSubstitutor(ImmutableMap.of("identifier", identifier, "testNum", "" + testNum));

    strSubstitutor.setVariablePrefix(RESOURCE_VARIABLE_DELIMETER);
    strSubstitutor.setVariableSuffix(RESOURCE_VARIABLE_DELIMETER);
    return strSubstitutor.replace(rawCswRecord);
  }

  public enum ResultType {
    TITLE,
    COUNT
  }

  public class ExpectedResultPair {

    String value;

    ResultType type;

    public ExpectedResultPair(ResultType type, String value) {
      this.value = value;
      this.type = type;
    }
  }

  public class FederatedSourceProperties extends HashMap<String, Object> {

    public FederatedSourceProperties(
        String sourceId,
        String context,
        String symbolicName,
        String factoryPid,
        String urlPropName) {
      this.putAll(getServiceManager().getMetatypeDefaults(symbolicName, factoryPid));

      this.put("id", sourceId);
      this.put(urlPropName, restitoStubServerPath + context);
      this.put("pollInterval", 1);
    }
  }
}
