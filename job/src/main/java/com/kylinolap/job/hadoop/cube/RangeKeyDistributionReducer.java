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

package com.kylinolap.job.hadoop.cube;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.StringUtils;

import com.kylinolap.common.mr.KylinReducer;
import com.kylinolap.job.constant.BatchConstants;
import com.kylinolap.metadata.model.cube.CubeDesc.CubeCapacity;

/**
 * @author ysong1
 * 
 */
public class RangeKeyDistributionReducer extends KylinReducer<Text, LongWritable, Text, LongWritable> {

    public static final long ONE_GIGA_BYTES = 1024L * 1024L * 1024L;
    public static final int SMALL_CUT = 5;  //  5 GB per region
    public static final int MEDIUM_CUT = 10; //  10 GB per region
    public static final int LARGE_CUT = 50; // 50 GB per region
    
    public static final int MAX_REGION = 1000;

    private LongWritable outputValue = new LongWritable(0);

    private int cut;
    private long bytesRead = 0;
    private List<Text> gbPoints = new ArrayList<Text>();

    @Override
    protected void setup(Context context) throws IOException {
        super.publishConfiguration(context.getConfiguration());

        CubeCapacity cubeCapacity = CubeCapacity.valueOf(context.getConfiguration().get(BatchConstants.CUBE_CAPACITY));
        switch (cubeCapacity) {
        case SMALL:
            cut = SMALL_CUT;
            break;
        case MEDIUM:
            cut = MEDIUM_CUT;
            break;
        case LARGE:
            cut = LARGE_CUT;
            break;
        }
    }

    @Override
    public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
        for (LongWritable v : values) {
            bytesRead += v.get();
        }
        
        if (bytesRead >= ONE_GIGA_BYTES) {
            gbPoints.add(new Text(key));
            bytesRead = 0; // reset bytesRead
        }
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        int nRegion = Math.round((float) gbPoints.size() / (float) cut);
        nRegion = Math.max(1,  nRegion);
        nRegion = Math.min(MAX_REGION, nRegion);
        
        int gbPerRegion = gbPoints.size() / nRegion;
        gbPerRegion = Math.max(1, gbPerRegion);
        
        System.out.println(nRegion + " regions");
        System.out.println(gbPerRegion + " GB per region");
        
        for (int i = gbPerRegion; i < gbPoints.size(); i += gbPerRegion) {
            Text key = gbPoints.get(i);
            outputValue.set(i);
            System.out.println(StringUtils.byteToHexString(key.getBytes()) + "\t" + outputValue.get());
            context.write(key, outputValue);
        }
    }
}
