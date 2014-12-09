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

import static org.junit.Assert.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.apache.hadoop.mrunit.types.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.kylinolap.common.util.LocalFileMetadataTestCase;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.kv.RowKeyDecoder;
import com.kylinolap.cube.measure.MeasureCodec;
import com.kylinolap.cube.model.MeasureDesc;
import com.kylinolap.job.constant.BatchConstants;

/**
 * @author George Song (ysong1)
 * 
 */
public class BaseCuboidMapperTest extends LocalFileMetadataTestCase {

    MapDriver<Text, Text, Text, Text> mapDriver;
    String localTempDir = System.getProperty("java.io.tmpdir") + File.separator;

    @Before
    public void setUp() throws Exception {
        createTestMetadata();

        // hack for distributed cache
        FileUtils.deleteDirectory(new File("../job/meta"));
        FileUtils.copyDirectory(new File(this.getTestConfig().getMetadataUrl()), new File("../job/meta"));

        BaseCuboidMapper<Text> mapper = new BaseCuboidMapper<Text>();
        mapDriver = MapDriver.newMapDriver(mapper);
    }

    @After
    public void after() throws Exception {
        cleanupTestMetadata();
        FileUtils.deleteDirectory(new File("../job/meta"));
    }

    @Test
    public void testMapperWithHeader() throws Exception {
        String cubeName = "test_kylin_cube_with_slr_1_new_segment";
        String segmentName = "20130331080000_20131212080000";
        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_NAME, cubeName);
        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_SEGMENT_NAME, segmentName);
        // mapDriver.getConfiguration().set(BatchConstants.CFG_METADATA_URL,
        // metadata);
        mapDriver.withInput(new Text("key"), new Text("2012-12-15118480Health & BeautyFragrancesWomenAuction15123456789132.331"));
        List<Pair<Text, Text>> result = mapDriver.run();

        CubeManager cubeMgr = CubeManager.getInstance(this.getTestConfig());
        CubeInstance cube = cubeMgr.getCube(cubeName);

        assertEquals(1, result.size());
        Text rowkey = result.get(0).getFirst();
        byte[] key = rowkey.getBytes();
        byte[] header = Bytes.head(key, 26);
        byte[] sellerId = Bytes.tail(header, 18);
        byte[] cuboidId = Bytes.head(header, 8);
        byte[] restKey = Bytes.tail(key, rowkey.getLength() - 26);

        RowKeyDecoder decoder = new RowKeyDecoder(cube.getFirstSegment());
        decoder.decode(key);
        assertEquals("[123456789, 2012-12-15, 11848, Health & Beauty, Fragrances, Women, Auction, 0, 15]", decoder.getValues().toString());

        assertTrue(Bytes.toString(sellerId).startsWith("123456789"));
        assertEquals(511, Bytes.toLong(cuboidId));
        assertEquals(22, restKey.length);

        verifyMeasures(cube.getDescriptor().getMeasures(), result.get(0).getSecond(), "132.33", "132.33", "132.33", 1);
    }

    private void verifyMeasures(List<MeasureDesc> measures, Text valueBytes, String m1, String m2, String m3, long m4) {
        MeasureCodec codec = new MeasureCodec(measures);
        Object[] values = new Object[measures.size()];
        codec.decode(valueBytes, values);
        assertTrue(new BigDecimal(m1).equals(values[0]));
        assertTrue(new BigDecimal(m2).equals(values[1]));
        assertTrue(new BigDecimal(m3).equals(values[2]));
        assertTrue(m4 == ((LongWritable) values[3]).get());
    }

    @Test
    public void testMapperWithNull() throws Exception {
        String cubeName = "test_kylin_cube_with_slr_1_new_segment";
        String segmentName = "20130331080000_20131212080000";
        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_NAME, cubeName);
        mapDriver.getConfiguration().set(BatchConstants.CFG_CUBE_SEGMENT_NAME, segmentName);
        // mapDriver.getConfiguration().set(BatchConstants.CFG_METADATA_URL,
        // metadata);
        mapDriver.withInput(new Text("key"), new Text("2012-12-15118480Health & BeautyFragrances\\NAuction15123456789\\N\\N"));
        List<Pair<Text, Text>> result = mapDriver.run();

        CubeManager cubeMgr = CubeManager.getInstance(this.getTestConfig());
        CubeInstance cube = cubeMgr.getCube(cubeName);

        assertEquals(1, result.size());
        Text rowkey = result.get(0).getFirst();
        byte[] key = rowkey.getBytes();
        byte[] header = Bytes.head(key, 26);
        byte[] sellerId = Bytes.tail(header, 18);
        byte[] cuboidId = Bytes.head(header, 8);
        byte[] restKey = Bytes.tail(key, rowkey.getLength() - 26);

        RowKeyDecoder decoder = new RowKeyDecoder(cube.getFirstSegment());
        decoder.decode(key);
        assertEquals("[123456789, 2012-12-15, 11848, Health & Beauty, Fragrances, null, Auction, 0, 15]", decoder.getValues().toString());

        assertTrue(Bytes.toString(sellerId).startsWith("123456789"));
        assertEquals(511, Bytes.toLong(cuboidId));
        assertEquals(22, restKey.length);

        verifyMeasures(cube.getDescriptor().getMeasures(), result.get(0).getSecond(), "0", "0", "0", 1L);
    }
}
