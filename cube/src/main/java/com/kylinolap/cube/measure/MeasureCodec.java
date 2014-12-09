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

import java.nio.ByteBuffer;
import java.util.Collection;

import org.apache.hadoop.io.Text;

import com.kylinolap.cube.model.MeasureDesc;

/**
 * @author yangli9
 * 
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class MeasureCodec {

    int nMeasures;
    MeasureSerializer[] serializers;

    public MeasureCodec(Collection<MeasureDesc> measureDescs) {
        this((MeasureDesc[]) measureDescs.toArray(new MeasureDesc[measureDescs.size()]));
    }

    public MeasureCodec(MeasureDesc... measureDescs) {
        String[] dataTypes = new String[measureDescs.length];
        for (int i = 0; i < dataTypes.length; i++) {
            dataTypes[i] = measureDescs[i].getFunction().getReturnType();
        }
        init(dataTypes);
    }

    public MeasureCodec(String... dataTypes) {
        init(dataTypes);
    }

    private void init(String[] dataTypes) {
        nMeasures = dataTypes.length;
        serializers = new MeasureSerializer[nMeasures];

        for (int i = 0; i < nMeasures; i++) {
            serializers[i] = MeasureSerializer.create(dataTypes[i]);
        }
    }

    public MeasureSerializer getSerializer(int idx) {
        return serializers[idx];
    }

    public void decode(Text bytes, Object[] result) {
        decode(ByteBuffer.wrap(bytes.getBytes(), 0, bytes.getLength()), result);
    }

    public void decode(ByteBuffer buf, Object[] result) {
        assert result.length == nMeasures;
        for (int i = 0; i < nMeasures; i++) {
            result[i] = serializers[i].deserialize(buf);
        }
    }

    public void encode(Object[] values, ByteBuffer out) {
        assert values.length == nMeasures;
        for (int i = 0; i < nMeasures; i++) {
            serializers[i].serialize(values[i], out);
        }
    }
}
