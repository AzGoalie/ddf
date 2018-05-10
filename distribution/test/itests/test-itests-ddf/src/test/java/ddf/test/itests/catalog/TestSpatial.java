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

    String wfs11GetCapabilities = getFileContent("/TestSpatial/xml/WFS_11_GetCapabilities.xml");
    whenHttp(server)
        .match(
            get(WFS_11_CONTEXT),
            parameter("service", "WFS"),
            parameter("version", "1.1.0"),
            parameter("request", "GetCapabilities"))
        .then(Action.success(), Action.stringContent(wfs11GetCapabilities));
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
