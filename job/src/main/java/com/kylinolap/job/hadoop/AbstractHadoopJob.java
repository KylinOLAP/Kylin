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
package com.kylinolap.job.hadoop;

/**
 * @author George Song (ysong1)
 * 
 */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.persistence.ResourceStore;
import com.kylinolap.common.util.StringSplitter;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeSegment;
import com.kylinolap.job.JobInstance;
import com.kylinolap.job.exception.JobException;
import com.kylinolap.job.tools.OptionsHelper;
import com.kylinolap.metadata.model.schema.TableDesc;

@SuppressWarnings("static-access")
public abstract class AbstractHadoopJob extends Configured implements Tool {
    protected static final Logger log = LoggerFactory.getLogger(AbstractHadoopJob.class);

    protected static final Option OPTION_JOB_NAME = OptionBuilder.withArgName("name").hasArg().isRequired(true).withDescription("Job name. For exmaple, Kylin_Cuboid_Builder-clsfd_v2_Step_22-D)").create("jobname");
    protected static final Option OPTION_CUBE_NAME = OptionBuilder.withArgName("name").hasArg().isRequired(true).withDescription("Cube name. For exmaple, flat_item_cube").create("cubename");
    protected static final Option OPTION_SEGMENT_NAME = OptionBuilder.withArgName("name").hasArg().isRequired(true).withDescription("Cube segment name)").create("segmentname");
    protected static final Option OPTION_TABLE_NAME = OptionBuilder.withArgName("name").hasArg().isRequired(true).withDescription("Hive table name.").create("tablename");
    protected static final Option OPTION_INPUT_PATH = OptionBuilder.withArgName("path").hasArg().isRequired(true).withDescription("Input path").create("input");
    protected static final Option OPTION_INPUT_FORMAT = OptionBuilder.withArgName("inputformat").hasArg().isRequired(false).withDescription("Input format").create("inputformat");
    protected static final Option OPTION_INPUT_DELIM = OptionBuilder.withArgName("inputdelim").hasArg().isRequired(false).withDescription("Input delimeter").create("inputdelim");
    protected static final Option OPTION_OUTPUT_PATH = OptionBuilder.withArgName("path").hasArg().isRequired(true).withDescription("Output path").create("output");
    protected static final Option OPTION_NCUBOID_LEVEL = OptionBuilder.withArgName("level").hasArg().isRequired(true).withDescription("N-Cuboid build level, e.g. 1, 2, 3...").create("level");
    protected static final Option OPTION_PARTITION_FILE_PATH = OptionBuilder.withArgName("path").hasArg().isRequired(true).withDescription("Partition file path.").create("input");
    protected static final Option OPTION_HTABLE_NAME = OptionBuilder.withArgName("htable name").hasArg().isRequired(true).withDescription("HTable name").create("htablename");
    protected static final Option OPTION_KEY_COLUMN_PERCENTAGE = OptionBuilder.withArgName("rowkey column percentage").hasArg().isRequired(true).withDescription("Percentage of row key columns").create("columnpercentage");
    protected static final Option OPTION_KEY_SPLIT_NUMBER = OptionBuilder.withArgName("key split number").hasArg().isRequired(true).withDescription("Number of key split range").create("splitnumber");

    protected String name;
    protected String description;
    protected boolean isAsync = false;
    protected OptionsHelper optionsHelper = new OptionsHelper();

    protected Job job;

    protected void parseOptions(Options options, String[] args) throws ParseException {
        optionsHelper.parseOptions(options, args);
    }

    public void printUsage(Options options) {
        optionsHelper.printUsage(getClass().getSimpleName(), options);
    }

    public Option[] getOptions() {
        return optionsHelper.getOptions();
    }

    public String getOptionsAsString() {
        return optionsHelper.getOptionsAsString();
    }

    protected String getOptionValue(Option option) {
        return optionsHelper.getOptionValue(option);
    }

    protected boolean hasOption(Option option) {
        return optionsHelper.hasOption(option);
    }

    protected int waitForCompletion(Job job) throws IOException, InterruptedException, ClassNotFoundException {
        int retVal = 0;
        long start = System.nanoTime();

        if (isAsync) {
            job.submit();
        } else {
            job.waitForCompletion(true);
            retVal = job.isSuccessful() ? 0 : 1;
        }

        log.debug("Job '" + job.getJobName() + "' finished " + (job.isSuccessful() ? "successfully in " : "with failures.  Time taken ") + StringUtils.formatTime((System.nanoTime() - start) / 1000000L));

        return retVal;
    }

    protected static void runJob(Tool job, String[] args) {
        try {
            int exitCode = ToolRunner.run(job, args);
            System.exit(exitCode);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(5);
        }
    }

