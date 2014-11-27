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

package com.kylinolap.job.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.util.HadoopUtil;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.CubeSegment;
import com.kylinolap.cube.CubeSegmentStatusEnum;

/**
 * @author yangli9
 */
public class DeployCoprocessorCLI {

    private static final Logger logger = LoggerFactory.getLogger(DeployCoprocessorCLI.class);

    public static final String AGGR_COPROCESSOR_CLS_NAME = "com.kylinolap.storage.hbase.observer.AggregateRegionObserver";

    public static void main(String[] args) throws IOException {
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        Configuration hconf = HadoopUtil.newHBaseConfiguration(kylinConfig.getStorageUrl());
        FileSystem fileSystem = FileSystem.get(hconf);
        HBaseAdmin hbaseAdmin = new HBaseAdmin(hconf);

        String localCoprocessorJar = new File(args[0]).getAbsolutePath();
        logger.info("Identify coprocessor jar " + localCoprocessorJar);

        List<String> tableNames = getHTableNames(kylinConfig);
        logger.info("Identify tables " + tableNames);

        Set<String> oldJarPaths = getCoprocessorJarPaths(hbaseAdmin, tableNames);
        logger.info("Old coprocessor jar: " + oldJarPaths);

        Path hdfsCoprocessorJar = uploadCoprocessorJar(localCoprocessorJar, fileSystem, oldJarPaths);
        logger.info("New coprocessor jar: " + hdfsCoprocessorJar);

        List<String> processedTables = resetCoprocessorOnHTables(hbaseAdmin, hdfsCoprocessorJar, tableNames);

        // Don't remove old jars, missing coprocessor jar will fail hbase
        // removeOldJars(oldJarPaths, fileSystem);

        hbaseAdmin.close();

        logger.info("Processed " + processedTables);
        logger.info("Active coprocessor jar: " + hdfsCoprocessorJar);
    }

    public static void setCoprocessorOnHTable(HTableDescriptor desc, Path hdfsCoprocessorJar) throws IOException {
        logger.info("Set coprocessor on " + desc.getNameAsString());
        desc.addCoprocessor(AGGR_COPROCESSOR_CLS_NAME, hdfsCoprocessorJar, 1001, null);
    }

    public static void resetCoprocessor(String tableName, HBaseAdmin hbaseAdmin, Path hdfsCoprocessorJar) throws IOException {
        logger.info("Disable " + tableName);
        hbaseAdmin.disableTable(tableName);

        logger.info("Unset coprocessor on " + tableName);
        HTableDescriptor desc = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
        while (desc.hasCoprocessor(AGGR_COPROCESSOR_CLS_NAME)) {
            desc.removeCoprocessor(AGGR_COPROCESSOR_CLS_NAME);
        }

        setCoprocessorOnHTable(desc, hdfsCoprocessorJar);
        hbaseAdmin.modifyTable(tableName, desc);

        logger.info("Enable " + tableName);
        hbaseAdmin.enableTable(tableName);
    }

    private static List<String> resetCoprocessorOnHTables(HBaseAdmin hbaseAdmin, Path hdfsCoprocessorJar, List<String> tableNames) throws IOException {
        List<String> processed = new ArrayList<String>();

        for (String tableName : tableNames) {
            try {
                resetCoprocessor(tableName, hbaseAdmin, hdfsCoprocessorJar);
                processed.add(tableName);
            } catch (IOException ex) {
                logger.error("Error processing " + tableName, ex);
            }
        }
        return processed;
    }

    public static Path getNewestCoprocessorJar(KylinConfig config, FileSystem fileSystem) throws IOException {
        Path coprocessorDir = getCoprocessorHDFSDir(fileSystem, config);
        FileStatus newestJar = null;
        for (FileStatus fileStatus : fileSystem.listStatus(coprocessorDir)) {
            if (fileStatus.getPath().toString().endsWith(".jar")) {
                if (newestJar == null) {
                    newestJar = fileStatus;
                } else {
                    if (newestJar.getModificationTime() < fileStatus.getModificationTime())
                        newestJar = fileStatus;
                }
            }
        }
        if (newestJar == null)
            return null;

        Path path = newestJar.getPath().makeQualified(fileSystem.getUri(), null);
        logger.info("The newest coprocessor is " + path.toString());
        return path;
    }

