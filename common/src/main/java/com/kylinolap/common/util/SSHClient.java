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

/** 
 * @author George Song (ysong1)
 * 
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHClient {
    protected static final Logger logger = LoggerFactory.getLogger(SSHClient.class);

    private String hostname;
    private String username;
    private String password;
    private String identityPath;

    private SSHLogger sshLogger;

    public SSHClient(String hostname, String username, String password, SSHLogger sshLogger) {
        this.hostname = hostname;
        this.username = username;
        if (new File(password).exists()) {
            this.identityPath = new File(password).getAbsolutePath();
            this.password = null;
        } else {
            this.password = password;
            this.identityPath = null;
        }
        this.sshLogger = sshLogger;
    }

    public void scpFileToRemote(String localFile, String remoteTargetDirectory) throws Exception {
        FileInputStream fis = null;
        try {
            System.out.println("SCP file " + localFile + " to " + remoteTargetDirectory);

            Session session = newJSchSession();
            session.connect();

            boolean ptimestamp = false;

            // exec 'scp -t rfile' remotely
            String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + remoteTargetDirectory;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            File _lfile = new File(localFile);

            if (ptimestamp) {
                command = "T " + (_lfile.lastModified() / 1000) + " 0";
                // The access time should be sent here,
                // but it is not accessible with JavaAPI ;-<
                command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    throw new Exception("Error in checkAck()");
                }
            }

            // send "C0644 filesize filename", where filename should not include '/'
            long filesize = _lfile.length();
            command = "C0644 " + filesize + " ";
            if (localFile.lastIndexOf("/") > 0) {
                command += localFile.substring(localFile.lastIndexOf("/") + 1);
            } else if (localFile.lastIndexOf(File.separator) > 0) {
                command += localFile.substring(localFile.lastIndexOf(File.separator) + 1);
            } else {
                command += localFile;
            }
            command += "\n";
            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0) {
                throw new Exception("Error in checkAck()");
            }

            // send a content of lfile
            fis = new FileInputStream(localFile);
            byte[] buf = new byte[1024];
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0)
                    break;
                out.write(buf, 0, len); // out.flush();
            }
            fis.close();
            fis = null;
            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
            if (checkAck(in) != 0) {
                throw new Exception("Error in checkAck()");
            }
            out.close();

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (Exception ee) {
            }
        }
    }

    public SSHClientOutput execCommand(String command) throws Exception {
        return execCommand(command, 7200);
    }

    public SSHClientOutput execCommand(String command, int timeoutSeconds) throws Exception {
        try {
            System.out.println("[" + username + "@" + hostname + "] Execute command: " + command);

            StringBuffer text = new StringBuffer();
            int exitCode = -1;

            Session session = newJSchSession();
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            channel.setInputStream(null);

            // channel.setOutputStream(System.out);

            ((ChannelExec) channel).setErrStream(System.err);

            InputStream in = channel.getInputStream();
            InputStream err = ((ChannelExec) channel).getErrStream();

            channel.connect();

            int timeout = timeoutSeconds;
            byte[] tmp = new byte[1024];
            while (true) {
                timeout--;
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;

                    String line = new String(tmp, 0, i);
                    text.append(line);
                    if (this.sshLogger != null) {
                        this.sshLogger.log(line);
                    }
                }
                while (err.available() > 0) {
                    int i = err.read(tmp, 0, 1024);
                    if (i < 0)
                        break;

                    String line = new String(tmp, 0, i);
                    text.append(line);
                    if (this.sshLogger != null) {
                        this.sshLogger.log(line);
                    }
                }
                if (channel.isClosed()) {
                    if (in.available() > 0)
                        continue;
                    exitCode = channel.getExitStatus();
                    System.out.println("[" + username + "@" + hostname + "] Command exit-status: " + exitCode);

                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) {
                    throw ee;
                }
                if (timeout < 0)
                    throw new Exception("Remote commmand not finished within " + timeoutSeconds + " seconds.");
            }
            channel.disconnect();
            session.disconnect();
            return new SSHClientOutput(exitCode, text.toString());
        } catch (Exception e) {
            throw e;
        }
    }

    private Session newJSchSession() throws JSchException {
        JSch jsch = new JSch();
        if (identityPath != null) {
            jsch.addIdentity(identityPath);
        }

        Session session = jsch.getSession(username, hostname, 22);
        if (password != null) {
            session.setPassword(password);
        }
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
    }

    private int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        // 1 for error,
        // 2 for fatal error,
        // -1
        if (b == 0)
            return b;
        if (b == -1)
            return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while (c != '\n');
            if (b == 1) { // error
                System.out.print(sb.toString());
            }
            if (b == 2) { // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }
}
