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
package com.kylinolap.common.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Writable;

public class BytesUtil {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static void writeLong(long num, byte[] bytes, int offset, int size) {
        for (int i = offset + size - 1; i >= offset; i--) {
            bytes[i] = (byte) num;
            num >>>= 8;
        }
    }

    public static void writeUnsigned(int num, byte[] bytes, int offset, int size) {
        for (int i = offset + size - 1; i >= offset; i--) {
            bytes[i] = (byte) num;
            num >>>= 8;
        }
    }

    public static long readLong(byte[] bytes, int offset, int size) {
        long integer = 0;
        for (int i = offset, n = offset + size; i < n; i++) {
            integer <<= 8;
            integer |= (long) bytes[i] & 0xFF;
        }
        return integer;
    }

    public static int readUnsigned(byte[] bytes, int offset, int size) {
        int integer = 0;
        for (int i = offset, n = offset + size; i < n; i++) {
            integer <<= 8;
            integer |= (int) bytes[i] & 0xFF;
        }
        return integer;
    }

    public static void writeSigned(int num, byte[] bytes, int offset, int size) {
        writeUnsigned(num, bytes, offset, size);
    }

    public static int readSigned(byte[] bytes, int offset, int size) {
        int integer = (bytes[offset] & 0x80) == 0 ? 0 : -1;
        for (int i = offset, n = offset + size; i < n; i++) {
            integer <<= 8;
            integer |= (int) bytes[i] & 0xFF;
        }
        return integer;
    }

    /**
     * No. bytes needed to store a value as big as the given
     */
    public static int sizeForValue(int maxValue) {
        int size = 0;
        while (maxValue > 0) {
            size++;
            maxValue >>>= 8;
        }
        return size;
    }

    public static int compareByteUnsigned(byte b1, byte b2) {
        int i1 = (int) b1 & 0xFF;
        int i2 = (int) b2 & 0xFF;
        return i1 - i2;
    }

    public static byte[] subarray(byte[] bytes, int start, int end) {
        byte[] r = new byte[end - start];
        System.arraycopy(bytes, start, r, 0, r.length);
        return r;
    }

