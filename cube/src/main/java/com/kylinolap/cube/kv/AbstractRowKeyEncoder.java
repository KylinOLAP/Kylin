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
package com.kylinolap.cube.kv;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.cube.CubeSegment;
import com.kylinolap.cube.cuboid.Cuboid;
import com.kylinolap.dict.Dictionary;
import com.kylinolap.metadata.model.realization.TblColRef;

/**
 * 
 * @author xjiang
 * 
 */
public abstract class AbstractRowKeyEncoder {

    public static final byte DEFAULT_BLANK_BYTE = Dictionary.NULL;

    protected static final Logger logger = LoggerFactory.getLogger(AbstractRowKeyEncoder.class);

    private static final Map<String, Map<Long, AbstractRowKeyEncoder>> ENCODER_CACHE = new ConcurrentHashMap<String, Map<Long, AbstractRowKeyEncoder>>();

    public static AbstractRowKeyEncoder createInstance(CubeSegment cubeSeg, Cuboid cuboid) {

        // The storage location identifier is unique for every segment
        Map<Long, AbstractRowKeyEncoder> cubeCache = ENCODER_CACHE.get(cubeSeg.getStorageLocationIdentifier());

        if (cubeCache == null) {
            cubeCache = new HashMap<Long, AbstractRowKeyEncoder>();
            ENCODER_CACHE.put(cuboid.getCube().getName(), cubeCache);
        }

        AbstractRowKeyEncoder encoder = cubeCache.get(cuboid.getId());
        if (encoder == null) {
            encoder = new RowKeyEncoder(cubeSeg, cuboid);
            cubeCache.put(cuboid.getId(), encoder);
        }
        return encoder;
    }

    protected final Cuboid cuboid;
    protected byte blankByte = DEFAULT_BLANK_BYTE;

    protected AbstractRowKeyEncoder(Cuboid cuboid) {
        this.cuboid = cuboid;
    }

    public void setBlankByte(byte blankByte) {
        this.blankByte = blankByte;
    }

    abstract public byte[] encode(Map<TblColRef, String> valueMap);

    abstract public byte[] encode(byte[][] values);
}
