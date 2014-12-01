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

package com.kylinolap.storage.hbase.observer;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import com.kylinolap.storage.hbase.observer.SRowAggregators;
import com.kylinolap.storage.hbase.observer.SRowAggregators.HCol;

/**
 * @author yangli9
 * 
 */
public class RowAggregatorsTest {

    @Test
    public void testSerialize() {
        HCol[] hcols = new HCol[] { //
        newHCol("f", "c1", new String[] { "SUM", "COUNT" }, new String[] { "decimal", "long" }), //
                newHCol("f", "c2", new String[] { "SUM", "SUM" }, new String[] { "long", "long" }) };
        SRowAggregators sample = new SRowAggregators(hcols);

        byte[] bytes = SRowAggregators.serialize(sample);
        SRowAggregators copy = SRowAggregators.deserialize(bytes);

        assertTrue(sample.nHCols == copy.nHCols);
        assertTrue(sample.nTotalMeasures == copy.nTotalMeasures);
        assertEquals(sample.hcols[0], copy.hcols[0]);
        assertEquals(sample.hcols[1], copy.hcols[1]);
    }

    private static HCol newHCol(String family, String qualifier, String[] funcNames, String[] dataTypes) {
        return new HCol(Bytes.toBytes(family), Bytes.toBytes(qualifier), funcNames, dataTypes);
    }

    private static void assertEquals(HCol a, HCol b) {
        assertTrue(a.nMeasures == b.nMeasures);
        assertTrue(Arrays.equals(a.family, b.family));
        assertTrue(Arrays.equals(a.qualifier, b.qualifier));
        assertTrue(Arrays.equals(a.funcNames, b.funcNames));
        assertTrue(Arrays.equals(a.dataTypes, b.dataTypes));
    }

}
