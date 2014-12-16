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

package com.kylinolap.cube.measure;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.junit.Test;

import com.kylinolap.common.hll.HyperLogLogPlusCounter;
import com.kylinolap.cube.kv.RowConstants;
import com.kylinolap.cube.model.MeasureDesc;
import com.kylinolap.metadata.model.realization.FunctionDesc;

/**
 * @author yangli9
 * 
 */
public class MeasureCodecTest {

    @Test
    public void basicTest() {
        MeasureDesc descs[] = new MeasureDesc[] { measure("double"), measure("long"), measure("decimal"), measure("HLLC16"), measure("HLLC16") };
        MeasureCodec codec = new MeasureCodec(descs);

        DoubleWritable d = new DoubleWritable(1.0);
        LongWritable l = new LongWritable(2);
        BigDecimal b = new BigDecimal("333.1234567");
        HyperLogLogPlusCounter hllc = new HyperLogLogPlusCounter(16);
        hllc.add("1234567");
        hllc.add("abcdefg");
        HyperLogLogPlusCounter hllc2 = new HyperLogLogPlusCounter(16);
        hllc.add("1234567");
        hllc.add("abcdefg");
        Object values[] = new Object[] { d, l, b, hllc, hllc2 };

        ByteBuffer buf = ByteBuffer.allocate(RowConstants.ROWVALUE_BUFFER_SIZE);

        codec.encode(values, buf);
        buf.flip();
        System.out.println("size: " + buf.limit());

        Object copy[] = new Object[values.length];
        codec.decode(buf, copy);

        assertTrue(Arrays.equals(values, copy));
    }

    private MeasureDesc measure(String returnType) {
        MeasureDesc desc = new MeasureDesc();
        FunctionDesc func = new FunctionDesc();
        func.setReturnType(returnType);
        desc.setFunction(func);
        return desc;
    }
}
