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

package com.kylinolap.job.hadoop.cube;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import com.kylinolap.cube.project.CubeRealizationManager;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.util.LocalFileMetadataTestCase;
import com.kylinolap.cube.CubeBuildTypeEnum;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.CubeSegment;
import com.kylinolap.cube.exception.CubeIntegrityException;
import com.kylinolap.dict.Dictionary;
import com.kylinolap.dict.DictionaryGenerator;
import com.kylinolap.dict.DictionaryInfo;
import com.kylinolap.dict.DictionaryManager;
import com.kylinolap.dict.TrieDictionary;
import com.kylinolap.dict.lookup.TableSignature;
import com.kylinolap.job.constant.BatchConstants;
import com.kylinolap.metadata.MetadataManager;
import com.kylinolap.metadata.model.realization.TblColRef;

/**
 * @author honma
 */
@SuppressWarnings("rawtypes")
public class MergeCuboidMapperTest extends LocalFileMetadataTestCase {

    private static final Logger logger = LoggerFactory.getLogger(MergeCuboidMapperTest.class);

    MapDriver<Text, Text, Text, Text> mapDriver;
    CubeManager cubeManager;
    CubeInstance cube;
    DictionaryManager dictionaryManager;

    TblColRef lfn;
    TblColRef lsi;
    TblColRef ssc;

    private DictionaryInfo makeSharedDict() throws IOException {
        TableSignature signature = new TableSignature();
        signature.setSize(100);
        signature.setLastModifiedTime(System.currentTimeMillis());
        signature.setPath("fake_common_dict");

        DictionaryInfo newDictInfo = new DictionaryInfo("", "", 0, "string", signature, "");

        List<byte[]> values = new ArrayList<byte[]>();
        values.add(new byte[] { 101, 101, 101 });
        values.add(new byte[] { 102, 102, 102 });
        Dictionary<?> dict = DictionaryGenerator.buildDictionaryFromValueList(newDictInfo, values);
        dictionaryManager.trySaveNewDict(dict, newDictInfo);
        ((TrieDictionary) dict).dump(System.out);

        return newDictInfo;
    }

    @Before
    public void setUp() throws Exception {

        createTestMetadata();

        logger.info("The metadataUrl is : " + this.getTestConfig());

        MetadataManager.removeInstance(this.getTestConfig());
        CubeManager.removeInstance(this.getTestConfig());
        CubeRealizationManager.removeInstance(this.getTestConfig());
        DictionaryManager.removeInstance(this.getTestConfig());

        // hack for distributed cache
        // CubeManager.removeInstance(KylinConfig.createInstanceFromUri("../job/meta"));//to
        // make sure the following mapper could get latest CubeManger
        FileUtils.deleteDirectory(new File("../job/meta"));

        MergeCuboidMapper mapper = new MergeCuboidMapper();
        mapDriver = MapDriver.newMapDriver(mapper);

        cubeManager = CubeManager.getInstance(this.getTestConfig());
        cube = cubeManager.getCube("test_kylin_cube_without_slr_left_join_ready_2_segments");
        dictionaryManager = DictionaryManager.getInstance(getTestConfig());
        lfn = cube.getDescriptor().findColumnRef("TEST_KYLIN_FACT", "LSTG_FORMAT_NAME");
        lsi = cube.getDescriptor().findColumnRef("TEST_KYLIN_FACT", "CAL_DT");
        ssc = cube.getDescriptor().findColumnRef("TEST_CATEGORY_GROUPINGS", "META_CATEG_NAME");

        DictionaryInfo sharedDict = makeSharedDict();

        boolean isFirstSegment = true;
        for (CubeSegment segment : cube.getSegments()) {

            TableSignature signature = new TableSignature();
            signature.setSize(100);
            signature.setLastModifiedTime(System.currentTimeMillis());
            signature.setPath("fake_dict_for" + lfn.getName() + segment.getName());

            DictionaryInfo newDictInfo = new DictionaryInfo(lfn.getTable(), lfn.getColumn().getName(), lfn.getColumn().getZeroBasedIndex(), "string", signature, "");

            List<byte[]> values = new ArrayList<byte[]>();
            values.add(new byte[] { 97, 97, 97 });
            if (isFirstSegment)
                values.add(new byte[] { 99, 99, 99 });
            else
                values.add(new byte[] { 98, 98, 98 });
            Dictionary<?> dict = DictionaryGenerator.buildDictionaryFromValueList(newDictInfo, values);
            dictionaryManager.trySaveNewDict(dict, newDictInfo);
            ((TrieDictionary) dict).dump(System.out);

            segment.putDictResPath(lfn, newDictInfo.getResourcePath());
            segment.putDictResPath(lsi, sharedDict.getResourcePath());
            segment.putDictResPath(ssc, sharedDict.getResourcePath());

            // cubeManager.saveResource(segment.getCubeInstance());
            // cubeManager.afterCubeUpdated(segment.getCubeInstance());
            cubeManager.updateCube(cube);

            isFirstSegment = false;
        }

    }

    @After
    public void after() throws Exception {
        cleanupTestMetadata();
        FileUtils.deleteDirectory(new File("../job/meta"));
    }

    @Test
    public void test() throws IOException, ParseException, CubeIntegrityException {

        String cubeName = "test_kylin_cube_without_slr_left_join_ready_2_segments";

        List<CubeSegment> newSegments = cubeManager.allocateSegments(cube, CubeBuildTypeEnum.MERGE, 1384240200000L, 1386835200000L);

        logger.info("Size of new segments: " + newSegments.size());

        CubeSegment newSeg = newSegments.get(0);
        String segmentName = newSeg.getName();

        ((TrieDictionary) cubeManager.getDictionary(newSeg, lfn)).dump(System.out);

        // hack for distributed cache
        File metaDir = new File("../job/meta");
        FileUtils.copyDirectory(new File(this.getTestConfig().getMetadataUrl()), metaDir);

        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_NAME, cubeName);
        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_SEGMENT_NAME, segmentName);
        // mapDriver.getConfiguration().set(KylinConfig.KYLIN_METADATA_URL,
        // "../job/meta");

        byte[] key = new byte[] { 0, 0, 0, 0, 0, 0, 0, -92, 1, 1, 1 };
        byte[] value = new byte[] { 1, 2, 3 };
        byte[] newkey = new byte[] { 0, 0, 0, 0, 0, 0, 0, -92, 1, 1, 2 };
        byte[] newvalue = new byte[] { 1, 2, 3 };

        mapDriver.withInput(new Text(key), new Text(value));
        mapDriver.withOutput(new Text(newkey), new Text(newvalue));
        mapDriver.setMapInputPath(new Path("/apps/hdmi-prod/b_kylin/prod/kylin-f24668f6-dcff-4cb6-a89b-77f1119df8fa/vac_sw_cube_v4/cuboid/15d_cuboid"));

        mapDriver.runTest();
    }
}
