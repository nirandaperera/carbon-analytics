/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.analytics.datasource.hbase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.wso2.carbon.analytics.datasource.commons.AnalyticsIterator;
import org.wso2.carbon.analytics.datasource.commons.Record;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsTableNotAvailableException;
import org.wso2.carbon.analytics.datasource.core.util.GenericUtils;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseAnalyticsDSConstants;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseRuntimeException;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseUtils;

import java.io.IOException;
import java.util.*;

/**
 * Subclass of java.util.Iterator for streaming in records based on timestamp ranges
 */
public class HBaseTimestampIterator implements AnalyticsIterator<Record> {

    private List<String> columns;

    private int tenantId;
    private int batchSize;
    private int recordsCount;
    private int globalCounter;

    private byte[] latestRow;
    private byte[] endRow;
    private static final long POSTFIX = 1L;

    private boolean fullyFetched;
    private boolean noStartTime = false;
    private boolean noStopTime = false;
    private String tableName;
    private Table table, indexTable;
    private Iterator<Record> subIterator = Collections.emptyIterator();

    private static final Log log = LogFactory.getLog(HBaseTimestampIterator.class);

    HBaseTimestampIterator(int tenantId, String tableName, List<String> columns, long timeFrom, long timeTo, int recordsCount,
                           Connection conn, int batchSize) throws AnalyticsException, AnalyticsTableNotAvailableException {
        if ((timeFrom > timeTo) || (batchSize <= 0)) {
            throw new AnalyticsException("Invalid parameters specified for reading data from table " + tableName +
                    " for tenant " + tenantId);
        } else {
            this.init(conn, tenantId, tableName, columns, recordsCount, batchSize);
            if (timeFrom == Long.MIN_VALUE) {
                this.noStartTime = true;
                /* Setting param to null, to recognize the first ever run. It will never become null after the first run. */
                this.latestRow = null;
            } else {
                /* Setting the initial row to start time -1 because it will soon be incremented by 1L. */
                this.latestRow = HBaseUtils.encodeLong(timeFrom - POSTFIX);
            }
            if (timeFrom == Long.MAX_VALUE) {
                this.noStopTime = true;
            } else {
                this.endRow = HBaseUtils.encodeLong(timeTo);
            }
            /* pre-fetching from HBase and populating records for the first time */
            this.fetchRecords();
        }
    }

    @Override
    public boolean hasNext() {
        boolean hasMore = this.subIterator.hasNext();
        if (!hasMore) {
            try {
                this.fetchRecords();
            } catch (AnalyticsTableNotAvailableException e) {
                this.subIterator = Collections.emptyIterator();
            }
        }
        return this.subIterator.hasNext();
    }

    @Override
    public Record next() {
        if (this.hasNext()) {
            return this.subIterator.next();
        } else {
            throw new NoSuchElementException("No further elements exist in iterator");
        }
    }

    @Override
    public void remove() {
        /* nothing to do here, since this is a read-only iterator */
    }

    private void fetchRecords() throws AnalyticsTableNotAvailableException {
        if (this.fullyFetched) {
            return;
        }
        List<String> currentBatch = this.populateNextRecordBatch();
        if (currentBatch.size() == 0) {
            return;
        }
        Set<String> colSet = null;
        List<Record> fetchedRecords = new ArrayList<>();
        List<Get> gets = new ArrayList<>();

        for (String currentId : currentBatch) {
            Get get = new Get(Bytes.toBytes(currentId));

            /* If the list of columns to be retrieved is null, retrieve ALL columns. */
            if (this.columns != null && this.columns.size() > 0) {
                colSet = new HashSet<>(this.columns);
                get.addColumn(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                        HBaseAnalyticsDSConstants.ANALYTICS_ROWDATA_QUALIFIER_NAME);
            } else {
                get.addFamily(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME);
            }
            gets.add(get);
        }

        try {
            Result[] results = this.table.get(gets);
            for (Result currentResult : results) {
                if (!currentResult.isEmpty()) {
                    Record record = HBaseUtils.constructRecord(currentResult, tenantId, tableName, colSet);
                    if (record != null) {
                        fetchedRecords.add(record);
                    }
                }
            }
            this.subIterator = fetchedRecords.iterator();
        } catch (Exception e) {
            if (e instanceof RetriesExhaustedException) {
                throw new AnalyticsTableNotAvailableException(tenantId, tableName);
            }
            this.cleanup();
            throw new HBaseRuntimeException("Error reading data from table " + this.tableName + " for tenant " +
                    this.tenantId, e);
        }
    }

