/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.rest;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseCommonTestingUtility;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.rest.client.Client;
import org.apache.hadoop.hbase.rest.client.Cluster;
import org.apache.hadoop.hbase.rest.client.Response;
import org.apache.hadoop.hbase.rest.model.CellModel;
import org.apache.hadoop.hbase.rest.model.CellSetModel;
import org.apache.hadoop.hbase.rest.model.RowModel;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.RestTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.hbase.thirdparty.com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.apache.hbase.thirdparty.javax.ws.rs.core.MediaType;

@Category({ RestTests.class, MediumTests.class })
@RunWith(Parameterized.class)
public class TestMultiRowResource {
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestMultiRowResource.class);

  private static final TableName TABLE = TableName.valueOf("TestRowResource");
  private static final String CFA = "a";
  private static final String CFB = "b";
  private static final String COLUMN_1 = CFA + ":1";
  private static final String COLUMN_2 = CFB + ":2";
  private static final String ROW_1 = "testrow5";
  private static final String VALUE_1 = "testvalue5";
  private static final String ROW_2 = "testrow6";
  private static final String VALUE_2 = "testvalue6";

  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final HBaseRESTTestingUtility REST_TEST_UTIL = new HBaseRESTTestingUtility();

  private static final Encoder base64UrlEncoder = java.util.Base64.getUrlEncoder().withoutPadding();

  private static Client client;
  private static Configuration conf;

  private static Header extraHdr = null;
  private static boolean csrfEnabled = true;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return HBaseCommonTestingUtility.BOOLEAN_PARAMETERIZED;
  }

  public TestMultiRowResource(Boolean csrf) {
    csrfEnabled = csrf;
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    conf = TEST_UTIL.getConfiguration();
    conf.setBoolean(RESTServer.REST_CSRF_ENABLED_KEY, csrfEnabled);
    if (csrfEnabled) {
      conf.set(RESTServer.REST_CSRF_BROWSER_USERAGENTS_REGEX_KEY, ".*");
    }
    extraHdr = new BasicHeader(RESTServer.REST_CSRF_CUSTOM_HEADER_DEFAULT, "");
    TEST_UTIL.startMiniCluster();
    REST_TEST_UTIL.startServletContainer(conf);
    client = new Client(new Cluster().add("localhost", REST_TEST_UTIL.getServletPort()));
    Admin admin = TEST_UTIL.getAdmin();
    if (admin.tableExists(TABLE)) {
      return;
    }
    HTableDescriptor htd = new HTableDescriptor(TABLE);
    htd.addFamily(new HColumnDescriptor(CFA));
    htd.addFamily(new HColumnDescriptor(CFB));
    admin.createTable(htd);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    REST_TEST_UTIL.shutdownServletContainer();
    TEST_UTIL.shutdownMiniCluster();
  }

  @Test
  public void testMultiCellGetJSON() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(ROW_1);
    path.append("&row=");
    path.append(ROW_2);

    if (csrfEnabled) {
      Response response = client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1));
      assertEquals(400, response.getCode());
    }

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);
    assertEquals(200, response.getCode());
    assertEquals(Constants.MIMETYPE_JSON, response.getHeader("content-type"));

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

  private void checkMultiCellGetJSON(Response response) throws IOException {
    assertEquals(200, response.getCode());
    assertEquals(Constants.MIMETYPE_JSON, response.getHeader("content-type"));

    ObjectMapper mapper = new JacksonJaxbJsonProvider().locateMapper(CellSetModel.class,
      MediaType.APPLICATION_JSON_TYPE);
    CellSetModel cellSet = mapper.readValue(response.getBody(), CellSetModel.class);

    RowModel rowModel = cellSet.getRows().get(0);
    assertEquals(ROW_1, new String(rowModel.getKey()));
    assertEquals(1, rowModel.getCells().size());
    CellModel cell = rowModel.getCells().get(0);
    assertEquals(COLUMN_1, new String(cell.getColumn()));
    assertEquals(VALUE_1, new String(cell.getValue()));

    rowModel = cellSet.getRows().get(1);
    assertEquals(ROW_2, new String(rowModel.getKey()));
    assertEquals(1, rowModel.getCells().size());
    cell = rowModel.getCells().get(0);
    assertEquals(COLUMN_2, new String(cell.getColumn()));
    assertEquals(VALUE_2, new String(cell.getValue()));
  }

  // See https://issues.apache.org/jira/browse/HBASE-28174
  @Test
  public void testMultiCellGetJSONB64() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    if (csrfEnabled) {
      Response response = client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1));
      assertEquals(400, response.getCode());
    }

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    StringBuilder path = new StringBuilder();
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(encoder.encodeToString(ROW_1.getBytes("UTF-8")));
    path.append("&row=");
    path.append(encoder.encodeToString(ROW_2.getBytes("UTF-8")));
    path.append("&e=b64"); // Specify encoding via query string

    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);

    checkMultiCellGetJSON(response);

    path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(encoder.encodeToString(ROW_1.getBytes("UTF-8")));
    path.append("&row=");
    path.append(encoder.encodeToString(ROW_2.getBytes("UTF-8")));

    Header[] headers = new Header[] { new BasicHeader("Accept", Constants.MIMETYPE_JSON),
      new BasicHeader("Encoding", "b64") // Specify encoding via header
    };
    response = client.get(path.toString(), headers);

    checkMultiCellGetJSON(response);

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

  @Test
  public void testMultiCellGetNoKeys() throws IOException {
    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget");

    Response response = client.get(path.toString(), Constants.MIMETYPE_XML);
    assertEquals(404, response.getCode());
  }

  @Test
  public void testMultiCellGetXML() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(ROW_1);
    path.append("&row=");
    path.append(ROW_2);

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    Response response = client.get(path.toString(), Constants.MIMETYPE_XML);
    assertEquals(200, response.getCode());
    assertEquals(Constants.MIMETYPE_XML, response.getHeader("content-type"));

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

  @Test
  public void testMultiCellGetWithColsJSON() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget");
    path.append("/" + COLUMN_1 + "," + CFB);
    path.append("?row=");
    path.append(ROW_1);
    path.append("&row=");
    path.append(ROW_2);

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);
    assertEquals(200, response.getCode());
    ObjectMapper mapper = new JacksonJaxbJsonProvider().locateMapper(CellSetModel.class,
      MediaType.APPLICATION_JSON_TYPE);
    CellSetModel cellSet = mapper.readValue(response.getBody(), CellSetModel.class);
    assertEquals(2, cellSet.getRows().size());
    assertEquals(ROW_1, Bytes.toString(cellSet.getRows().get(0).getKey()));
    assertEquals(VALUE_1, Bytes.toString(cellSet.getRows().get(0).getCells().get(0).getValue()));
    assertEquals(ROW_2, Bytes.toString(cellSet.getRows().get(1).getKey()));
    assertEquals(VALUE_2, Bytes.toString(cellSet.getRows().get(1).getCells().get(0).getValue()));

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

  @Test
  public void testMultiCellGetJSONNotFound() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(ROW_1);
    path.append("&row=");
    path.append(ROW_2);

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);
    assertEquals(200, response.getCode());
    ObjectMapper mapper = new JacksonJaxbJsonProvider().locateMapper(CellSetModel.class,
      MediaType.APPLICATION_JSON_TYPE);
    CellSetModel cellSet = (CellSetModel) mapper.readValue(response.getBody(), CellSetModel.class);
    assertEquals(1, cellSet.getRows().size());
    assertEquals(ROW_1, Bytes.toString(cellSet.getRows().get(0).getKey()));
    assertEquals(VALUE_1, Bytes.toString(cellSet.getRows().get(0).getCells().get(0).getValue()));
    client.delete(row_5_url, extraHdr);
  }

  @Test
  public void testMultiCellGetWithColsInQueryPathJSON() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(ROW_1);
    path.append("/");
    path.append(COLUMN_1);
    path.append("&row=");
    path.append(ROW_2);
    path.append("/");
    path.append(COLUMN_1);

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);
    assertEquals(200, response.getCode());
    ObjectMapper mapper = new JacksonJaxbJsonProvider().locateMapper(CellSetModel.class,
      MediaType.APPLICATION_JSON_TYPE);
    CellSetModel cellSet = mapper.readValue(response.getBody(), CellSetModel.class);
    assertEquals(1, cellSet.getRows().size());
    assertEquals(ROW_1, Bytes.toString(cellSet.getRows().get(0).getKey()));
    assertEquals(VALUE_1, Bytes.toString(cellSet.getRows().get(0).getCells().get(0).getValue()));

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

  @Test
  public void testMultiCellGetFilterJSON() throws IOException {
    String row_5_url = "/" + TABLE + "/" + ROW_1 + "/" + COLUMN_1;
    String row_6_url = "/" + TABLE + "/" + ROW_2 + "/" + COLUMN_2;

    StringBuilder path = new StringBuilder();
    path.append("/");
    path.append(TABLE);
    path.append("/multiget/?row=");
    path.append(ROW_1);
    path.append("&row=");
    path.append(ROW_2);

    if (csrfEnabled) {
      Response response = client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1));
      assertEquals(400, response.getCode());
    }

    client.post(row_5_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_1), extraHdr);
    client.post(row_6_url, Constants.MIMETYPE_BINARY, Bytes.toBytes(VALUE_2), extraHdr);

    Response response = client.get(path.toString(), Constants.MIMETYPE_JSON);
    assertEquals(200, response.getCode());
    assertEquals(Constants.MIMETYPE_JSON, response.getHeader("content-type"));

    // If the filter is used, then we get the same result
    String positivePath = path.toString() + ("&" + Constants.FILTER_B64 + "=" + base64UrlEncoder
      .encodeToString("PrefixFilter('testrow')".getBytes(StandardCharsets.UTF_8.toString())));
    response = client.get(positivePath, Constants.MIMETYPE_JSON);
    checkMultiCellGetJSON(response);

    // Same with non binary clean param
    positivePath = path.toString() + ("&" + Constants.FILTER + "="
      + URLEncoder.encode("PrefixFilter('testrow')", StandardCharsets.UTF_8.name()));
    response = client.get(positivePath, Constants.MIMETYPE_JSON);
    checkMultiCellGetJSON(response);

    // This filter doesn't match the found rows
    String negativePath = path.toString() + ("&" + Constants.FILTER_B64 + "=" + base64UrlEncoder
      .encodeToString("PrefixFilter('notfound')".getBytes(StandardCharsets.UTF_8.toString())));
    response = client.get(negativePath, Constants.MIMETYPE_JSON);
    assertEquals(404, response.getCode());

    // Same with non binary clean param
    negativePath = path.toString() + ("&" + Constants.FILTER + "="
      + URLEncoder.encode("PrefixFilter('notfound')", StandardCharsets.UTF_8.name()));
    response = client.get(negativePath, Constants.MIMETYPE_JSON);
    assertEquals(404, response.getCode());

    // Check with binary parameters
    // positive case
    positivePath = path.toString() + ("&" + Constants.FILTER_B64 + "=" + base64UrlEncoder
      .encodeToString(Bytes.toBytesBinary("ColumnRangeFilter ('\\x00', true, '\\xff', true)")));
    response = client.get(positivePath, Constants.MIMETYPE_JSON);
    checkMultiCellGetJSON(response);

    // negative case
    negativePath = path.toString() + ("&" + Constants.FILTER_B64 + "=" + base64UrlEncoder
      .encodeToString(Bytes.toBytesBinary("ColumnRangeFilter ('\\x00', true, '1', false)")));
    response = client.get(negativePath, Constants.MIMETYPE_JSON);
    assertEquals(404, response.getCode());

    client.delete(row_5_url, extraHdr);
    client.delete(row_6_url, extraHdr);
  }

}
