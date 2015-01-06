package com.kylinolap.job.hadoop.cardinality;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hive.hcatalog.mapreduce.HCatInputFormat;

import com.kylinolap.job.hadoop.AbstractHadoopJob;

public class HiveColumnCardinalityJob extends AbstractHadoopJob {
    public static final String JOB_TITLE = "Kylin Hive Column Cardinality Job";

    @SuppressWarnings("static-access")
    protected static final Option OPTION_TABLE = OptionBuilder.withArgName("table name").hasArg().isRequired(true).withDescription("The hive table name").create("table");

    public static final String KEY_INPUT_DELIM = "INPUT_DELIM";
    public static final String OUTPUT_PATH = "/tmp/cardinality";

    /**
     * This is the jar path
     */
    private String jarPath;
    private Configuration conf;
    
    private String table;

    /**
     * MRJobConfig.MAPREDUCE_JOB_CREDENTIALS_BINARY
     */
    private String tokenPath;

    public HiveColumnCardinalityJob() {

    }

    public HiveColumnCardinalityJob(String path, String tokenPath) {
        this.jarPath = path;
        this.tokenPath = tokenPath;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.hadoop.conf.Configured#getConf()
     */
    @Override
    public Configuration getConf() {
        if (conf != null) {
            return conf;
        }
        conf = new JobConf();
        String path = "/apache/hadoop/conf/";
        File file = new File(path);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                File tmp = files[i];
                if (tmp.getName().endsWith(".xml")) {
                    try {
                        conf.addResource(new FileInputStream(tmp));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // conf.addResource("/apache/hadoop/conf/mapred-site.xml");
        if (tokenPath != null) {
            conf.set(MRJobConfig.MAPREDUCE_JOB_CREDENTIALS_BINARY, tokenPath);
            conf.set("hadoop.security.authentication", "kerberos");
            UserGroupInformation.setConfiguration(conf);
            try {
                UserGroupInformation.loginUserFromKeytab("b_kylin@CORP.EBAY.COM", "~/.keytabs/b_kylin.keytab");
                System.out.println("###" + UserGroupInformation.getLoginUser());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return conf;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int run(String[] args) throws Exception {

        Options options = new Options();

        try {
            options.addOption(OPTION_TABLE);
            options.addOption(OPTION_OUTPUT_PATH);

            parseOptions(options, args);

            // start job
            String jobName = JOB_TITLE + getOptionsAsString();
            System.out.println("Starting: " + jobName);
            Configuration conf = getConf();
            job = Job.getInstance(conf, jobName);

            // set job configuration - basic
            if (jarPath == null || !new File(jarPath).exists()) {
                job.setJarByClass(getClass());
            } else {
                job.setJar(jarPath);
            }

            Path output = new Path(getOptionValue(OPTION_OUTPUT_PATH));
            FileOutputFormat.setOutputPath(job, output);
            job.getConfiguration().set("dfs.block.size", "67108864");

            // Mapper
            this.table = getOptionValue(OPTION_TABLE);
            System.out.println("Going to start HiveColumnCardinalityJob on table '" + table + "'");
            String filter = "dt=\"20150101\" and hour=\"00\"";
            HCatInputFormat.setInput(job, "default",
                    table, filter);

            System.out.println("Set input format as HCat on table '" + table + "'");
            
            job.setInputFormatClass(HCatInputFormat.class);
            job.setMapperClass(ColumnCardinalityMapper.class);
            job.setMapOutputKeyClass(IntWritable.class);
            job.setMapOutputValueClass(BytesWritable.class);

            // Reducer - only one
            job.setReducerClass(ColumnCardinalityReducer.class);
            job.setOutputFormatClass(TextOutputFormat.class);
            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(LongWritable.class);
            job.setNumReduceTasks(1);
            

            this.deletePath(job.getConfiguration(), output);

            isAsync = true;
            System.out.println("Going to submit HiveColumnCardinalityJob for table '" + table + "'");
            int result = waitForCompletion(job);

            System.out.println("Get job track url " + job.getJobID() + "\n");
            System.out.println("Get job track url " + job.getTrackingURL() + "\n");
            
            return result;
        } catch (Exception e) {
            printUsage(options);
            e.printStackTrace(System.err);
            log.error(e.getLocalizedMessage(), e);
            return 2;
        }

    }

    public static void main1(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new HiveColumnCardinalityJob(), args);
        System.exit(exitCode);
    }

    public static void main(String[] args) {

        String location = "hdfs://apollo-phx-nn.vip.ebay.com:8020/tmp/f1a98d8a-26b9-452e-ab7b-9f01e5a6459b/shipping_sisense_cube_desc_intermediate_table";
        String tempName = "test";
        String inputFormat = "org.apache.hadoop.mapred.SequenceFileInputFormat";
        String delim = "177";
        String jarPath = "/usr/lib/kylin/kylin-index-latest.jar";

        args = new String[] { "-output", "/tmp/cardinality/" + tempName, "-table", "shipping_sisense_cube_desc_intermediate_table" };
        HiveColumnCardinalityJob job = new HiveColumnCardinalityJob(jarPath, "/tmp/krb5cc_882");
        try {
            ToolRunner.run(job, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> readLines(Path location, Configuration conf) throws Exception {
        FileSystem fileSystem = FileSystem.get(location.toUri(), conf);
        CompressionCodecFactory factory = new CompressionCodecFactory(conf);
        FileStatus[] items = fileSystem.listStatus(location);
        if (items == null)
            return new ArrayList<String>();
        List<String> results = new ArrayList<String>();
        for (FileStatus item : items) {

            // ignoring files like _SUCCESS
            if (item.getPath().getName().startsWith("_")) {
                continue;
            }

            CompressionCodec codec = factory.getCodec(item.getPath());
            InputStream stream = null;

            // check if we have a compression codec we need to use
            if (codec != null) {
                stream = codec.createInputStream(fileSystem.open(item.getPath()));
            } else {
                stream = fileSystem.open(item.getPath());
            }

            StringWriter writer = new StringWriter();
            IOUtils.copy(stream, writer, "UTF-8");
            String raw = writer.toString();
            for (String str : raw.split("\n")) {
                results.add(str);
            }
        }
        return results;
    }

}
