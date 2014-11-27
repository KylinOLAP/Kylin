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

package com.kylinolap.storage.hbase.observer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.regionserver.RegionScanner;

import com.kylinolap.cube.measure.MeasureAggregator;
import com.kylinolap.storage.hbase.observer.SRowProjector.AggrKey;

/**
 * @author yangli9
 * 
 */
public class AggregationScanner implements RegionScanner {

    private RegionScanner outerScanner;

    public AggregationScanner(SRowType type, SRowFilter filter, SRowProjector groupBy, SRowAggregators aggrs, RegionScanner innerScanner) throws IOException {

        AggregateRegionObserver.LOG.info("Kylin Coprocessor start");

        AggregationCache aggCache;
        Stats stats = new Stats();

        aggCache = buildAggrCache(innerScanner, type, groupBy, aggrs, filter, stats);
        stats.countOutputRow(aggCache.getSize());
        this.outerScanner = aggCache.getScanner(innerScanner);

        AggregateRegionObserver.LOG.info("Kylin Coprocessor aggregation done: " + stats);
    }

    @SuppressWarnings("rawtypes")
    AggregationCache buildAggrCache(final RegionScanner innerScanner, SRowType type, SRowProjector projector, SRowAggregators aggregators, SRowFilter filter, Stats stats) throws IOException {

        AggregationCache aggCache = new AggregationCache(aggregators, 0);

        SRowTuple tuple = new SRowTuple(type);
        boolean hasMore = true;
        List<Cell> results = new ArrayList<Cell>();
        while (hasMore) {
            results.clear();
            hasMore = innerScanner.nextRaw(results);
            if (results.isEmpty())
                continue;

            if (stats != null)
                stats.countInputRow(results);

            Cell cell = results.get(0);
            tuple.setUnderlying(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength());
            if (filter != null && filter.evaluate(tuple) == false)
                continue;

            AggrKey aggKey = projector.getRowKey(results);
            MeasureAggregator[] bufs = aggCache.getBuffer(aggKey);
            aggregators.aggregate(bufs, results);

            aggCache.checkMemoryUsage();
        }
        return aggCache;
    }

    @Override
    public boolean next(List<Cell> results) throws IOException {
        return outerScanner.next(results);
    }

    @Override
    public boolean next(List<Cell> result, int limit) throws IOException {
        return outerScanner.next(result, limit);
    }

    @Override
    public boolean nextRaw(List<Cell> result) throws IOException {
        return outerScanner.nextRaw(result);
    }

    @Override
    public boolean nextRaw(List<Cell> result, int limit) throws IOException {
        return outerScanner.nextRaw(result, limit);
    }

    @Override
    public void close() throws IOException {
        outerScanner.close();
    }

    @Override
    public HRegionInfo getRegionInfo() {
        return outerScanner.getRegionInfo();
    }

    @Override
    public boolean isFilterDone() throws IOException {
        return outerScanner.isFilterDone();
    }

    @Override
    public boolean reseek(byte[] row) throws IOException {
        return outerScanner.reseek(row);
    }

    @Override
    public long getMaxResultSize() {
        return outerScanner.getMaxResultSize();
    }

    @Override
    public long getMvccReadPoint() {
        return outerScanner.getMvccReadPoint();
    }

    private static class Stats {
        long inputRows = 0;
        long inputBytes = 0;
        long outputRows = 0;

        // have no outputBytes because that requires actual serialize all the
        // aggregator buffers

        public void countInputRow(List<Cell> row) {
            inputRows++;
            inputBytes += row.get(0).getRowLength();
            for (int i = 0, n = row.size(); i < n; i++) {
                inputBytes += row.get(i).getValueLength();
            }
        }

        public void countOutputRow(long rowCount) {
            outputRows += rowCount;
        }

        public String toString() {
            double percent = (double) outputRows / inputRows * 100;
            return Math.round(percent) + "% = " + outputRows + " (out rows) / " + inputRows + " (in rows); in bytes = " + inputBytes + "; est. out bytes = " + Math.round(inputBytes * percent / 100);
        }
    }
}
