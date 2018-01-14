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
package org.apache.nifi.processors.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.apache.nifi.csv.CSVUtils;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConvertExcelToCSVProcessorTest {

    private TestRunner testRunner;

    @Before
    public void init() {
        testRunner = TestRunners.newTestRunner(ConvertExcelToCSVProcessor.class);
    }

    @Test
    public void testMultipleSheetsGeneratesMultipleFlowFiles() throws Exception {

        testRunner.enqueue(new File("src/test/resources/TwoSheets.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 2);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ffSheetA = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheetA = new Long(ffSheetA.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheetA == 4l);
        assertTrue(ffSheetA.getAttribute(ConvertExcelToCSVProcessor.SHEET_NAME).equalsIgnoreCase("TestSheetA"));
        assertTrue(ffSheetA.getAttribute(ConvertExcelToCSVProcessor.SOURCE_FILE_NAME).equals("TwoSheets.xlsx"));

        //Since TestRunner.run() will create a random filename even if the attribute is set in enqueue manually we just check that "_{SHEETNAME}.csv is present
        assertTrue(ffSheetA.getAttribute(CoreAttributes.FILENAME.key()).endsWith("_TestSheetA.csv"));

        MockFlowFile ffSheetB = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(1);
        Long rowsSheetB = new Long(ffSheetB.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheetB == 3l);
        assertTrue(ffSheetB.getAttribute(ConvertExcelToCSVProcessor.SHEET_NAME).equalsIgnoreCase("TestSheetB"));
        assertTrue(ffSheetB.getAttribute(ConvertExcelToCSVProcessor.SOURCE_FILE_NAME).equals("TwoSheets.xlsx"));

        //Since TestRunner.run() will create a random filename even if the attribute is set in enqueue manually we just check that "_{SHEETNAME}.csv is present
        assertTrue(ffSheetB.getAttribute(CoreAttributes.FILENAME.key()).endsWith("_TestSheetB.csv"));

    }

    @Test
    public void testDataFormatting() throws Exception {
        testRunner.enqueue(new File("src/test/resources/dataformatting.xlsx").toPath());

        testRunner.setProperty(ConvertExcelToCSVProcessor.FORMAT_VALUES, "false");

        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheet = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheet == 9);

        ff.assertContentEquals("Numbers,Timestamps,Money\n" +
                "1234.4559999999999,42736.5,123.45\n" +
                "1234.4559999999999,42736.5,123.45\n" +
                "1234.4559999999999,42736.5,123.45\n" +
                "1234.4559999999999,42736.5,1023.45\n" +
                "1234.4559999999999,42736.5,1023.45\n" +
                "987654321,42736.5,1023.45\n" +
                "987654321,,\n" +
                "987654321,,\n");
    }

    @Test
    public void testQuoting() throws Exception {
        testRunner.enqueue(new File("src/test/resources/dataformatting.xlsx").toPath());

        testRunner.setProperty(CSVUtils.QUOTE_MODE, CSVUtils.QUOTE_MINIMAL);
        testRunner.setProperty(ConvertExcelToCSVProcessor.FORMAT_VALUES, "true");

        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheet = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheet == 9);

        ff.assertContentEquals("Numbers,Timestamps,Money\n" +
                "1234.456,1/1/17,$   123.45\n" +
                "1234.46,12:00:00 PM,£   123.45\n" +
                "1234.5,\"Sunday, January 01, 2017\",¥   123.45\n" +
                "\"1,234.46\",1/1/17 12:00,\"$   1,023.45\"\n" +
                "\"1,234.4560\",12:00 PM,\"£   1,023.45\"\n" +
                "9.88E+08,2017/01/01/ 12:00,\"¥   1,023.45\"\n" +
                "9.877E+08,,\n" +
                "9.8765E+08,,\n");
    }

    @Test
    public void testSkipRows() throws Exception {
        testRunner.enqueue(new File("src/test/resources/dataformatting.xlsx").toPath());

        testRunner.setProperty(ConvertExcelToCSVProcessor.ROWS_TO_SKIP, "2");
        testRunner.setProperty(ConvertExcelToCSVProcessor.FORMAT_VALUES, "true");

        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheet = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertEquals("Row count does match expected value.", "7", rowsSheet.toString());

        ff.assertContentEquals("1234.46,12:00:00 PM,£   123.45\n" +
                "1234.5,Sunday\\, January 01\\, 2017,¥   123.45\n" +
                "1\\,234.46,1/1/17 12:00,$   1\\,023.45\n" +
                "1\\,234.4560,12:00 PM,£   1\\,023.45\n" +
                "9.88E+08,2017/01/01/ 12:00,¥   1\\,023.45\n" +
                "9.877E+08,,\n" +
                "9.8765E+08,,\n");
    }

    @Test
    public void testSkipColumns() throws Exception {
        testRunner.enqueue(new File("src/test/resources/dataformatting.xlsx").toPath());

        testRunner.setProperty(ConvertExcelToCSVProcessor.COLUMNS_TO_SKIP, "2");
        testRunner.setProperty(ConvertExcelToCSVProcessor.FORMAT_VALUES, "true");

        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheet = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheet == 9);

        ff.assertContentEquals("Numbers,Money\n" +
                "1234.456,$   123.45\n" +
                "1234.46,£   123.45\n" +
                "1234.5,¥   123.45\n" +
                "1\\,234.46,$   1\\,023.45\n" +
                "1\\,234.4560,£   1\\,023.45\n" +
                "9.88E+08,¥   1\\,023.45\n" +
                "9.877E+08,\n" +
                "9.8765E+08,\n");
    }

    @Test
    public void testCustomDelimiters() throws Exception {
        testRunner.enqueue(new File("src/test/resources/dataformatting.xlsx").toPath());

        testRunner.setProperty(CSVUtils.VALUE_SEPARATOR, "|");
        testRunner.setProperty(CSVUtils.RECORD_SEPARATOR, "\\r\\n");
        testRunner.setProperty(ConvertExcelToCSVProcessor.FORMAT_VALUES, "true");

        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long rowsSheet = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(rowsSheet == 9);

        ff.assertContentEquals("Numbers|Timestamps|Money\r\n" +
                "1234.456|1/1/17|$   123.45\r\n" +
                "1234.46|12:00:00 PM|£   123.45\r\n" +
                "1234.5|Sunday, January 01, 2017|¥   123.45\r\n" +
                "1,234.46|1/1/17 12:00|$   1,023.45\r\n" +
                "1,234.4560|12:00 PM|£   1,023.45\r\n" +
                "9.88E+08|2017/01/01/ 12:00|¥   1,023.45\r\n" +
                "9.877E+08||\r\n" +
                "9.8765E+08||\r\n");
    }

    /**
     * Validates that all sheets in the Excel document are exported.
     *
     * @throws Exception
     *  Any exception thrown during execution.
     */
    @Test
    public void testProcessAllSheets() throws Exception {

        testRunner.enqueue(new File("src/test/resources/CollegeScorecard.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long l = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(l == 7805l);

        testRunner.clearProvenanceEvents();
        testRunner.clearTransferState();

        testRunner.enqueue(new File("src/test/resources/TwoSheets.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 2);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        l = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(l == 4l);

        ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(1);
        l = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(l == 3l);
    }

    /**
     * Validates that the manually specified sheet is exported from the Excel document.
     *
     * @throws Exception
     *  Any exception thrown during execution.
     */
    @Test
    public void testProcessASpecificSheetThatDoesExist() throws Exception {

        testRunner.setProperty(ConvertExcelToCSVProcessor.DESIRED_SHEETS, "Scorecard");
        testRunner.enqueue(new File("src/test/resources/CollegeScorecard.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long l = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(l == 7805l);
    }

    /**
     * Tests for a syntactically valid Excel XSSF document with a manually specified Excel sheet that does not exist.
     * In this scenario only the Original relationship should be invoked.
     *
     * @throws Exception
     *  Any exception thrown during execution.
     */
    @Test
    public void testNonExistantSpecifiedSheetName() throws Exception {

        testRunner.setProperty(ConvertExcelToCSVProcessor.DESIRED_SHEETS, "NopeIDoNotExist");
        testRunner.enqueue(new File("src/test/resources/CollegeScorecard.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 0);  //We aren't expecting any output to success here because the sheet doesn't exist
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);
    }

    /**
     * Validates that a sheet contains blank cells can be converted to a CSV without missing columns.
     *
     * @throws Exception
     *  Any exception thrown during execution.
     */
    @Test
    public void testProcessASheetWithBlankCells() throws Exception {

        testRunner.setProperty(ConvertExcelToCSVProcessor.DESIRED_SHEETS, "Sheet1");
        testRunner.enqueue(new File("src/test/resources/with-blank-cells.xlsx").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 1);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 0);

        MockFlowFile ff = testRunner.getFlowFilesForRelationship(ConvertExcelToCSVProcessor.SUCCESS).get(0);
        Long l = new Long(ff.getAttribute(ConvertExcelToCSVProcessor.ROW_NUM));
        assertTrue(l == 8l);

        ff.assertContentEquals(new File("src/test/resources/with-blank-cells.csv"));
    }

    /**
     * Tests for graceful handling and error messaging of unsupported .XLS files.
     */
    @Test
    public void testHandleUnsupportedXlsFile() throws Exception {

        testRunner.enqueue(new File("src/test/resources/Unsupported.xls").toPath());
        testRunner.run();

        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.SUCCESS, 0);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.ORIGINAL, 0);
        testRunner.assertTransferCount(ConvertExcelToCSVProcessor.FAILURE, 1);

        List<LogMessage> errorMessages = testRunner.getLogger().getErrorMessages();
        Assert.assertEquals(1, errorMessages.size());
        String messageText = errorMessages.get(0).getMsg();
        Assert.assertTrue(messageText.contains("Excel") && messageText.contains("OLE2"));
    }
}