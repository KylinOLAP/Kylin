package com.kylinolap.cube.estimation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.cuboid.Cuboid;
import com.kylinolap.cube.cuboid.CuboidScheduler;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.cube.model.DimensionDesc;
import com.kylinolap.cube.model.HierarchyDesc;
import com.kylinolap.cube.model.MeasureDesc;
import com.kylinolap.cube.model.RowKeyColDesc;
import com.kylinolap.cube.model.RowKeyDesc;
import com.kylinolap.metadata.model.DataType;

/**
 * Created by honma on 9/1/14.
 */
public class CubeSizeEstimationCLI {

    public static class RowKeyColInfo {
        public List<List<Integer>> hierachyColBitIndice;
        public List<Integer> nonHierachyColBitIndice;
    }

    public static long estimatedCubeSize(String cubeName, long[] cardinality) {
        KylinConfig config = KylinConfig.getInstanceFromEnv();
        CubeManager cubeManager = CubeManager.getInstance(config);
        CubeInstance cubeInstance = cubeManager.getCube(cubeName);
        CubeDesc cubeDesc = cubeInstance.getDescriptor();

        CuboidScheduler scheduler = new CuboidScheduler(cubeDesc);
        long baseCuboid = Cuboid.getBaseCuboidId(cubeDesc);
        LinkedList<Long> cuboidQueue = new LinkedList<Long>();
        cuboidQueue.push(baseCuboid);

        long totalSpace = 0;

        while (!cuboidQueue.isEmpty()) {
            long cuboidID = cuboidQueue.pop();
            Collection<Long> spanningCuboid = scheduler.getSpanningCuboid(cuboidID);
            for (Long sc : spanningCuboid) {
                cuboidQueue.push(sc);
            }

            totalSpace += estimateCuboidSpace(cuboidID, cardinality, cubeDesc);
        }
        return totalSpace;
    }

    public static long estimateCuboidSpace(long cuboidID, long[] cardinality, CubeDesc cubeDesc) {

        RowKeyColInfo rowKeyColInfo = extractRowKeyInfo(cubeDesc);
        RowKeyDesc rowKeyDesc = cubeDesc.getRowkey();

        long rowCount = 1;
        int[] rowKeySpaces = estimateRowKeyColSpace(rowKeyDesc, cardinality);
        int dimensionSpace = 0;
        int measureSpace = getMeasureSpace(cubeDesc);

        for (List<Integer> hlist : rowKeyColInfo.hierachyColBitIndice) {
            // for hierachy columns, the cardinality of the most detailed column
            // nominates.
            int i;
            for (i = 0; i < hlist.size() && rowKeyColExists(hlist.get(i), cuboidID); ++i) {
                dimensionSpace += rowKeySpaces[hlist.get(i)];
            }

            if (i != 0)
                rowCount *= cardinality[hlist.get(i - 1)];
        }

        for (int index : rowKeyColInfo.nonHierachyColBitIndice) {
            if (rowKeyColExists(index, cuboidID)) {
                rowCount *= cardinality[index];
                dimensionSpace += rowKeySpaces[index];
            }
        }
        return rowCount * (dimensionSpace + measureSpace);
    }

    private static int[] estimateRowKeyColSpace(RowKeyDesc rowKeyDesc, long[] cardinality) {
        RowKeyColDesc[] rowKeyColDescs = rowKeyDesc.getRowKeyColumns();
        int[] ret = new int[rowKeyColDescs.length];
        for (int i = 0; i < rowKeyColDescs.length; ++i) {
            RowKeyColDesc rowKeyColDesc = rowKeyColDescs[rowKeyColDescs.length - 1 - i];
            if (rowKeyColDesc.getDictionary() == null) {
                if (rowKeyColDesc.getLength() == 0)
                    throw new IllegalStateException("The non-dictionary col " + rowKeyColDesc.getColumn() + " has length of 0");
                ret[i] = rowKeyColDesc.getLength();
            } else {
                ret[i] = estimateDictionaryColSpace(cardinality[i]);
            }
        }
        return ret;
    }

    // TODO what if it's date dictionary?
    private static int estimateDictionaryColSpace(long cardinality) {
        long mask = 1L;
        int i;
        for (i = Long.SIZE - 1; i >= 0; i--) {
            if ((cardinality & (mask << i)) != 0) {
                break;
            }
        }

        if (i < 0)
            throw new IllegalStateException("the cardinality is 0");

        return ((i + 1) + 7) / 8;// the bytes required to save at most
                                 // cardinality numbers
    }

    private static int getMeasureSpace(CubeDesc cubeDesc) {
        int space = 0;
        for (MeasureDesc measureDesc : cubeDesc.getMeasures()) {
            DataType returnType = measureDesc.getFunction().getReturnDataType();
            space += returnType.getSpaceEstimate();
        }
        return space;
    }

    private static boolean rowKeyColExists(int bitIndex, long cuboidID) {
        long mask = 1L << bitIndex;
        return (cuboidID & mask) != 0;
    }

    private static RowKeyColInfo extractRowKeyInfo(CubeDesc cubeDesc) {
        RowKeyDesc rowKeyDesc = cubeDesc.getRowkey();
        RowKeyColInfo info = new RowKeyColInfo();
        info.hierachyColBitIndice = new ArrayList<List<Integer>>();
        info.nonHierachyColBitIndice = new ArrayList<Integer>();
        HashSet<Integer> heirachyIndexSet = new HashSet<Integer>();

        for (DimensionDesc dim : cubeDesc.getDimensions()) {
            if (dim.getHierarchy() != null) {
                LinkedList<Integer> hlist = new LinkedList<Integer>();
                for (HierarchyDesc hierarchyDesc : dim.getHierarchy()) {
                    int index = rowKeyDesc.getColumnBitIndex(hierarchyDesc.getColumnRef());
                    hlist.add(index);
                    heirachyIndexSet.add(index);
                }
                info.hierachyColBitIndice.add(hlist);
            }
        }

        for (int i = 0; i < rowKeyDesc.getRowKeyColumns().length; ++i) {
            if (!heirachyIndexSet.contains(i)) {
                info.nonHierachyColBitIndice.add(i);
            }
        }

        return info;
    }

}
