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

package com.kylinolap.cube.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kylinolap.metadata.model.realization.TblColRef;

/**
 * @author yangli9
 * 
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class RowKeyColDesc {

    @JsonProperty("column")
    private String column;
    @JsonProperty("length")
    private int length;
    @JsonProperty("dictionary")
    private String dictionary;
    @JsonProperty("mandatory")
    private boolean mandatory = false;

    // computed
    private int bitIndex;
    private TblColRef colRef;

    public String getDictionary() {
        return dictionary;
    }

    public String getColumn() {
        return column;
    }

    void setColumn(String column) {
        this.column = column;
    }

    public int getLength() {
        return length;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    void setBitIndex(int index) {
        this.bitIndex = index;
    }

    public TblColRef getColRef() {
        return colRef;
    }

    void setColRef(TblColRef colRef) {
        this.colRef = colRef;
    }

    @Override
    public String toString() {
        return "RowKeyColDesc [column=" + column + ", length=" + length + ", dictionary=" + dictionary + ", mandatory=" + mandatory + "]";
    }

}
