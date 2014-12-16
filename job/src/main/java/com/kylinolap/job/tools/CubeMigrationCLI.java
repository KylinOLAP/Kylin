package com.kylinolap.job.tools;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.persistence.JsonSerializer;
import com.kylinolap.common.persistence.ResourceStore;
import com.kylinolap.common.persistence.Serializer;
import com.kylinolap.cube.*;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.dict.DictionaryInfo;
import com.kylinolap.dict.DictionaryManager;
import com.kylinolap.dict.lookup.SnapshotManager;
import com.kylinolap.dict.lookup.SnapshotTable;
import com.kylinolap.job.JobInstance;
import com.kylinolap.metadata.model.TableDesc;
import com.kylinolap.metadata.project.ProjectInstance;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by honma on 9/3/14.
 * <p/>
 * This tool serves for the purpose of migrating cubes. e.g. upgrade cube from
 * dev env to test(prod) env, or vice versa.
 * <p/>
 * Note that different envs are assumed to share the same hadoop cluster,
 * including hdfs, hbase and hive.
 */
public class CubeMigrationCLI {

    private static final Logger logger = LoggerFactory.getLogger(CubeMigrationCLI.class);

    private static List<Opt> operations;
    private static KylinConfig srcConfig;
    private static KylinConfig dstConfig;
    private static ResourceStore srcStore;
    private static ResourceStore dstStore;
    private static FileSystem hdfsFS;
    private static HBaseAdmin hbaseAdmin;

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 6) {
            usage();
            System.exit(1);
        }

        moveCube(args[0], args[1], args[2], args[3], args[4], args[5]);
    }

    private static void usage() {
        System.out.println("Usage: CubeMigrationCLI srcKylinConfigUri dstKylinConfigUri cubeName projectName overwriteIfExists realExecute");
        System.out.println(
                " srcKylinConfigUri: The KylinConfig of the cube’s source \n" +
                        "dstKylinConfigUri: The KylinConfig of the cube’s new home \n" +
                        "cubeName: the name of cube to be migrated. \n" +
                        "projectName: The target project in the target environment.(Make sure it exist) \n" +
                        "overwriteIfExists: overwrite cube if it already exists in the target environment. \n" +
                        "realExecute: if false, just print the operations to take, if true, do the real migration. \n");

    }

    public static void moveCube(KylinConfig srcCfg, KylinConfig dstCfg, String cubeName, String projectName, String overwriteIfExists, String realExecute) throws IOException, InterruptedException {

        srcConfig = srcCfg;
        srcStore = ResourceStore.getStore(srcConfig);
        dstConfig = dstCfg;
        dstStore = ResourceStore.getStore(dstConfig);

        CubeManager cubeManager = CubeManager.getInstance(srcConfig);
        CubeInstance cube = cubeManager.getCube(cubeName);
        logger.info("cube to be moved is : " + cubeName);

        if (cube.getStatus() != CubeStatusEnum.READY)
            throw new IllegalStateException("Cannot migrate cube that is not in READY state.");

        for (CubeSegment segment : cube.getSegments()) {
            if (segment.getStatus() != CubeSegmentStatusEnum.READY) {
                throw new IllegalStateException("At least one segment is not in READY state");
            }
        }

        checkAndGetHbaseUrl();

        Configuration conf = HBaseConfiguration.create();
        hbaseAdmin = new HBaseAdmin(conf);

        hdfsFS = FileSystem.get(new Configuration());

        operations = new ArrayList<Opt>();

        copyFilesInMetaStore(cube, overwriteIfExists);
        renameFoldersInHdfs(cube);
        changeHtableHost(cube);
        addCubeIntoProject(cubeName, projectName);

        if (realExecute.equalsIgnoreCase("true")) {
            doOpts();
        } else {
            showOpts();
        }
    }

    public static void moveCube(String srcCfgUri, String dstCfgUri, String cubeName, String projectName, String overwriteIfExists, String realExecute) throws IOException, InterruptedException {

        moveCube(KylinConfig.createInstanceFromUri(srcCfgUri), KylinConfig.createInstanceFromUri(dstCfgUri), cubeName, projectName, overwriteIfExists, realExecute);
    }

    private static String checkAndGetHbaseUrl() {
        String srcMetadataUrl = srcConfig.getMetadataUrl();
        String dstMetadataUrl = dstConfig.getMetadataUrl();

        logger.info("src metadata url is " + srcMetadataUrl);
        logger.info("dst metadata url is " + dstMetadataUrl);

        int srcIndex = srcMetadataUrl.toLowerCase().indexOf("hbase:");
        int dstIndex = dstMetadataUrl.toLowerCase().indexOf("hbase:");
        if (srcIndex < 0 || dstIndex < 0)
            throw new IllegalStateException("Both metadata urls should be hbase metadata url");

        String srcHbaseUrl = srcMetadataUrl.substring(srcIndex).trim();
        String dstHbaseUrl = dstMetadataUrl.substring(dstIndex).trim();
        if (!srcHbaseUrl.equalsIgnoreCase(dstHbaseUrl)) {
            throw new IllegalStateException("hbase url not equal! ");
        }

        logger.info("hbase url is " + srcHbaseUrl.trim());
        return srcHbaseUrl.trim();
    }

    private static void renameFoldersInHdfs(CubeInstance cube) {
        for (CubeSegment segment : cube.getSegments()) {

            String jobUuid = segment.getLastBuildJobID();
            String src = JobInstance.getJobWorkingDir(jobUuid, srcConfig.getHdfsWorkingDirectory());
            String tgt = JobInstance.getJobWorkingDir(jobUuid, dstConfig.getHdfsWorkingDirectory());

            operations.add(new Opt(OptType.RENAME_FOLDER_IN_HDFS, new Object[] { src, tgt }));
        }

    }

    private static void changeHtableHost(CubeInstance cube) {
        for (CubeSegment segment : cube.getSegments()) {
            operations.add(new Opt(OptType.CHANGE_HTABLE_HOST,
                    new Object[] { segment.getStorageLocationIdentifier() }));
        }
    }

    private static void copyFilesInMetaStore(CubeInstance cube, String overwriteIfExists) throws IOException {

        List<String> metaItems = new ArrayList<String>();
        List<String> dictAndSnapshot = new ArrayList<String>();
        listCubeRelatedResources(cube, metaItems, dictAndSnapshot);

        if (dstStore.exists(cube.getResourcePath()) && !overwriteIfExists.equalsIgnoreCase("true"))
            throw new IllegalStateException("The cube named " + cube.getName() + " already exists on target metadata store. Use overwriteIfExists to overwrite it");

        for (String item : metaItems) {
            operations.add(new Opt(OptType.COPY_FILE_IN_META, new Object[] { item }));
        }

        for (String item : dictAndSnapshot) {
            operations.add(new Opt(OptType.COPY_DICT_OR_SNAPSHOT, new Object[] { item, cube.getName() }));
        }
    }

    private static void addCubeIntoProject(String cubeName, String projectName) throws IOException {
        String projectResPath = ProjectInstance.concatResourcePath(projectName);
        if (!dstStore.exists(projectResPath))
            throw new IllegalStateException("The target project " + projectName + "does not exist");

        operations.add(new Opt(OptType.ADD_INTO_PROJECT, new Object[] { cubeName, projectName }));
    }

    private static void listCubeRelatedResources(CubeInstance cube, List<String> metaResource, List<String> dictAndSnapshot) throws IOException {

        CubeDesc cubeDesc = cube.getDescriptor();
        metaResource.add(cube.getResourcePath());
        metaResource.add(cubeDesc.getResourcePath());

        for (TableDesc tableDesc : cubeDesc.listTables()) {
            metaResource.add(tableDesc.getResourcePath());
        }

        for (CubeSegment segment : cube.getSegments()) {
            dictAndSnapshot.addAll(segment.getSnapshotPaths());
            dictAndSnapshot.addAll(segment.getDictionaryPaths());
        }
    }

    private static enum OptType {
        COPY_FILE_IN_META, COPY_DICT_OR_SNAPSHOT, RENAME_FOLDER_IN_HDFS, ADD_INTO_PROJECT, CHANGE_HTABLE_HOST
    }

    private static class Opt {
        private OptType type;
        private Object[] params;

        private Opt(OptType type, Object[] params) {
            this.type = type;
            this.params = params;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(type).append(":");
            for (Object s : params)
                sb.append(s).append(", ");
            return sb.toString();
        }

    }

    private static void showOpts() {
        for (int i = 0; i < operations.size(); ++i) {
            showOpt(operations.get(i));
        }
    }

    private static void showOpt(Opt opt) {
        logger.info("Operation: " + opt.toString());
    }

    private static void doOpts() throws IOException, InterruptedException {
        int index = 0;
        try {
            for (; index < operations.size(); ++index) {
                logger.info("Operation index :" + index);
                doOpt(operations.get(index));
            }
        } catch (Exception e) {
            logger.error("error met", e);
            logger.info("Try undoing previous changes");
            // undo:
            for (int i = index; i >= 0; --i) {
                try {
                    undo(operations.get(i));
                } catch (Exception ee) {
                    logger.error("error met ", e);
                    logger.info("Continue undoing...");
                }
            }

            throw new RuntimeException("Cube moving failed");
        }
    }

    private static void doOpt(Opt opt) throws IOException, InterruptedException {
        logger.info("Executing operation: " + opt.toString());

        switch (opt.type) {
        case CHANGE_HTABLE_HOST: {
            String tableName = (String) opt.params[0];
            HTableDescriptor desc = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
            hbaseAdmin.disableTable(tableName);
            desc.setValue(CubeManager.getHtableMetadataKey(), dstConfig.getMetadataUrlPrefix());
            hbaseAdmin.modifyTable(tableName, desc);
            hbaseAdmin.enableTable(tableName);
            logger.info("CHANGE_HTABLE_HOST is completed");
            break;
        }
        case COPY_FILE_IN_META: {
            String item = (String) opt.params[0];
            InputStream inputStream = srcStore.getResource(item);
            long ts = srcStore.getResourceTimestamp(item);
            dstStore.putResource(item, inputStream, ts);
            inputStream.close();
            logger.info("Item " + item + " is copied");
            break;
        }
        case COPY_DICT_OR_SNAPSHOT: {
            String item = (String) opt.params[0];

            if (item.toLowerCase().endsWith(".dict")) {
                DictionaryManager dstDictMgr = DictionaryManager.getInstance(dstConfig);
                DictionaryManager srcDicMgr = DictionaryManager.getInstance(srcConfig);
                DictionaryInfo dictSrc = srcDicMgr.getDictionaryInfo(item);

                long ts = dictSrc.getLastModified();
                dictSrc.setLastModified(0);//to avoid resource store write conflict
                DictionaryInfo dictSaved = dstDictMgr.trySaveNewDict(dictSrc.getDictionaryObject(), dictSrc);
                dictSrc.setLastModified(ts);

                if (dictSaved == dictSrc) {
                    //no dup found, already saved to dest
                    logger.info("Item " + item + " is copied");
                } else {
                    //dictSrc is rejected because of duplication
                    //modify cube's dictionary path
                    String cubeName = (String) opt.params[1];
                    String cubeResPath = CubeInstance.concatResourcePath(cubeName);
                    Serializer<CubeInstance> cubeSerializer = new JsonSerializer<CubeInstance>(CubeInstance.class);
                    CubeInstance cube = dstStore.getResource(cubeResPath, CubeInstance.class, cubeSerializer);
                    for (CubeSegment segment : cube.getSegments()) {
                        for (Map.Entry<String, String> entry : segment.getDictionaries().entrySet()) {
                            if (entry.getValue().equalsIgnoreCase(item)) {
                                entry.setValue(dictSaved.getResourcePath());
                            }
                        }
                    }
                    dstStore.putResource(cubeResPath, cube, cubeSerializer);
                    logger.info("Item " + item + " is dup, instead " + dictSaved.getResourcePath() + " is reused");
                }

            } else if (item.toLowerCase().endsWith(".snapshot")) {
                SnapshotManager dstSnapMgr = SnapshotManager.getInstance(dstConfig);
                SnapshotManager srcSnapMgr = SnapshotManager.getInstance(srcConfig);
                SnapshotTable snapSrc = srcSnapMgr.getSnapshotTable(item);

                long ts = snapSrc.getLastModified();
                snapSrc.setLastModified(0);
                SnapshotTable snapSaved = dstSnapMgr.trySaveNewSnapshot(snapSrc);
                snapSrc.setLastModified(ts);


                if (snapSaved == snapSrc) {
                    //no dup found, already saved to dest
                    logger.info("Item " + item + " is copied");

                } else {
                    String cubeName = (String) opt.params[1];
                    String cubeResPath = CubeInstance.concatResourcePath(cubeName);
                    Serializer<CubeInstance> cubeSerializer = new JsonSerializer<CubeInstance>(CubeInstance.class);
                    CubeInstance cube = dstStore.getResource(cubeResPath, CubeInstance.class, cubeSerializer);
                    for (CubeSegment segment : cube.getSegments()) {
                        for (Map.Entry<String, String> entry : segment.getSnapshots().entrySet()) {
                            if (entry.getValue().equalsIgnoreCase(item)) {
                                entry.setValue(snapSaved.getResourcePath());
                            }
                        }
                    }
                    dstStore.putResource(cubeResPath, cube, cubeSerializer);
                    logger.info("Item " + item + " is dup, instead " + snapSaved.getResourcePath() + " is reused");

                }

            } else {
                logger.error("unknown item found: " + item);
                logger.info("ignore it");
            }

            break;
        }
        case RENAME_FOLDER_IN_HDFS: {
            String srcPath = (String) opt.params[0];
            String dstPath = (String) opt.params[1];
            hdfsFS.rename(new Path(srcPath), new Path(dstPath));
            logger.info("HDFS Folder renamed from " + srcPath + " to " + dstPath);
            break;
        }
        case ADD_INTO_PROJECT: {
            String cubeName = (String) opt.params[0];
            String projectName = (String) opt.params[1];
            String projectResPath = ProjectInstance.concatResourcePath(projectName);
            Serializer<ProjectInstance> projectSerializer = new JsonSerializer<ProjectInstance>(ProjectInstance.class);
            ProjectInstance project = dstStore.getResource(projectResPath, ProjectInstance.class, projectSerializer);
            project.removeCube(cubeName);
            project.addCube(cubeName);
            dstStore.putResource(projectResPath, project, projectSerializer);
            logger.info("Project instance for " + projectName + " is corrected");
            break;
        }
        }
    }

    private static void undo(Opt opt) throws IOException, InterruptedException {
        logger.info("Undo operation: " + opt.toString());

        switch (opt.type) {
        case CHANGE_HTABLE_HOST: {
            String tableName = (String) opt.params[0];
            HTableDescriptor desc = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
            hbaseAdmin.disableTable(tableName);
            desc.setValue(CubeManager.getHtableMetadataKey(), srcConfig.getMetadataUrlPrefix());
            hbaseAdmin.modifyTable(tableName, desc);
            hbaseAdmin.enableTable(tableName);
            break;
        }
        case COPY_FILE_IN_META: {
            // no harm
            logger.info("Undo for COPY_FILE_IN_META is ignored");
            break;
        }
        case COPY_DICT_OR_SNAPSHOT: {
            // no harm
            logger.info("Undo for COPY_DICT_OR_SNAPSHOT is ignored");
            break;
        }
        case RENAME_FOLDER_IN_HDFS: {
            String srcPath = (String) opt.params[1];
            String dstPath = (String) opt.params[0];

            if (hdfsFS.exists(new Path(srcPath)) && !hdfsFS.exists(new Path(dstPath))) {
                hdfsFS.rename(new Path(srcPath), new Path(dstPath));
                logger.info("HDFS Folder renamed from " + srcPath + " to " + dstPath);
            }
            break;
        }
        case ADD_INTO_PROJECT: {
            logger.info("Undo for ADD_INTO_PROJECT is ignored");
            break;
        }
        }
    }
}
