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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kylinolap.common.util.StringUtil;
import com.kylinolap.metadata.model.realization.FunctionDesc;

/**
 * Created with IntelliJ IDEA. User: lukhan Date: 9/24/13 Time: 10:44 AM To
 * change this template use File | Settings | File Templates.
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class HBaseMappingDesc {

    @JsonProperty("column_family")
    private HBaseColumnFamilyDesc[] columnFamily;

    // point to the cube instance which contain this HBaseMappingDesc instance.
    private CubeDesc cubeRef;

    public Collection<HBaseColumnDesc> findHBaseColumnByFunction(FunctionDesc function) {
        Collection<HBaseColumnDesc> result = new LinkedList<HBaseColumnDesc>();
        HBaseMappingDesc hbaseMapping = cubeRef.getHBaseMapping();
        if (hbaseMapping == null || hbaseMapping.getColumnFamily() == null) {
            return result;
        }
        for (HBaseColumnFamilyDesc cf : hbaseMapping.getColumnFamily()) {
            for (HBaseColumnDesc c : cf.getColumns()) {
                for (MeasureDesc m : c.getMeasures()) {
                    if (m.getFunction().equals(function)) {
                        result.add(c);
                    }
                }
            }
        }
        return result;
    }

    public CubeDesc getCubeRef() {
        return cubeRef;
    }

    public void setCubeRef(CubeDesc cubeRef) {
        this.cubeRef = cubeRef;
    }

    public HBaseColumnFamilyDesc[] getColumnFamily() {
        return columnFamily;
    }

    public void setColumnFamily(HBaseColumnFamilyDesc[] columnFamily) {
        this.columnFamily = columnFamily;
    }

    public void init(CubeDesc cubeDesc) {
        cubeRef = cubeDesc;

        for (HBaseColumnFamilyDesc cf : columnFamily) {
            cf.setName(cf.getName().toUpperCase());

            for (HBaseColumnDesc c : cf.getColumns()) {
                c.setQualifier(c.getQualifier().toUpperCase());
                StringUtil.toUpperCaseArray(c.getMeasureRefs(), c.getMeasureRefs());
            }
        }
    }

    @Override
    public String toString() {
        return "HBaseMappingDesc [columnFamily=" + Arrays.toString(columnFamily) + "]";
    }

}
