/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.analytics.datasource.hbase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.analytics.datasource.core.fs.AnalyticsFileSystem;
import org.wso2.carbon.analytics.datasource.core.util.GenericUtils;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseAnalyticsDSConstants;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseUtils;
import org.wso2.carbon.ndatasource.common.DataSourceException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hadoop Distributed File System (HDFS) {@link org.wso2.carbon.analytics.datasource.core.fs.AnalyticsFileSystem} - implementation.
 */
public class HDFSAnalyticsFileSystem implements AnalyticsFileSystem {

    private FileSystem fileSystem;

    private static final Log log = LogFactory.getLog(HDFSAnalyticsFileSystem.class);

    public HDFSAnalyticsFileSystem(FileSystem fs) throws AnalyticsException {
        this.fileSystem = fs;
    }

    public HDFSAnalyticsFileSystem() {
        this.fileSystem = null;
    }

    @Override
    public void init(Map<String, String> properties) throws AnalyticsException {
        String dsName = properties.get(HBaseAnalyticsDSConstants.DATASOURCE_NAME);
        if (dsName == null) {
            throw new AnalyticsException("The property '" + HBaseAnalyticsDSConstants.DATASOURCE_NAME +
                    "' is required");
        }
        try {
            Configuration config = (Configuration) GenericUtils.loadGlobalDataSource(dsName);
            if (config == null) {
                throw new AnalyticsException("Failed to initialize HDFS configuration based on data source" +
                        " definition");
            }
            this.fileSystem = FileSystem.get(config);
        } catch (DataSourceException | IOException e) {
            throw new AnalyticsException("Error establishing connection to HDFS instance from data source definition: " +
                    e.getMessage(), e);
        }
        if (this.fileSystem == null) {
            throw new AnalyticsException("Error establishing connection to HDFS instance : Hadoop FileSystem " +
                    "initialization failed");
        }
    }

    @Override
    public boolean exists(String source) throws IOException {
        return this.fileSystem.exists(HBaseUtils.createPath(source));
    }

    @Override
    public List<String> list(String source) throws IOException {
        Path path = HBaseUtils.createPath(source);
        List<String> filePaths = new ArrayList<>();
        /* maintaining normalized version of path string, because we'll be doing String operations on it later */
        source = GenericUtils.normalizePath(source);
        if (!(this.fileSystem.exists(path))) {
            if (log.isDebugEnabled()) {
                log.debug("Path specified (" + source + ") does not exist in the filesystem");
            }
        } else {
            FileStatus[] files = this.fileSystem.listStatus(path);
            if (null == files) {
                throw new IOException("An error occurred while listing files from the specified path: " + path);
            } else {
                for (FileStatus file : files) {
                    if (null != file) {
                        filePaths.add(file.getPath().toString().substring(file.getPath().toString().lastIndexOf(source) +
                                source.length() + 1));
                    }
                }
            }
        }
        return filePaths;
    }

    @Override
    public void delete(String source) throws IOException {
        Path path = HBaseUtils.createPath(source);
        if (!(this.fileSystem.exists(path))) {
            if (log.isDebugEnabled()) {
                log.debug("Path specified (" + source + ") does not exist in the filesystem");
            }
        } else {
            /* Directory will be deleted regardless it being empty or not*/
            this.fileSystem.delete(path, true);
        }
    }

    @Override
    public void mkdir(String source) throws IOException {
        Path path = HBaseUtils.createPath(source);
        if (this.fileSystem.exists(path)) {
            if (log.isDebugEnabled()) {
                log.debug("Path specified (" + source + ") already exists in the filesystem");
            }
        } else {
            this.fileSystem.mkdirs(path);
        }
    }

    @Override
    public DataInput createInput(String source) throws IOException {
        return new HDFSDataInput(HBaseUtils.createPath(source), this.fileSystem);
    }

    @Override
    public OutputStream createOutput(String source) throws IOException {
        Path path = HBaseUtils.createPath(source);
        if (this.fileSystem.exists(path)) {
            if (log.isDebugEnabled()) {
                log.debug("Specified path (" + source + ") already exists in filesystem and has been overwritten.");
            }
        }
        return new HDFSOutputStream(this.fileSystem, path, true);
    }

    @Override
    public void sync(String source) throws IOException {
        /* Nothing to do here, since the hadoop filesystem itself is responsible for all sync operations.*/
    }

    @Override
    public long length(String source) throws IOException {
        FileStatus status = this.fileSystem.getFileStatus(HBaseUtils.createPath(source));
        return status.getLen();
    }

    @Override
    public void destroy() throws IOException {
        try {
            this.fileSystem.close();
        } catch (IOException ignore) {
            /* ignore, we'll no longer use the fileSystem instance anyway */
        }
    }

    @Override
    public void renameFileInDirectory(String dirPath, String nameFrom, String nameTo) throws IOException {
        if (!dirPath.endsWith("/")) {
            dirPath += "/";
        }
        this.fileSystem.rename(HBaseUtils.createPath(dirPath + nameFrom),
                HBaseUtils.createPath(dirPath + nameTo));
    }

    /**
     * HDFS Implementation of {@link org.wso2.carbon.analytics.datasource.core.fs.AnalyticsFileSystem.DataInput}
     */
    public class HDFSDataInput implements DataInput {

        private FileSystem fileSystem;
        private Path path;

        private FSDataInputStream stream;

        public HDFSDataInput(Path path, FileSystem fs) throws IOException {
            this.fileSystem = fs;
            this.path = path;
            this.stream = fs.open(path);
        }

        @Override
        public int read(byte[] buff, int offset, int len) throws IOException {
            return this.stream.read(buff, offset, len);
        }

        @Override
        public void seek(long pos) throws IOException {
            this.stream.seek(pos);
        }

        @Override
        public long getPosition() {
            try {
                return this.stream.getPos();
            } catch (IOException e) {
                /* Not worrying about IOException at compile time */
                throw new HDFSRuntimeException("Error getting the current file pointer from stream: ", e);
            }
        }

        @Override
        public void close() throws IOException {
            this.stream.close();
        }

        @Override
        public DataInput makeCopy() throws IOException {
            return new HDFSDataInput(this.path, this.fileSystem);
        }
    }

    public static class HDFSOutputStream extends OutputStream {

        private FSDataOutputStream stream;

        public HDFSOutputStream(FileSystem fileSystem, Path path, Boolean overwrite) throws IOException {
            this.stream = fileSystem.create(path, overwrite);
        }

        @Override
        public void write(int b) throws IOException {
            this.stream.write(b);
        }

        @Override
        public void write(byte b[]) throws IOException {
            this.stream.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            this.stream.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            this.stream.hsync();
            this.stream.hflush();
        }

        @Override
        public void close() throws IOException {
            this.flush();
            this.stream.close();
        }

    }

    public static class HDFSRuntimeException extends RuntimeException {

        private static final long serialVersionUID = 9089866389463879488L;

        HDFSRuntimeException(String s, Throwable t) {
            super(s, t);
        }
    }

}