    public static Path uploadCoprocessorJar(String localCoprocessorJar, FileSystem fileSystem, Set<String> oldJarPaths) throws IOException {
        Path uploadPath = null;
        File localCoprocessorFile = new File(localCoprocessorJar);

        // check existing jars
        if (oldJarPaths == null) {
            oldJarPaths = new HashSet<String>();
        }
        Path coprocessorDir = getCoprocessorHDFSDir(fileSystem, KylinConfig.getInstanceFromEnv());
        for (FileStatus fileStatus : fileSystem.listStatus(coprocessorDir)) {
            if (fileStatus.getLen() == localCoprocessorJar.length() && fileStatus.getModificationTime() == localCoprocessorFile.lastModified()) {
                uploadPath = fileStatus.getPath();
                break;
            }
            String filename = fileStatus.getPath().toString();
            if (filename.endsWith(".jar")) {
                oldJarPaths.add(filename);
            }
        }

        // upload if not existing
        if (uploadPath == null) {
            // figure out a unique new jar file name
            Set<String> oldJarNames = new HashSet<String>();
            for (String path : oldJarPaths) {
                oldJarNames.add(new Path(path).getName());
            }
            String baseName = getBaseFileName(localCoprocessorJar);
            String newName = null;
            int i = 0;
            while (newName == null) {
                newName = baseName + "-" + (i++) + ".jar";
                if (oldJarNames.contains(newName))
                    newName = null;
            }

            // upload
            uploadPath = new Path(coprocessorDir, newName);
            FileInputStream in = null;
            FSDataOutputStream out = null;
            try {
                in = new FileInputStream(localCoprocessorFile);
                out = fileSystem.create(uploadPath);
                IOUtils.copy(in, out);
            } finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }

            fileSystem.setTimes(uploadPath, localCoprocessorFile.lastModified(), System.currentTimeMillis());

        }

        uploadPath = uploadPath.makeQualified(fileSystem.getUri(), null);
        return uploadPath;
    }

    private static String getBaseFileName(String localCoprocessorJar) {
        File localJar = new File(localCoprocessorJar);
        String baseName = localJar.getName();
        if (baseName.endsWith(".jar"))
            baseName = baseName.substring(0, baseName.length() - ".jar".length());
        return baseName;
    }

    private static Path getCoprocessorHDFSDir(FileSystem fileSystem, KylinConfig config) throws IOException {
        String hdfsWorkingDirectory = config.getHdfsWorkingDirectory();
        Path coprocessorDir = new Path(hdfsWorkingDirectory, "coprocessor");
        fileSystem.mkdirs(coprocessorDir);
        return coprocessorDir;
    }

    private static Set<String> getCoprocessorJarPaths(HBaseAdmin hbaseAdmin, List<String> tableNames) throws IOException {
        HashSet<String> result = new HashSet<String>();

        for (String tableName : tableNames) {
            HTableDescriptor tableDescriptor = null;
            try {
                tableDescriptor = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
            } catch (TableNotFoundException e) {
                logger.warn("Table not found " + tableName, e);
                continue;
            }

            Matcher keyMatcher;
            Matcher valueMatcher;
            for (Map.Entry<ImmutableBytesWritable, ImmutableBytesWritable> e : tableDescriptor.getValues().entrySet()) {
                keyMatcher = HConstants.CP_HTD_ATTR_KEY_PATTERN.matcher(Bytes.toString(e.getKey().get()));
                if (!keyMatcher.matches()) {
                    continue;
                }
                valueMatcher = HConstants.CP_HTD_ATTR_VALUE_PATTERN.matcher(Bytes.toString(e.getValue().get()));
                if (!valueMatcher.matches()) {
                    continue;
                }

                String jarPath = valueMatcher.group(1).trim();
                String clsName = valueMatcher.group(2).trim();

                if (AGGR_COPROCESSOR_CLS_NAME.equals(clsName)) {
                    result.add(jarPath);
                }
            }
        }

        return result;
    }

    private static List<String> getHTableNames(KylinConfig config) {
        CubeManager cubeMgr = CubeManager.getInstance(config);

        ArrayList<String> result = new ArrayList<String>();
        for (CubeInstance cube : cubeMgr.listAllCubes()) {
            for (CubeSegment seg : cube.getSegments(CubeSegmentStatusEnum.READY)) {
                String tableName = seg.getStorageLocationIdentifier();
                if (StringUtils.isBlank(tableName) == false)
                    result.add(tableName);
            }
        }

        return result;
    }
}
