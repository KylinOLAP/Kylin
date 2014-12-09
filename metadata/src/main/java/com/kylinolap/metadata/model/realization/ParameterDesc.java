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
package com.kylinolap.metadata.model.realization;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created with IntelliJ IDEA. User: lukhan Date: 9/30/13 Time: 2:46 PM To
 * change this template use File | Settings | File Templates.
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class ParameterDesc {

    public static final String COLUMN_TYPE = "column";

    @JsonProperty("type")
    private String type;
    @JsonProperty("value")
    private String value;

    private List<TblColRef> colRefs;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public byte[] getBytes() throws UnsupportedEncodingException {
        return value.getBytes("UTF-8");
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<TblColRef> getColRefs() {
        return colRefs;
    }

    public void setColRefs(List<TblColRef> colRefs) {
        this.colRefs = colRefs;
    }

    public boolean isColumnType() {
        return COLUMN_TYPE.equals(type);
    }

    public void normalizeColumnValue() {
        if (isColumnType()) {
            String values[] = value.split("\\s*,\\s*");
            for (int i = 0; i < values.length; i++)
                values[i] = values[i].toUpperCase();
            Arrays.sort(values);
            value = StringUtils.join(",", values);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParameterDesc other = (ParameterDesc) obj;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ParameterDesc [type=" + type + ", value=" + value + "]";
    }

}
