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

import java.io.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.util.Pair;

/**
 * @author yangli9
 */
public class CliCommandExecutor {

    private String remoteHost;
    private String remoteUser;
    private String remotePwd;
    private int remoteTimeoutSeconds = 3600;

    public CliCommandExecutor() {
    }

    public void setRunAtRemote(String host, String user, String pwd) {
        this.remoteHost = host;
        this.remoteUser = user;
        this.remotePwd = pwd;
    }

    public void setRunAtLocal() {
        this.remoteHost = null;
        this.remoteUser = null;
        this.remotePwd = null;
    }
    
    public void copyFile(String localFile, String destDir) throws IOException {
        if (remoteHost == null)
            copyNative(localFile, destDir);
        else
            copyRemote(localFile, destDir);
    }

    private void copyNative(String localFile, String destDir) throws IOException {
        File src = new File(localFile);
        File dest = new File(destDir, src.getName());
        FileUtils.copyFile(src, dest);
    }

    private void copyRemote(String localFile, String destDir) throws IOException {
        SSHClient ssh = new SSHClient(remoteHost, remoteUser, remotePwd);
        try {
            ssh.scpFileToRemote(localFile, destDir);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public Pair<Integer, String> execute(String command) throws IOException {
        return execute(command, null);
    }

    public Pair<Integer, String> execute(String command, Logger logAppender) throws IOException {
        Pair<Integer, String> r;
        if (remoteHost == null) {
            r = runNativeCommand(command, logAppender);
        } else {
            r = runRemoteCommand(command, logAppender);
        }

        if (r.getFirst() != 0) {
            throw new IOException("OS command error exit with " + r.getFirst() + " -- " + command + "\n" + r.getSecond());
        }
        return r;
    }

    private Pair<Integer, String> runRemoteCommand(String command, Logger logAppender) throws IOException {
        SSHClient ssh = new SSHClient(remoteHost, remoteUser, remotePwd);

        SSHClientOutput sshOutput;
        try {
            sshOutput = ssh.execCommand(command, remoteTimeoutSeconds, logAppender);
            int exitCode = sshOutput.getExitCode();
            String output = sshOutput.getText();
            return new Pair<Integer, String>(exitCode, output);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private Pair<Integer, String> runNativeCommand(String command, Logger logAppender) throws IOException {
        String[] cmd = new String[3];
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            cmd[0] = "cmd.exe";
            cmd[1] = "/C";
        } else {
            cmd[0] = "/bin/bash";
            cmd[1] = "-c";
        }
        cmd[2] = command;

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);
        Process proc = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line;
        StringBuilder result = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            result.append("line").append('\n');
            if (logAppender != null) {
                logAppender.log(line);
            }
        }

        try {
            int exitCode = proc.waitFor();
            return new Pair<Integer, String>(exitCode, result.toString());
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

}
