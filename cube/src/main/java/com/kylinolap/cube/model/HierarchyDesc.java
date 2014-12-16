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
 * Created with IntelliJ IDEA. User: lukhan Date: 9/24/13 Time: 10:46 AM To
 * change this template use File | Settings | File Templates.
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class HierarchyDesc {

    @JsonProperty("level")
    private String level;
    @JsonProperty("column")
    private String column;

    private TblColRef columnRef;

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public TblColRef getColumnRef() {
        return columnRef;
    }

    public void setColumnRef(TblColRef column) {
        this.columnRef = column;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String columnName) {
        this.column = columnName;
    }

    @Override
    public String toString() {
        return "HierarchyDesc [level=" + level + ", column=" + column + "]";
    }

}
