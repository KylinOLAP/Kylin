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

import java.util.Arrays;

import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.util.BytesUtil;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.CubeSegment;
import com.kylinolap.cube.model.RowKeyDesc;
import com.kylinolap.dict.Dictionary;
import com.kylinolap.metadata.model.realization.TblColRef;

/**
 * Read/Write column values from/into bytes
 * 
 * @author yangli9
 */
@SuppressWarnings("unchecked")
public class RowKeyColumnIO {

    private static final Logger logger = LoggerFactory.getLogger(RowKeyColumnIO.class);

    private CubeSegment seg;
    private RowKeyDesc rowkeyDesc;
    private boolean forceNoDict = Boolean.getBoolean("forceNoDict");

    public RowKeyColumnIO(CubeSegment cubeSeg) {
        this.seg = cubeSeg;
        this.rowkeyDesc = seg.getCubeDesc().getRowkey();
    }

    public CubeSegment getCubeSegment() {
        return seg;
    }

    public int getColumnLength(TblColRef col) {
        Dictionary<String> dict = getDictionary(col);
        if (dict == null) {
            return rowkeyDesc.getColumnLength(col);
        } else {
            return dict.getSizeOfId();
        }
    }

    public void writeColumn(TblColRef column, byte[] value, int valueLen, byte dft, byte[] output, int outputOffset) {
        writeColumn(column, value, valueLen, 0, dft, output, outputOffset);
    }

    public void writeColumn(TblColRef column, byte[] value, int valueLen, int roundingFlag, byte dft, byte[] output, int outputOffset) {

        Dictionary<String> dict = getDictionary(column);
        int columnLen = getColumnLength(column);

        // non-dict value
        if (dict == null) {
            byte[] valueBytes = padFixLen(columnLen, value);
            System.arraycopy(valueBytes, 0, output, outputOffset, columnLen);
            return;
        }

        // dict value
        try {
            int id = dict.getIdFromValueBytes(value, 0, valueLen, roundingFlag);
            BytesUtil.writeUnsigned(id, output, outputOffset, dict.getSizeOfId());
        } catch (IllegalArgumentException ex) {
            for (int i = outputOffset; i < outputOffset + columnLen; i++)
                output[i] = dft;
            logger.error("Can't translate value " + Bytes.toString(value, 0, valueLen) + " to dictionary ID, roundingFlag " + roundingFlag + ". Using default value " + String.format("\\x%02X", dft));
        }
    }

    private byte[] padFixLen(int length, byte[] valueBytes) {
        int valLen = valueBytes.length;
        if (valLen == length) {
            return valueBytes;
        } else if (valLen < length) {
            byte[] newValueBytes = new byte[length];
            System.arraycopy(valueBytes, 0, newValueBytes, 0, valLen);
            Arrays.fill(newValueBytes, valLen, length, RowConstants.ROWKEY_PLACE_HOLDER_BYTE);
            return newValueBytes;
        } else {
            return Arrays.copyOf(valueBytes, length);
        }
    }

    public String readColumnString(TblColRef col, byte[] bytes, int bytesLen) {
        Dictionary<String> dict = getDictionary(col);
        if (dict == null) {
            bytes = Bytes.head(bytes, bytesLen);
            if (isNull(bytes)) {
                return null;
            }
            bytes = removeFixLenPad(bytes, 0);
            return Bytes.toString(bytes);
        } else {
            int id = BytesUtil.readUnsigned(bytes, 0, bytesLen);
            try {
                String value = dict.getValueFromId(id);
                return value;
            } catch (IllegalArgumentException e) {
                logger.error("Can't get dictionary value for column " + col.getName() + " (id = " + id + ")");
                return "";
            }
        }
    }

    private boolean isNull(byte[] bytes) {
        // all 0xFF is NULL
        if (bytes.length == 0)
            return false;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != AbstractRowKeyEncoder.DEFAULT_BLANK_BYTE)
                return false;
        }
        return true;
    }

    private byte[] removeFixLenPad(byte[] bytes, int offset) {
        int padCount = 0;
        for (int i = offset; i < bytes.length; i++) {
            byte vb = bytes[i];
            if (vb == RowConstants.ROWKEY_PLACE_HOLDER_BYTE) {
                padCount++;
            }
        }

        int size = bytes.length - offset - padCount;
        byte[] stripBytes = new byte[size];
        int index = 0;
        for (int i = offset; i < bytes.length; i++) {
            byte vb = bytes[i];
            if (vb != RowConstants.ROWKEY_PLACE_HOLDER_BYTE) {
                stripBytes[index++] = vb;
            }
        }
        return stripBytes;
    }

    public Dictionary<String> getDictionary(TblColRef col) {
        if (forceNoDict)
            return null;

        return (Dictionary<String>) CubeManager.getInstance(seg.getCubeInstance().getConfig()).getDictionary(seg, col);
    }

}
