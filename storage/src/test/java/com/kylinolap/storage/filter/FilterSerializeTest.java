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

package com.kylinolap.storage.filter;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.kylinolap.metadata.model.ColumnDesc;
import com.kylinolap.metadata.model.TableDesc;
import com.kylinolap.metadata.model.realization.TblColRef;
import com.kylinolap.storage.filter.TupleFilter.FilterOperatorEnum;

/**
 * @author xjiang
 * 
 */
public class FilterSerializeTest extends FilterBaseTest {

    @Test
    public void testSerialize01() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize02() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareFilter(groups, 1);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize03() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildAndFilter(groups);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize04() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildOrFilter(groups);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize05() {
        ColumnDesc column = new ColumnDesc();

        TblColRef colRef = new TblColRef(column);
        List<TblColRef> groups = new ArrayList<TblColRef>();
        groups.add(colRef);
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize06() {
        ColumnDesc column = new ColumnDesc();
        column.setName("META_CATEG_NAME");
        TblColRef colRef = new TblColRef(column);
        List<TblColRef> groups = new ArrayList<TblColRef>();
        groups.add(colRef);
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize07() {
        TableDesc table = new TableDesc();
        table.setName("TEST_KYLIN_FACT");

        ColumnDesc column = new ColumnDesc();
        column.setTable(table);
        TblColRef colRef = new TblColRef(column);
        List<TblColRef> groups = new ArrayList<TblColRef>();
        groups.add(colRef);
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize08() {
        TableDesc table = new TableDesc();

        ColumnDesc column = new ColumnDesc();
        column.setTable(table);
        TblColRef colRef = new TblColRef(column);
        List<TblColRef> groups = new ArrayList<TblColRef>();
        groups.add(colRef);
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize10() {
        List<TblColRef> groups = buildGroups();
        TupleFilter orFilter = buildOrFilter(groups);
        TupleFilter andFilter = buildAndFilter(groups);

        LogicalTupleFilter logicFilter = new LogicalTupleFilter(FilterOperatorEnum.OR);
        logicFilter.addChild(orFilter);
        logicFilter.addChild(andFilter);

        byte[] bytes = TupleFilterSerializer.serialize(logicFilter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(logicFilter, newFilter);
    }

    @Test
    public void testSerialize11() {
        List<TblColRef> groups = buildGroups();
        TupleFilter orFilter = buildOrFilter(groups);
        TupleFilter andFilter = buildAndFilter(groups);

        LogicalTupleFilter logicFilter = new LogicalTupleFilter(FilterOperatorEnum.AND);
        logicFilter.addChild(orFilter);
        logicFilter.addChild(andFilter);

        byte[] bytes = TupleFilterSerializer.serialize(logicFilter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(logicFilter, newFilter);
    }

    @Test
    public void testSerialize12() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCaseFilter(groups);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

    @Test
    public void testSerialize13() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "0");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        compareFilter(filter, newFilter);
    }

}