    private List<String> populateNextRecordBatch() {
        List<String> currentBatch = new ArrayList<>();
        if (this.recordsCount > 0) {
            if (this.globalCounter >= this.recordsCount) {
                this.fullyFetched = true;
                return currentBatch;
            }
        }
        int counter = 0;
        Scan indexScan = new Scan();
        long latestTime;
        if (!this.noStartTime && (this.latestRow != null)) {
            latestTime = HBaseUtils.decodeLong(this.latestRow);
            indexScan.setStartRow(HBaseUtils.encodeLong(latestTime + POSTFIX));
        }
        if (!this.noStopTime) {
            indexScan.setStopRow(this.endRow);
        }
        indexScan.addFamily(HBaseAnalyticsDSConstants.ANALYTICS_INDEX_COLUMN_FAMILY_NAME);
        ResultScanner resultScanner;
        try {
            resultScanner = this.indexTable.getScanner(indexScan);
            for (Result rowResult : resultScanner) {
                if (counter == this.batchSize || this.globalCounter == this.recordsCount) {
                    /* Snap out of further processing, because either the batch end or the client limit has been reached. */
                    break;
                }
                Cell[] cells = rowResult.rawCells();
                for (Cell cell : cells) {
                    currentBatch.add(Bytes.toString(CellUtil.cloneValue(cell)));
                }
                this.latestRow = rowResult.getRow();
                counter++;
                this.globalCounter++;
            }
            resultScanner.close();
            indexTable.close();
        } catch (IOException e) {
            throw new HBaseRuntimeException("Error reading index data for table " + this.tableName + ", tenant " +
                    this.tenantId, e);
        }
        if (counter < this.batchSize) {
            /* Checking if processing had been interrupted PRIOR TO:
            * - More results being scanned (counter equals 0 in this case), where there are no more records to be scanned
            * - Batch size becoming equal to the counter (counter < batchSize case), signifying the scan ran out of records
            *       where the scan has either exhausted all records on the table or has reached the limit from the client.
            *  For both of the above cases, we understand that the end of processing for this particular query is at hand
            *  (i.e. Iterator: I die in peace now, tell my family I love them..) */
            this.fullyFetched = true;
            this.cleanup();
        }
        return currentBatch;
    }

    private void init(Connection conn, int tenantId, String tableName, List<String> columns, int recordsCount,
                      int batchSize) throws AnalyticsException {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.columns = columns;
        this.recordsCount = recordsCount;
        this.batchSize = batchSize;
        this.globalCounter = 0;
        try {
            this.indexTable = conn.getTable(TableName.valueOf(
                    HBaseUtils.generateTableName(tenantId, tableName, HBaseAnalyticsDSConstants.TableType.INDEX)));
            this.table = conn.getTable(TableName.valueOf(
                    HBaseUtils.generateTableName(tenantId, tableName, HBaseAnalyticsDSConstants.TableType.DATA)));
        } catch (IOException e) {
            throw new AnalyticsException("The table " + tableName + " for tenant " + tenantId +
                    " could not be initialized for reading: " + e.getMessage(), e);
        }
    }

    private void cleanup() {
        GenericUtils.closeQuietly(this.indexTable);
        GenericUtils.closeQuietly(this.table);
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }
}
