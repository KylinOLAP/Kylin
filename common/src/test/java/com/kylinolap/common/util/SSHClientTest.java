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

package com.kylinolap.common.util;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.fs.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.kylinolap.common.KylinConfig;

/**
 * @author ysong1
 * 
 */
public class SSHClientTest extends LocalFileMetadataTestCase {

    private boolean isRemote;
    private String hostname;
    private String username;
    private String password;

    private void loadPropertiesFile() throws IOException {

        KylinConfig cfg = KylinConfig.getInstanceFromEnv();

        this.isRemote = cfg.getRunAsRemoteCommand();
        this.hostname = cfg.getRemoteHadoopCliHostname();
        this.username = cfg.getRemoteHadoopCliUsername();
        this.password = cfg.getRemoteHadoopCliPassword();
    }

    @Before
    public void before() throws Exception {
        this.createTestMetadata();
        loadPropertiesFile();
    }

    @After
    public void after() throws Exception {
        this.cleanupTestMetadata();
    }

    @Test
    public void testCmd() throws Exception {
        if (isRemote == false)
            return;
        
        SSHClient ssh = new SSHClient(this.hostname, this.username, this.password);
        SSHClientOutput output = ssh.execCommand("echo hello");
        assertEquals(0, output.getExitCode());
        assertEquals("hello\n", output.getText());
    }

    @Test
    public void testScp() throws Exception {
        if (isRemote == false)
            return;
        
        SSHClient ssh = new SSHClient(this.hostname, this.username, this.password);
        File tmpFile = FileUtil.createLocalTempFile(new File("/tmp/test_scp"), "temp_", false);
        ssh.scpFileToRemote(tmpFile.getAbsolutePath(), "/tmp");
    }
}
