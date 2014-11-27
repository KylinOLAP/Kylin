/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kylinolap.query.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.metadata.MetadataManager;
import com.kylinolap.metadata.model.schema.ColumnDesc;
import com.kylinolap.metadata.model.schema.TableDesc;

public class H2Database {
    private static final Logger logger = LoggerFactory.getLogger(H2Database.class);

    private static final String[] ALL_TABLES = new String[] { "test_cal_dt", "test_category_groupings", "test_kylin_fact", "test_seller_type_dim", "test_sites" };
    private static final Map<String, String> javaToH2DataTypeMapping = new HashMap<String, String>();

    static {
        javaToH2DataTypeMapping.put("short", "smallint");
        javaToH2DataTypeMapping.put("long", "bigint");
        javaToH2DataTypeMapping.put("byte", "tinyint");
        javaToH2DataTypeMapping.put("string", "varchar");
    }

    private final Connection h2Connection;

    private final KylinConfig config;

    public H2Database(Connection h2Connection, KylinConfig config) {
        this.h2Connection = h2Connection;
        this.config = config;
    }

    public void loadAllTables(String joinType) throws SQLException {
        for (String tableName : ALL_TABLES) {
            loadH2Table(tableName, joinType);
        }
    }

    private void loadH2Table(String tableName, String joinType) throws SQLException {
        MetadataManager metaMgr = MetadataManager.getInstance(config);
        TableDesc tableDesc = metaMgr.getTableDesc(tableName.toUpperCase());
        File tempFile = null;

        String fileNameSuffix = joinType.equalsIgnoreCase("default") ? "" : "." + joinType;

        try {
            tempFile = File.createTempFile("tmp_h2", ".csv");
            FileOutputStream tempFileStream = new FileOutputStream(tempFile);
            String normalPath = "/data/" + tableDesc.getName() + ".csv";

            // If it's the fact table, there will be a facttable.csv.inner or
            // facttable.csv.left in hbase
            // otherwise just use lookup.csv
            InputStream csvStream = metaMgr.getStore().getResource(normalPath + fileNameSuffix);
            if (csvStream == null) {
                csvStream = metaMgr.getStore().getResource(normalPath);
            } else {
                logger.info("H2 decides to load " + (normalPath + fileNameSuffix) + " for table " + tableDesc.getName());
            }

            org.apache.commons.io.IOUtils.copy(csvStream, tempFileStream);

            csvStream.close();
            tempFileStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        String cvsFilePath = tempFile.getPath();
        Statement stmt = h2Connection.createStatement();
        String sql = generateCreateH2TableSql(tableDesc, cvsFilePath);
        stmt.executeUpdate(sql);

        if (tempFile != null)
            tempFile.delete();
    }

    private String generateCreateH2TableSql(TableDesc tableDesc, String csvFilePath) {
        StringBuilder ddl = new StringBuilder();
        StringBuilder csvColumns = new StringBuilder();

        ddl.append("CREATE TABLE " + tableDesc.getName() + "\n");
        ddl.append("(" + "\n");

        for (int i = 0; i < tableDesc.getColumns().length; i++) {
            ColumnDesc col = tableDesc.getColumns()[i];
            if (i > 0) {
                ddl.append(",");
                csvColumns.append(",");
            }
            ddl.append(col.getName() + " " + getH2DataType((col.getDatatype())) + "\n");
            csvColumns.append(col.getName());
        }
        ddl.append(")" + "\n");
        ddl.append("AS SELECT * FROM CSVREAD('" + csvFilePath + "', '" + csvColumns + "', 'charset=UTF-8 fieldSeparator=,');");

        return ddl.toString();
    }

    private static String getH2DataType(String javaDataType) {
        String hiveDataType = javaToH2DataTypeMapping.get(javaDataType.toLowerCase());
        if (hiveDataType == null) {
            hiveDataType = javaDataType;
        }
        return hiveDataType.toLowerCase();
    }

}
