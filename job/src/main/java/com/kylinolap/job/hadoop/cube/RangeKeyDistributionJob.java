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

import java.io.File;

import org.apache.commons.cli.Options;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.model.CubeDesc.CubeCapacity;
import com.kylinolap.job.constant.BatchConstants;
import com.kylinolap.job.hadoop.AbstractHadoopJob;

/**
 * @author xjiang, ysong1
 * 
 */

public class RangeKeyDistributionJob extends AbstractHadoopJob {
    protected static final Logger log = LoggerFactory.getLogger(RangeKeyDistributionJob.class);

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
     */
    @Override
    public int run(String[] args) throws Exception {
        Options options = new Options();

        try {
            options.addOption(OPTION_INPUT_PATH);
            options.addOption(OPTION_OUTPUT_PATH);
            options.addOption(OPTION_JOB_NAME);
            options.addOption(OPTION_CUBE_NAME);

            parseOptions(options, args);

            // start job
            String jobName = getOptionValue(OPTION_JOB_NAME);
            job = Job.getInstance(getConf(), jobName);

            File JarFile = new File(KylinConfig.getInstanceFromEnv().getKylinJobJarPath());
            if (JarFile.exists()) {
                job.setJar(KylinConfig.getInstanceFromEnv().getKylinJobJarPath());
            } else {
                job.setJarByClass(this.getClass());
            }

            addInputDirs(getOptionValue(OPTION_INPUT_PATH), job);

            Path output = new Path(getOptionValue(OPTION_OUTPUT_PATH));
            FileOutputFormat.setOutputPath(job, output);
            // job.getConfiguration().set("dfs.block.size", "67108864");

            // Mapper
            job.setInputFormatClass(SequenceFileInputFormat.class);
            job.setMapperClass(RangeKeyDistributionMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(LongWritable.class);

            // Reducer - only one
            job.setReducerClass(RangeKeyDistributionReducer.class);
            job.setOutputFormatClass(SequenceFileOutputFormat.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(LongWritable.class);
            job.setNumReduceTasks(1);

            this.deletePath(job.getConfiguration(), output);

            String cubeName = getOptionValue(OPTION_CUBE_NAME).toUpperCase();
            CubeManager cubeMgr = CubeManager.getInstance(KylinConfig.getInstanceFromEnv());
            CubeInstance cube = cubeMgr.getCube(cubeName);
            CubeCapacity cubeCapacity = cube.getDescriptor().getCapacity();
            job.getConfiguration().set(BatchConstants.CUBE_CAPACITY, cubeCapacity.toString());

            return waitForCompletion(job);
        } catch (Exception e) {
            printUsage(options);
            log.error(e.getLocalizedMessage(), e);
            return 2;
        }
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new RangeKeyDistributionJob(), args);
        System.exit(exitCode);
    }

}
