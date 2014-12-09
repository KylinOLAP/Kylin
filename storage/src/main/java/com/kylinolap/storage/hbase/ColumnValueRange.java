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
package com.kylinolap.storage.hbase;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.Sets;
import com.kylinolap.cube.kv.RowKeyColumnOrder;
import com.kylinolap.metadata.model.realization.TblColRef;
import com.kylinolap.storage.filter.TupleFilter.FilterOperatorEnum;

/**
 * 
 * @author xjiang
 * 
 */
public class ColumnValueRange {
    private TblColRef column;
    private RowKeyColumnOrder order;
    private String beginValue;
    private String endValue;
    private Set<String> equalValues;

    public ColumnValueRange(TblColRef column, Collection<String> values, FilterOperatorEnum op) {
        this.column = column;
        this.order = RowKeyColumnOrder.getInstance(column.getType());

        switch (op) {
        case EQ:
        case IN:
            equalValues = new HashSet<String>(values);
            refreshBeginEndFromEquals();
            break;
        case LT:
        case LTE:
            endValue = order.max(values);
            break;
        case GT:
        case GTE:
            beginValue = order.min(values);
            break;
        case NEQ:
        case NOTIN:
        case ISNULL: // TODO ISNULL worth pass down as a special equal value
        case ISNOTNULL:
            // let Optiq filter it!
            break;
        default:
            throw new UnsupportedOperationException(op.name());
        }
    }

    public ColumnValueRange(TblColRef column, String beginValue, String endValue, Set<String> equalValues) {
        copy(column, beginValue, endValue, equalValues);
    }

    void copy(TblColRef column, String beginValue, String endValue, Set<String> equalValues) {
        this.column = column;
        this.order = RowKeyColumnOrder.getInstance(column.getType());
        this.beginValue = beginValue;
        this.endValue = endValue;
        this.equalValues = equalValues;
    }

    public TblColRef getColumn() {
        return column;
    }

    public String getBeginValue() {
        return beginValue;
    }

    public String getEndValue() {
        return endValue;
    }

    public Set<String> getEqualValues() {
        return equalValues;
    }

    private void refreshBeginEndFromEquals() {
        this.beginValue = order.min(this.equalValues);
        this.endValue = order.max(this.equalValues);
    }

    public boolean satisfyAll() {
        return beginValue == null && endValue == null; // the NEQ case
    }

    public boolean satisfyNone() {
        if (equalValues != null) {
            return equalValues.isEmpty();
        } else if (beginValue != null && endValue != null) {
            return order.compare(beginValue, endValue) > 0;
        } else {
            return false;
        }
    }

    public void andMerge(ColumnValueRange another) {
        assert this.column.equals(another.column);

        if (another.satisfyAll()) {
            return;
        }

        if (this.satisfyAll()) {
            copy(another.column, another.beginValue, another.endValue, another.equalValues);
            return;
        }

        if (this.equalValues != null && another.equalValues != null) {
            this.equalValues.retainAll(another.equalValues);
            refreshBeginEndFromEquals();
            return;
        }

        if (this.equalValues != null) {
            this.equalValues = filter(this.equalValues, another.beginValue, another.endValue);
            refreshBeginEndFromEquals();
            return;
        }

        if (another.equalValues != null) {
            this.equalValues = filter(another.equalValues, this.beginValue, this.endValue);
            refreshBeginEndFromEquals();
            return;
        }

        this.beginValue = order.max(this.beginValue, another.beginValue);
        this.endValue = order.min(this.endValue, another.endValue);
    }

    private Set<String> filter(Set<String> equalValues, String beginValue, String endValue) {
        Set<String> result = Sets.newHashSetWithExpectedSize(equalValues.size());
        for (String v : equalValues) {
            if (between(v, beginValue, endValue)) {
                result.add(v);
            }
        }
        return equalValues;
    }

    private boolean between(String v, String beginValue, String endValue) {
        return (beginValue == null || order.compare(beginValue, v) <= 0) && (endValue == null || order.compare(v, endValue) <= 0);
    }

    public String toString() {
        if (equalValues == null) {
            return column.getName() + " between " + beginValue + " and " + endValue;
        } else {
            return column.getName() + " in " + equalValues;
        }
    }
}
