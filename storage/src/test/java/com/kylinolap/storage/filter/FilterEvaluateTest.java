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

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.kylinolap.metadata.model.realization.TblColRef;
import com.kylinolap.storage.tuple.Tuple;

/**
 * @author xjiang
 * 
 */
public class FilterEvaluateTest extends FilterBaseTest {

    @Test
    public void testEvaluate00() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareFilter(groups, 0);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[0]);
    }

    @Test
    public void testEvaluate01() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareFilter(groups, 1);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[1]);
    }

    @Test
    public void testEvaluate02() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildOrFilter(groups);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[0] + matcheCounts[1] - matcheCounts[2]);
    }

    @Test
    public void testEvaluate03() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildAndFilter(groups);

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[2]);
    }

    @Test
    public void testEvaluate04() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "0");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[2]);
    }

    @Test
    public void testEvaluate05() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "1");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[0] - matcheCounts[2]);
    }

    @Test
    public void testEvaluate06() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "2");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 1;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, matcheCounts[1] - matcheCounts[2]);
    }

    @Test
    public void testEvaluate07() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "3");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, 0);
    }

    @Test
    public void testEvaluate08() {
        List<TblColRef> groups = buildGroups();
        TupleFilter filter = buildCompareCaseFilter(groups, "4");

        byte[] bytes = TupleFilterSerializer.serialize(filter);
        TupleFilter newFilter = TupleFilterSerializer.deserialize(bytes);

        int number = 10000;
        int[] matcheCounts = new int[] { 0, 0, 0 };
        Collection<Tuple> tuples = generateTuple(number, groups, matcheCounts);
        int match = evaluateTuples(tuples, newFilter);

        assertEquals(match, number - matcheCounts[0] - matcheCounts[1] + matcheCounts[2]);
    }

}
