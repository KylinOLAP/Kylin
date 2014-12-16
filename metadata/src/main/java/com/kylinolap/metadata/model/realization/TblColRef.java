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

import org.apache.commons.lang.StringUtils;

import com.kylinolap.metadata.model.ColumnDesc;
import com.kylinolap.metadata.model.DataType;
import com.kylinolap.metadata.model.TableDesc;

/**
 * Created with IntelliJ IDEA. User: lukhan Date: 9/26/13 Time: 1:30 PM To
 * change this template use File | Settings | File Templates.
 */
public class TblColRef {

    private static final String INNER_TABLE_NAME = "_kylin_table";

    // used by projection rewrite, see OLAPProjectRel
    public enum InnerDataTypeEnum {

        LITERAL("_literal_type"), DERIVED("_derived_type");

        private final String dateType;

        private InnerDataTypeEnum(String name) {
            this.dateType = name;
        }

        public String getDataType() {
            return dateType;
        }

        public static boolean contains(String name) {
            return LITERAL.getDataType().equals(name) || DERIVED.getDataType().equals(name);
        }
    }

    // used by projection rewrite, see OLAPProjectRel
    public static TblColRef newInnerColumn(String columnName, InnerDataTypeEnum dataType) {
        ColumnDesc column = new ColumnDesc();
        column.setName(columnName);
        TableDesc table = new TableDesc();
        column.setTable(table);
        TblColRef colRef = new TblColRef(column);
        colRef.markInnerColumn(dataType);
        return colRef;
    }

    // ============================================================================

    private ColumnDesc column;

    public TblColRef(ColumnDesc column) {
        this.column = column;
    }

    public ColumnDesc getColumn() {
        return column;
    }

    public void setColumn(ColumnDesc column) {
        this.column = column;
    }

    public String getName() {
        return column.getName();
    }

    public String getTable() {
        if (column.getTable() == null) {
            return null;
        }
        return column.getTable().getIdentity();
    }

    public String getDatatype() {
        return column.getDatatype();
    }

    public DataType getType() {
        return column.getType();
    }

    public void markInnerColumn(InnerDataTypeEnum dataType) {
        this.column.setDatatype(dataType.getDataType());
        this.column.getTable().setName(INNER_TABLE_NAME);
    }

    public boolean isInnerColumn() {
        return InnerDataTypeEnum.contains(getDatatype());
    }

    public boolean isDerivedDataType() {
        return InnerDataTypeEnum.DERIVED.getDataType().equals(getDatatype());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + column.getTable().getIdentity().hashCode();
        result = prime * result + column.getName().hashCode();
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
        TblColRef other = (TblColRef) obj;
        if (!StringUtils.equals(TableDesc.getTableIdentity(column.getTable()), TableDesc.getTableIdentity(other.column.getTable())))
            return false;
        if (!StringUtils.equals(column.getName(), other.column.getName()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return (column.getTable() == null ? null : column.getTable().getIdentity()) + "." + column.getName();
    }
}
