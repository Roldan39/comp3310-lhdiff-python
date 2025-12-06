/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;


public class ByteOrderMark implements Serializable {

    private static final long serialVersionUID = 1L;

    
    public static final ByteOrderMark UTF_8 = new ByteOrderMark(StandardCharsets.UTF_8.name(), 0xEF, 0xBB, 0xBF);

    
    public static final ByteOrderMark UTF_16BE = new ByteOrderMark(StandardCharsets.UTF_16BE.name(), 0xFE, 0xFF);

    
    public static final ByteOrderMark UTF_16LE = new ByteOrderMark(StandardCharsets.UTF_16LE.name(), 0xFF, 0xFE);

    
    public static final ByteOrderMark UTF_32BE = new ByteOrderMark("UTF-32BE", 0x00, 0x00, 0xFE, 0xFF);

    
    public static final ByteOrderMark UTF_32LE = new ByteOrderMark("UTF-32LE", 0xFF, 0xFE, 0x00, 0x00);

    
    public static final char UTF_BOM = '\uFEFF';

    
    private final String charsetName;

    
    private final int[] bytes;

    
    public ByteOrderMark(final String charsetName, final int... bytes) {
        Objects.requireNonNull(charsetName, "charsetName");
        Objects.requireNonNull(bytes, "bytes");
        if (charsetName.isEmpty()) {
            throw new IllegalArgumentException("No charsetName specified");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("No bytes specified");
        }
        this.charsetName = charsetName;
        this.bytes = bytes.clone();
    }

    
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ByteOrderMark)) {
            return false;
        }
        final ByteOrderMark bom = (ByteOrderMark) obj;
        if (bytes.length != bom.length()) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != bom.get(i)) {
                return false;
            }
        }
        return true;
    }

    
    public int get(final int pos) {
        return bytes[pos];
    }

    
    public byte[] getBytes() {
        final byte[] copy = IOUtils.byteArray(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            copy[i] = (byte) bytes[i];
        }
        return copy;
    }

    
    public String getCharsetName() {
        return charsetName;
    }

    int[] getRawBytes() {
        return bytes;
    }

    
    @Override
    public int hashCode() {
        int hashCode = getClass().hashCode();
        for (final int b : bytes) {
            hashCode += b;
        }
        return hashCode;
    }

    
    public int length() {
        return bytes.length;
    }

    
    public boolean matches(final int[] test) {
        // Our test are never null.
        if (bytes == test) {
            return true;
        }
        if (test == null) {
            return false;
        }
        final int length = bytes.length;
        if (test.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (bytes[i] != test[i]) {
                return false;
            }
        }
        return true;
    }

    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append('[');
        builder.append(charsetName);
        builder.append(": ");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("0x");
            builder.append(Integer.toHexString(0xFF & bytes[i]).toUpperCase(Locale.ROOT));
        }
        builder.append(']');
        return builder.toString();
    }

}
