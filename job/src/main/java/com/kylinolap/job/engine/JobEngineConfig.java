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

package com.kylinolap.job.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.cube.model.CubeDesc.CubeCapacity;
import com.kylinolap.job.tools.OptionsHelper;

/**
 * @author ysong1
 */
public class JobEngineConfig {
    private static final Logger logger = LoggerFactory.getLogger(JobEngineConfig.class);
    public static String HADOOP_JOB_CONF_FILENAME = "kylin_job_conf";

    private String getHadoopJobConfFilePath(CubeCapacity capaticy, boolean appendSuffix) throws IOException {
        String hadoopJobConfFile;
        if (appendSuffix)
            hadoopJobConfFile = (HADOOP_JOB_CONF_FILENAME + "_" + capaticy.toString().toLowerCase() + ".xml");
        else
            hadoopJobConfFile = (HADOOP_JOB_CONF_FILENAME + ".xml");

        String path = System.getProperty(KylinConfig.KYLIN_CONF);

        if (path == null) {
            path = System.getenv(KylinConfig.KYLIN_CONF);
        }

        if (path != null) {
            path = path + File.separator + hadoopJobConfFile;
        }

        if (null == path || !new File(path).exists()) {
            File defaultFilePath = new File("/etc/kylin/" + hadoopJobConfFile);

            if (defaultFilePath.exists()) {
                path = defaultFilePath.getAbsolutePath();
            } else {
                logger.debug("Search conf file " + hadoopJobConfFile + "  from classpath ...");
                InputStream is = JobEngineConfig.class.getClassLoader().getResourceAsStream(hadoopJobConfFile);
                if (is == null) {
                    logger.debug("Can't get " + hadoopJobConfFile + " from classpath");
                    logger.debug("No " + hadoopJobConfFile + " file were found");
                } else {
                    File tmp = File.createTempFile(HADOOP_JOB_CONF_FILENAME, ".xml");
                    inputStreamToFile(is, tmp);
                    path = tmp.getAbsolutePath();
                }
            }
        }

        if (null == path || !new File(path).exists()) {
            return "";
        }

        return OptionsHelper.convertToFileURL(path);
    }

    public String getHadoopJobConfFilePath(CubeCapacity capaticy) throws IOException {
        String path = getHadoopJobConfFilePath(capaticy, true);
        if (!StringUtils.isEmpty(path)) {
            logger.info("Chosen job conf is : " + path);
            return path;
        } else {
            path = getHadoopJobConfFilePath(capaticy, false);
            if (!StringUtils.isEmpty(path)) {
                logger.info("Chosen job conf is : " + path);
                return path;
            }
        }
        return "";
    }

    private void inputStreamToFile(InputStream ins, File file) throws IOException {
        OutputStream os = new FileOutputStream(file);
        int bytesRead = 0;
        byte[] buffer = new byte[8192];
        while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.close();
        ins.close();
    }

    // there should be no setters
    private final KylinConfig config;

    public JobEngineConfig(KylinConfig config) {
        this.config = config;
    }

    public KylinConfig getConfig() {
        return config;
    }

    public String getHdfsWorkingDirectory() {
        return config.getHdfsWorkingDirectory();
    }

    /**
     * @return the kylinJobJarPath
     */
    public String getKylinJobJarPath() {
        return config.getKylinJobJarPath();
    }

    /**
     * @return the runAsRemoteCommand
     */
    public boolean isRunAsRemoteCommand() {
        return config.getRunAsRemoteCommand();
    }

    /**
     * @return the zookeeperString
     */
    public String getZookeeperString() {
        return config.getZookeeperString();
    }

    /**
     * @return the remoteHadoopCliHostname
     */
    public String getRemoteHadoopCliHostname() {
        return config.getRemoteHadoopCliHostname();
    }

    /**
     * @return the remoteHadoopCliUsername
     */
    public String getRemoteHadoopCliUsername() {
        return config.getRemoteHadoopCliUsername();
    }

    /**
     * @return the remoteHadoopCliPassword
     */
    public String getRemoteHadoopCliPassword() {
        return config.getRemoteHadoopCliPassword();
    }
    
    public String getMapReduceCmdExtraArgs() {
        return config.getMapReduceCmdExtraArgs();
    }

    /**
     * @return the yarnStatusServiceUrl
     */
    public String getYarnStatusServiceUrl() {
        return config.getYarnStatusServiceUrl();
    }

    /**
     * @return the maxConcurrentJobLimit
     */
    public int getMaxConcurrentJobLimit() {
        return config.getMaxConcurrentJobLimit();
    }

    /**
     * @return the timeZone
     */
    public String getTimeZone() {
        return config.getTimeZone();
    }

    /**
     * @return the adminDls
     */
    public String getAdminDls() {
        return config.getAdminDls();
    }

    /**
     * @return the jobStepTimeout
     */
    public long getJobStepTimeout() {
        return config.getJobStepTimeout();
    }

    /**
     * @return the asyncJobCheckInterval
     */
    public int getAsyncJobCheckInterval() {
        return config.getYarnStatusCheckIntervalSeconds();
    }

    /**
     * @return the flatTableByHive
     */
    public boolean isFlatTableByHive() {
        return config.getFlatTableByHive();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((config == null) ? 0 : config.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JobEngineConfig other = (JobEngineConfig) obj;
        if (config == null) {
            if (other.config != null)
                return false;
        } else if (!config.equals(other.config))
            return false;
        return true;
    }

}