    public static int compareBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        int r = 0;
        for (int i = 0; i < length; i++) {
            r = src[srcOffset + i] - dst[dstOffset + i];
            if (r != 0)
                break;
        }
        return r;
    }

    // from WritableUtils
    // ============================================================================

    public static void writeVInt(int i, ByteBuffer out) {
        writeVLong(i, out);
    }

    public static void writeVLong(long i, ByteBuffer out) {
        if (i >= -112 && i <= 127) {
            out.put((byte) i);
            return;
        }

        int len = -112;
        if (i < 0) {
            i ^= -1L; // take one's complement'
            len = -120;
        }

        long tmp = i;
        while (tmp != 0) {
            tmp = tmp >> 8;
            len--;
        }

        out.put((byte) len);

        len = (len < -120) ? -(len + 120) : -(len + 112);

        for (int idx = len; idx != 0; idx--) {
            int shiftbits = (idx - 1) * 8;
            long mask = 0xFFL << shiftbits;
            out.put((byte) ((i & mask) >> shiftbits));
        }
    }

    public static long readVLong(ByteBuffer in) {
        byte firstByte = in.get();
        int len = decodeVIntSize(firstByte);
        if (len == 1) {
            return firstByte;
        }
        long i = 0;
        for (int idx = 0; idx < len - 1; idx++) {
            byte b = in.get();
            i = i << 8;
            i = i | (b & 0xFF);
        }
        return (isNegativeVInt(firstByte) ? (i ^ -1L) : i);
    }

    public static int readVInt(ByteBuffer in) {
        long n = readVLong(in);
        if ((n > Integer.MAX_VALUE) || (n < Integer.MIN_VALUE)) {
            throw new IllegalArgumentException("value too long to fit in integer");
        }
        return (int) n;
    }

    private static boolean isNegativeVInt(byte value) {
        return value < -120 || (value >= -112 && value < 0);
    }

    private static int decodeVIntSize(byte value) {
        if (value >= -112) {
            return 1;
        } else if (value < -120) {
            return -119 - value;
        }
        return -111 - value;
    }

    public static void writeUnsigned(int num, int size, ByteBuffer out) {
        for (int i = 0; i < size; i++) {
            out.put((byte) num);
            num >>>= 8;
        }
    }

    public static int readUnsigned(ByteBuffer in, int size) {
        int integer = 0;
        int mask = 0xff;
        int shift = 0;
        for (int i = 0; i < size; i++) {
            integer |= (in.get() << shift) & mask;
            mask = mask << 8;
            shift += 8;
        }
        return integer;
    }

    public static void writeLong(long num, ByteBuffer out) {
        for (int i = 0; i < 8; i++) {
            out.put((byte) num);
            num >>>= 8;
        }
    }

    public static long readLong(ByteBuffer in) {
        long integer = 0;
        int mask = 0xff;
        int shift = 0;
        for (int i = 0; i < 8; i++) {
            integer |= (in.get() << shift) & mask;
            mask = mask << 8;
            shift += 8;
        }
        return integer;
    }

    public static void writeUTFString(String str, ByteBuffer out) {
        byte[] bytes = str == null ? null : Bytes.toBytes(str);
        writeByteArray(bytes, out);
    }

    public static String readUTFString(ByteBuffer in) {
        byte[] bytes = readByteArray(in);
        return bytes == null ? null : Bytes.toString(bytes);
    }

    public static void writeAsciiString(String str, ByteBuffer out) {
        if (str == null) {
            BytesUtil.writeVInt(-1, out);
            return;
        }
        int len = str.length();
        BytesUtil.writeVInt(len, out);
        for (int i = 0; i < len; i++) {
            out.put((byte) str.charAt(i));
        }
    }

    public static String readAsciiString(ByteBuffer in) {
        int len = BytesUtil.readVInt(in);
        if (len < 0) {
            return null;
        }
        String result;
        try {
            if (in.hasArray()) {
                int pos = in.position();
                result = new String(in.array(), pos, len, "ISO-8859-1");
                in.position(pos + len);
            } else {
                byte[] tmp = new byte[len];
                in.get(tmp);
                result = new String(tmp, "ISO-8859-1");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // never happen
        }
        return result;
    }

    public static void writeAsciiStringArray(String[] strs, ByteBuffer out) {
        writeVInt(strs.length, out);
        for (int i = 0; i < strs.length; i++)
            writeAsciiString(strs[i], out);
    }

    public static String[] readAsciiStringArray(ByteBuffer in) {
        int len = readVInt(in);
        String[] strs = new String[len];
        for (int i = 0; i < len; i++)
            strs[i] = readAsciiString(in);
        return strs;
    }

    public static void writeIntArray(int[] array, ByteBuffer out) {
        if (array == null) {
            writeVInt(-1, out);
            return;
        }
        writeVInt(array.length, out);
        for (int i = 0; i < array.length; ++i) {
            writeVInt(array[i], out);
        }
    }

    public static int[] readIntArray(ByteBuffer in) {
        int len = readVInt(in);
        if (len < 0)
            return null;
        int[] array = new int[len];

        for (int i = 0; i < len; ++i) {
            array[i] = readVInt(in);
        }
        return array;
    }

    public static void writeByteArray(byte[] array, ByteBuffer out) {
        if (array == null) {
            writeVInt(-1, out);
            return;
        }
        writeVInt(array.length, out);
        out.put(array);
    }

    public static byte[] readByteArray(ByteBuffer in) {
        int len = readVInt(in);
        if (len < 0)
            return null;

        byte[] array = new byte[len];
        in.get(array);
        return array;
    }

    public static byte[] toBytes(Writable writable) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            writable.write(out);
            out.close();
            bout.close();
            return bout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toReadableText(byte[] array) {
        return toHex(array);
    }

    public static byte[] fromReadableText(String text) {
        String[] tokens = text.split("\\\\x");
        byte[] ret = new byte[tokens.length - 1];
        for (int i = 1; i < tokens.length; ++i) {
            int x = Bytes.toBinaryFromHex((byte) tokens[i].charAt(0));
            x = x << 4;
            int y = Bytes.toBinaryFromHex((byte) tokens[i].charAt(1));
            ret[i - 1] = (byte) (x + y);
        }
        return ret;
    }

    public static String toHex(byte[] array) {
        return toHex(new ImmutableBytesWritable(array));
    }

    public static String toHex(ImmutableBytesWritable bytes) {
        byte[] array = bytes.get();
        int offset = bytes.getOffset();
        int length = bytes.getLength();
        StringBuilder sb = new StringBuilder(length * 4);
        for (int i = 0; i < length; i++) {
            int b = array[offset + i];
            sb.append(String.format("\\x%02X", b & 0xFF));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
    }

}
