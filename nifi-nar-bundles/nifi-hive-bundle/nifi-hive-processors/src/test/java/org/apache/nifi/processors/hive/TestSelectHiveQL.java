/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.hive;

import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.dbcp.hive.HiveDBCPService;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.util.hive.HiveJdbcCommon;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.apache.nifi.processors.hive.SelectHiveQL.HIVEQL_OUTPUT_FORMAT;
import static org.apache.nifi.util.hive.HiveJdbcCommon.AVRO;
import static org.apache.nifi.util.hive.HiveJdbcCommon.CSV;
import static org.apache.nifi.util.hive.HiveJdbcCommon.CSV_MIME_TYPE;
import static org.apache.nifi.util.hive.HiveJdbcCommon.MIME_TYPE_AVRO_BINARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSelectHiveQL {

    private static final Logger LOGGER;
    private final static String MAX_ROWS_KEY = "maxRows";

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.log.nifi.io.nio", "debug");
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.hive.SelectHiveQL", "debug");
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.hive.TestSelectHiveQL", "debug");
        LOGGER = LoggerFactory.getLogger(TestSelectHiveQL.class);
    }

    private final static String DB_LOCATION = "target/db";

    private final static String QUERY_WITH_EL = "select "
            + "  PER.ID as PersonId, PER.NAME as PersonName, PER.CODE as PersonCode"
            + " from persons PER"
            + " where PER.ID > ${person.id}";

    private final static String QUERY_WITHOUT_EL = "select "
            + "  PER.ID as PersonId, PER.NAME as PersonName, PER.CODE as PersonCode"
            + " from persons PER"
            + " where PER.ID > 10";


    @BeforeClass
    public static void setupClass() {
        System.setProperty("derby.stream.error.file", "target/derby.log");
    }

    private TestRunner runner;

    @Before
    public void setup() throws InitializationException {
        final DBCPService dbcp = new DBCPServiceSimpleImpl();
        final Map<String, String> dbcpProperties = new HashMap<>();

        runner = TestRunners.newTestRunner(SelectHiveQL.class);
        runner.addControllerService("dbcp", dbcp, dbcpProperties);
        runner.enableControllerService(dbcp);
        runner.setProperty(SelectHiveQL.HIVE_DBCP_SERVICE, "dbcp");
    }

    @Test
    public void testIncomingConnectionWithNoFlowFile() throws InitializationException {
        runner.setIncomingConnection(true);
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, "SELECT * FROM persons");
        runner.run();
        runner.assertTransferCount(SelectHiveQL.REL_SUCCESS, 0);
        runner.assertTransferCount(SelectHiveQL.REL_FAILURE, 0);
    }

    @Test
    public void testNoIncomingConnection() throws ClassNotFoundException, SQLException, InitializationException, IOException {
        runner.setIncomingConnection(false);
        invokeOnTrigger(QUERY_WITHOUT_EL, false, "Avro");
    }

    @Test
    public void testNoTimeLimit() throws InitializationException, ClassNotFoundException, SQLException, IOException {
        invokeOnTrigger(QUERY_WITH_EL, true, "Avro");
    }


    @Test
    public void testWithNullIntColumn() throws SQLException {
        // remove previous test database, if any
        final File dbLocation = new File(DB_LOCATION);
        dbLocation.delete();

        // load test data to database
        final Connection con = ((HiveDBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();

        try {
            stmt.execute("drop table TEST_NULL_INT");
        } catch (final SQLException sqle) {
            // Nothing to do, probably means the table didn't exist
        }

        stmt.execute("create table TEST_NULL_INT (id integer not null, val1 integer, val2 integer, constraint my_pk primary key (id))");

        stmt.execute("insert into TEST_NULL_INT (id, val1, val2) VALUES (0, NULL, 1)");
        stmt.execute("insert into TEST_NULL_INT (id, val1, val2) VALUES (1, 1, 1)");

        runner.setIncomingConnection(false);
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, "SELECT * FROM TEST_NULL_INT");
        runner.run();

        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, 1);
        runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(0).assertAttributeEquals(SelectHiveQL.RESULT_ROW_COUNT, "2");
    }

    @Test
    public void testWithSqlException() throws SQLException {
        // remove previous test database, if any
        final File dbLocation = new File(DB_LOCATION);
        dbLocation.delete();

        // load test data to database
        final Connection con = ((HiveDBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();

        try {
            stmt.execute("drop table TEST_NO_ROWS");
        } catch (final SQLException sqle) {
            // Nothing to do, probably means the table didn't exist
        }

        stmt.execute("create table TEST_NO_ROWS (id integer)");

        runner.setIncomingConnection(false);
        // Try a valid SQL statement that will generate an error (val1 does not exist, e.g.)
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, "SELECT val1 FROM TEST_NO_ROWS");
        runner.run();

        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_FAILURE, 1);
    }

    @Test
    public void testWithBadSQL() throws SQLException {
        final String BAD_SQL = "create table TEST_NO_ROWS (id integer)";

        // Test with incoming flow file (it should be routed to failure intact, i.e. same content and no parent)
        runner.setIncomingConnection(true);
        // Try a valid SQL statement that will generate an error (val1 does not exist, e.g.)
        runner.enqueue(BAD_SQL);
        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_FAILURE, 1);
        MockFlowFile flowFile = runner.getFlowFilesForRelationship(SelectHiveQL.REL_FAILURE).get(0);
        flowFile.assertContentEquals(BAD_SQL);
        flowFile.assertAttributeEquals("parentIds", null);
        runner.clearTransferState();

        // Test with no incoming flow file (an empty flow file is transferred)
        runner.setIncomingConnection(false);
        // Try a valid SQL statement that will generate an error (val1 does not exist, e.g.)
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, BAD_SQL);
        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_FAILURE, 1);
        flowFile = runner.getFlowFilesForRelationship(SelectHiveQL.REL_FAILURE).get(0);
        flowFile.assertContentEquals("");
    }

    @Test
    public void invokeOnTriggerWithCsv()
            throws InitializationException, ClassNotFoundException, SQLException, IOException {
        invokeOnTrigger(QUERY_WITHOUT_EL, false, CSV);
    }

    @Test
    public void invokeOnTriggerWithAvro()
            throws InitializationException, ClassNotFoundException, SQLException, IOException {
        invokeOnTrigger(QUERY_WITHOUT_EL, false, AVRO);
    }

    public void invokeOnTrigger(final String query, final boolean incomingFlowFile, String outputFormat)
            throws InitializationException, ClassNotFoundException, SQLException, IOException {

        // remove previous test database, if any
        final File dbLocation = new File(DB_LOCATION);
        dbLocation.delete();

        // load test data to database
        final Connection con = ((HiveDBCPService) runner.getControllerService("dbcp")).getConnection();
        final Statement stmt = con.createStatement();
        try {
            stmt.execute("drop table persons");
        } catch (final SQLException sqle) {
            // Nothing to do here, the table didn't exist
        }

        stmt.execute("create table persons (id integer, name varchar(100), code integer)");
        Random rng = new Random(53496);
        final int nrOfRows = 100;
        stmt.executeUpdate("insert into persons values (1, 'Joe Smith', " + rng.nextInt(469947) + ")");
        for (int i = 2; i < nrOfRows; i++) {
            stmt.executeUpdate("insert into persons values (" + i + ", 'Someone Else', " + rng.nextInt(469947) + ")");
        }
        stmt.executeUpdate("insert into persons values (" + nrOfRows + ", 'Last Person', NULL)");

        LOGGER.info("test data loaded");

        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, query);
        runner.setProperty(HIVEQL_OUTPUT_FORMAT, outputFormat);

        if (incomingFlowFile) {
            // incoming FlowFile content is not used, but attributes are used
            final Map<String, String> attributes = new HashMap<>();
            attributes.put("person.id", "10");
            runner.enqueue("Hello".getBytes(), attributes);
        }

        runner.setIncomingConnection(incomingFlowFile);
        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, 1);

        final List<MockFlowFile> flowfiles = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS);
        MockFlowFile flowFile = flowfiles.get(0);
        final InputStream in = new ByteArrayInputStream(flowFile.toByteArray());
        long recordsFromStream = 0;
        if (AVRO.equals(outputFormat)) {
            assertEquals(MIME_TYPE_AVRO_BINARY, flowFile.getAttribute(CoreAttributes.MIME_TYPE.key()));
            final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
            try (DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(in, datumReader)) {
                GenericRecord record = null;
                while (dataFileReader.hasNext()) {
                    // Reuse record object by passing it to next(). This saves us from
                    // allocating and garbage collecting many objects for files with
                    // many items.
                    record = dataFileReader.next(record);
                    recordsFromStream++;
                }
            }
        } else {
            assertEquals(CSV_MIME_TYPE, flowFile.getAttribute(CoreAttributes.MIME_TYPE.key()));
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String headerRow = br.readLine();
            // Derby capitalizes column names
            assertEquals("PERSONID,PERSONNAME,PERSONCODE", headerRow);

            // Validate rows
            String line;
            while ((line = br.readLine()) != null) {
                recordsFromStream++;
                String[] values = line.split(",");
                if (recordsFromStream < (nrOfRows - 10)) {
                    assertEquals(3, values.length);
                    assertTrue(values[1].startsWith("\""));
                    assertTrue(values[1].endsWith("\""));
                } else {
                    assertEquals(2, values.length); // Middle value is null
                }
            }
        }
        assertEquals(nrOfRows - 10, recordsFromStream);
        assertEquals(recordsFromStream, Integer.parseInt(flowFile.getAttribute(SelectHiveQL.RESULT_ROW_COUNT)));
        flowFile.assertAttributeEquals(AbstractHiveQLProcessor.ATTR_INPUT_TABLES, "persons");
    }

    @Test
    public void testMaxRowsPerFlowFileAvro() throws ClassNotFoundException, SQLException, InitializationException, IOException {

        // load test data to database
        final Connection con = ((DBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();
        InputStream in;
        MockFlowFile mff;

        try {
            stmt.execute("drop table TEST_QUERY_DB_TABLE");
        } catch (final SQLException sqle) {
            // Ignore this error, probably a "table does not exist" since Derby doesn't yet support DROP IF EXISTS [DERBY-4842]
        }

        stmt.execute("create table TEST_QUERY_DB_TABLE (id integer not null, name varchar(100), scale float, created_on timestamp, bignum bigint default 0)");
        int rowCount = 0;
        //create larger row set
        for (int batch = 0; batch < 100; batch++) {
            stmt.execute("insert into TEST_QUERY_DB_TABLE (id, name, scale, created_on) VALUES (" + rowCount + ", 'Joe Smith', 1.0, '1962-09-23 03:23:34.234')");
            rowCount++;
        }

        runner.setIncomingConnection(false);
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, "SELECT * FROM TEST_QUERY_DB_TABLE");
        runner.setProperty(SelectHiveQL.MAX_ROWS_PER_FLOW_FILE, "${" + MAX_ROWS_KEY + "}");
        runner.setProperty(SelectHiveQL.HIVEQL_OUTPUT_FORMAT, HiveJdbcCommon.AVRO);
        runner.setVariable(MAX_ROWS_KEY, "9");

        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, 12);

        //ensure all but the last file have 9 records each
        for (int ff = 0; ff < 11; ff++) {
            mff = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(ff);
            in = new ByteArrayInputStream(mff.toByteArray());
            assertEquals(9, getNumberOfRecordsFromStream(in));

            mff.assertAttributeExists("fragment.identifier");
            assertEquals(Integer.toString(ff), mff.getAttribute("fragment.index"));
            assertEquals("12", mff.getAttribute("fragment.count"));
        }

        //last file should have 1 record
        mff = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(11);
        in = new ByteArrayInputStream(mff.toByteArray());
        assertEquals(1, getNumberOfRecordsFromStream(in));
        mff.assertAttributeExists("fragment.identifier");
        assertEquals(Integer.toString(11), mff.getAttribute("fragment.index"));
        assertEquals("12", mff.getAttribute("fragment.count"));
        runner.clearTransferState();
    }

    @Test
    public void testParametrizedQuery() throws ClassNotFoundException, SQLException, InitializationException, IOException {
        // load test data to database
        final Connection con = ((DBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();

        try {
            stmt.execute("drop table TEST_QUERY_DB_TABLE");
        } catch (final SQLException sqle) {
            // Ignore this error, probably a "table does not exist" since Derby doesn't yet support DROP IF EXISTS [DERBY-4842]
        }

        stmt.execute("create table TEST_QUERY_DB_TABLE (id integer not null, name varchar(100), scale float, created_on timestamp, bignum bigint default 0)");
        int rowCount = 0;
        //create larger row set
        for (int batch = 0; batch < 100; batch++) {
            stmt.execute("insert into TEST_QUERY_DB_TABLE (id, name, scale, created_on) VALUES (" + rowCount + ", 'Joe Smith', 1.0, '1962-09-23 03:23:34.234')");
            rowCount++;
        }

        runner.setIncomingConnection(true);
        runner.setProperty(SelectHiveQL.MAX_ROWS_PER_FLOW_FILE, "${" + MAX_ROWS_KEY + "}");
        runner.setProperty(SelectHiveQL.HIVEQL_OUTPUT_FORMAT, HiveJdbcCommon.AVRO);
        runner.setVariable(MAX_ROWS_KEY, "9");

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("hiveql.args.1.value", "1");
        attributes.put("hiveql.args.1.type", String.valueOf(Types.INTEGER));
        runner.enqueue("SELECT * FROM TEST_QUERY_DB_TABLE WHERE id = ?", attributes );

        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, 1);
        runner.clearTransferState();
    }

    @Test
    public void testMaxRowsPerFlowFileCSV() throws ClassNotFoundException, SQLException, InitializationException, IOException {

        // load test data to database
        final Connection con = ((DBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();
        InputStream in;
        MockFlowFile mff;

        try {
            stmt.execute("drop table TEST_QUERY_DB_TABLE");
        } catch (final SQLException sqle) {
            // Ignore this error, probably a "table does not exist" since Derby doesn't yet support DROP IF EXISTS [DERBY-4842]
        }

        stmt.execute("create table TEST_QUERY_DB_TABLE (id integer not null, name varchar(100), scale float, created_on timestamp, bignum bigint default 0)");
        int rowCount = 0;
        //create larger row set
        for (int batch = 0; batch < 100; batch++) {
            stmt.execute("insert into TEST_QUERY_DB_TABLE (id, name, scale, created_on) VALUES (" + rowCount + ", 'Joe Smith', 1.0, '1962-09-23 03:23:34.234')");
            rowCount++;
        }

        runner.setIncomingConnection(true);
        runner.setProperty(SelectHiveQL.MAX_ROWS_PER_FLOW_FILE, "${" + MAX_ROWS_KEY + "}");
        runner.setProperty(SelectHiveQL.HIVEQL_OUTPUT_FORMAT, HiveJdbcCommon.CSV);

        runner.enqueue("SELECT * FROM TEST_QUERY_DB_TABLE", new HashMap<String, String>() {{
            put(MAX_ROWS_KEY, "9");
        }});

        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, 12);

        //ensure all but the last file have 9 records (10 lines = 9 records + header) each
        for (int ff = 0; ff < 11; ff++) {
            mff = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(ff);
            in = new ByteArrayInputStream(mff.toByteArray());
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            assertEquals(10, br.lines().count());

            mff.assertAttributeExists("fragment.identifier");
            assertEquals(Integer.toString(ff), mff.getAttribute("fragment.index"));
            assertEquals("12", mff.getAttribute("fragment.count"));
        }

        //last file should have 1 record (2 lines = 1 record + header)
        mff = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(11);
        in = new ByteArrayInputStream(mff.toByteArray());
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        assertEquals(2, br.lines().count());
        mff.assertAttributeExists("fragment.identifier");
        assertEquals(Integer.toString(11), mff.getAttribute("fragment.index"));
        assertEquals("12", mff.getAttribute("fragment.count"));
        runner.clearTransferState();
    }

    @Test
    public void testMaxRowsPerFlowFileWithMaxFragments() throws ClassNotFoundException, SQLException, InitializationException, IOException {

        // load test data to database
        final Connection con = ((DBCPService) runner.getControllerService("dbcp")).getConnection();
        Statement stmt = con.createStatement();
        InputStream in;
        MockFlowFile mff;

        try {
            stmt.execute("drop table TEST_QUERY_DB_TABLE");
        } catch (final SQLException sqle) {
            // Ignore this error, probably a "table does not exist" since Derby doesn't yet support DROP IF EXISTS [DERBY-4842]
        }

        stmt.execute("create table TEST_QUERY_DB_TABLE (id integer not null, name varchar(100), scale float, created_on timestamp, bignum bigint default 0)");
        int rowCount = 0;
        //create larger row set
        for (int batch = 0; batch < 100; batch++) {
            stmt.execute("insert into TEST_QUERY_DB_TABLE (id, name, scale, created_on) VALUES (" + rowCount + ", 'Joe Smith', 1.0, '1962-09-23 03:23:34.234')");
            rowCount++;
        }

        runner.setIncomingConnection(false);
        runner.setProperty(SelectHiveQL.HIVEQL_SELECT_QUERY, "SELECT * FROM TEST_QUERY_DB_TABLE");
        runner.setProperty(SelectHiveQL.MAX_ROWS_PER_FLOW_FILE, "9");
        Integer maxFragments = 3;
        runner.setProperty(SelectHiveQL.MAX_FRAGMENTS, maxFragments.toString());

        runner.run();
        runner.assertAllFlowFilesTransferred(SelectHiveQL.REL_SUCCESS, maxFragments);

        for (int i = 0; i < maxFragments; i++) {
            mff = runner.getFlowFilesForRelationship(SelectHiveQL.REL_SUCCESS).get(i);
            in = new ByteArrayInputStream(mff.toByteArray());
            assertEquals(9, getNumberOfRecordsFromStream(in));

            mff.assertAttributeExists("fragment.identifier");
            assertEquals(Integer.toString(i), mff.getAttribute("fragment.index"));
            assertEquals(maxFragments.toString(), mff.getAttribute("fragment.count"));
        }

        runner.clearTransferState();
    }

    private long getNumberOfRecordsFromStream(InputStream in) throws IOException {
        final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        try (DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(in, datumReader)) {
            GenericRecord record = null;
            long recordsFromStream = 0;
            while (dataFileReader.hasNext()) {
                // Reuse record object by passing it to next(). This saves us from
                // allocating and garbage collecting many objects for files with
                // many items.
                record = dataFileReader.next(record);
                recordsFromStream += 1;
            }

            return recordsFromStream;
        }
    }

    /**
     * Simple implementation only for SelectHiveQL processor testing.
     */
    private class DBCPServiceSimpleImpl extends AbstractControllerService implements HiveDBCPService {

        @Override
        public String getIdentifier() {
            return "dbcp";
        }

        @Override
        public Connection getConnection() throws ProcessException {
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                return DriverManager.getConnection("jdbc:derby:" + DB_LOCATION + ";create=true");
            } catch (final Exception e) {
                throw new ProcessException("getConnection failed: " + e);
            }
        }

        @Override
        public String getConnectionURL() {
            return "jdbc:derby:" + DB_LOCATION + ";create=true";
        }
    }

}