    public void addInputDirs(String input, Job job) throws IOException {
        for (String inp : StringSplitter.split(input, ",")) {
            inp = inp.trim();
            if (inp.endsWith("/*")) {
                inp = inp.substring(0, inp.length() - 2);
                FileSystem fs = FileSystem.get(job.getConfiguration());
                Path path = new Path(inp);
                FileStatus[] fileStatuses = fs.listStatus(path);
                boolean hasDir = false;
                for (FileStatus stat : fileStatuses) {
                    if (stat.isDirectory()) {
                        hasDir = true;
                        addInputDirs(stat.getPath().toString(), job);
                    }
                }
                if (fileStatuses.length > 0 && !hasDir) {
                    addInputDirs(path.toString(), job);
                }
            } else {
                System.out.println("Add input " + inp);
                FileInputFormat.addInputPath(job, new Path(inp));
            }
        }
    }

    protected void attachKylinPropsAndMetadata(CubeInstance cube, Configuration conf) throws IOException {
        File tmp = File.createTempFile("kylin_job_meta", "");
        tmp.delete(); // we need a directory, so delete the file first

        File metaDir = new File(tmp, "meta");
        metaDir.mkdirs();
        metaDir.getParentFile().deleteOnExit();

        // write kylin.properties
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        File kylinPropsFile = new File(metaDir, "kylin.properties");
        kylinConfig.writeProperties(kylinPropsFile);

        // write cube / cube_desc / dict / table
        ArrayList<String> dumpList = new ArrayList<String>();
        dumpList.add(cube.getResourcePath());
        dumpList.add(cube.getDescriptor().getResourcePath());
        if (cube.isInvertedIndex()) {
            dumpList.add(cube.getInvertedIndexDesc().getResourcePath());
        }
        for (TableDesc table : cube.getDescriptor().listTables()) {
            dumpList.add(table.getResourcePath());
        }

        for (CubeSegment segment : cube.getSegments()) {
            dumpList.addAll(segment.getDictionaryPaths());
        }

        dumpResources(kylinConfig, metaDir, dumpList);

        // hadoop distributed cache
        conf.set("tmpfiles", "file:///" + OptionsHelper.convertToFileURL(metaDir.getAbsolutePath()));
    }

    private void dumpResources(KylinConfig kylinConfig, File metaDir, ArrayList<String> dumpList) throws IOException {
        ResourceStore from = ResourceStore.getStore(kylinConfig);
        KylinConfig localConfig = KylinConfig.createInstanceFromUri(metaDir.getAbsolutePath());
        ResourceStore to = ResourceStore.getStore(localConfig);
        for (String path : dumpList) {
            InputStream in = from.getResource(path);
            if (in == null)
                throw new IllegalStateException("No resource found at -- " + path);
            long ts = from.getResourceTimestamp(path);
            to.putResource(path, in, ts);
            log.info("Dumped resource " + path + " to " + metaDir.getAbsolutePath());
        }
    }

    protected void deletePath(Configuration conf, Path path) throws IOException {
        FileSystem fs = FileSystem.get(conf);
        if (fs.exists(path)) {
            fs.delete(path, true);
        }
    }

    protected double getTotalMapInputMB() throws ClassNotFoundException, IOException, InterruptedException, JobException {
        if (job == null) {
            throw new JobException("Job is null");
        }

        long mapInputBytes = 0;
        InputFormat<?, ?> input = ReflectionUtils.newInstance(job.getInputFormatClass(), job.getConfiguration());
        for (InputSplit split : input.getSplits(job)) {
            mapInputBytes += split.getLength();
        }
        if (mapInputBytes == 0) {
            throw new IllegalArgumentException("Map input splits are 0 bytes, something is wrong!");
        }
        double totalMapInputMB = (double) mapInputBytes / 1024 / 1024;
        return totalMapInputMB;
    }

    protected int getMapInputSplitCount() throws ClassNotFoundException, JobException, IOException, InterruptedException {
        if (job == null) {
            throw new JobException("Job is null");
        }
        InputFormat<?, ?> input = ReflectionUtils.newInstance(job.getInputFormatClass(), job.getConfiguration());
        return input.getSplits(job).size();
    }

    public static KylinConfig loadKylinPropsAndMetadata(Configuration conf) throws IOException {
        File metaDir = new File("meta");
        System.setProperty(KylinConfig.KYLIN_CONF, metaDir.getAbsolutePath());
        System.out.println("The absolute path for meta dir is " + metaDir.getAbsolutePath());
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        kylinConfig.setMetadataUrl(metaDir.getCanonicalPath());
        return kylinConfig;
    }

    public void kill() throws JobException {
        if (job != null) {
            try {
                job.killJob();
            } catch (IOException e) {
                throw new JobException(e);
            }
        }
    }

    public Map<String, String> getInfo() throws JobException {
        if (job != null) {
            Map<String, String> status = new HashMap<String, String>();
            if (null != job.getJobID()) {
                status.put(JobInstance.MR_JOB_ID, job.getJobID().toString());
            }
            if (null != job.getTrackingURL()) {
                status.put(JobInstance.YARN_APP_URL, job.getTrackingURL().toString());
            }

            return status;
        } else {
            throw new JobException("Job is null");
        }
    }

    public Counters getCounters() throws JobException {
        if (job != null) {
            try {
                return job.getCounters();
            } catch (IOException e) {
                throw new JobException(e);
            }
        } else {
            throw new JobException("Job is null");
        }
    }

    public void setAsync(boolean isAsync) {
        this.isAsync = isAsync;
    }

}
