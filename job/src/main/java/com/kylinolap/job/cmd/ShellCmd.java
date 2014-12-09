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

package com.kylinolap.job.cmd;

import com.kylinolap.common.util.CliCommandExecutor;
import com.kylinolap.job.constant.JobStepStatusEnum;
import com.kylinolap.job.exception.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * @author xjiang
 * 
 */
public class ShellCmd implements IJobCommand {

    private static Logger log = LoggerFactory.getLogger(ShellCmd.class);

    private final String executeCommand;
    private final ICommandOutput output;
    private final boolean isAsync;
    private final CliCommandExecutor cliCommandExecutor;

    private FutureTask<Integer> future;

    protected ShellCmd(String executeCmd, ICommandOutput out, String host, String user, String password, boolean async) {
        this.executeCommand = executeCmd;
        this.output = out;
        cliCommandExecutor = new CliCommandExecutor();
        cliCommandExecutor.setRunAtRemote(host, user, password);
        this.isAsync = async;
    }

    public ShellCmd(String executeCmd, String host, String user, String password, boolean async) {
        this(executeCmd, new ShellCmdOutput(), host, user, password, async);
    }

    @Override
    public ICommandOutput execute() throws JobException {

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        future = new FutureTask<Integer>(new Callable<Integer>() {
            public Integer call() throws JobException, IOException {
                executor.shutdown();
                return executeCommand(executeCommand);
            }
        });
        executor.execute(future);

        int exitCode = -1;
        if (!isAsync) {
            try {
                exitCode = future.get();
                log.info("finish executing");
            } catch (CancellationException e) {
                log.debug("Command is cancelled");
                exitCode = -2;
            } catch (Exception e) {
                throw new JobException("Error when exectute job " + executeCommand, e);
            } finally {
                if (exitCode == 0) {
                    output.setStatus(JobStepStatusEnum.FINISHED);
                } else if (exitCode == -2) {
                    output.setStatus(JobStepStatusEnum.DISCARDED);
                } else {
                    output.setStatus(JobStepStatusEnum.ERROR);
                }
                output.setExitCode(exitCode);
            }
        }
        return output;
    }

    protected int executeCommand(String command) throws JobException, IOException {
        output.reset();
        output.setStatus(JobStepStatusEnum.RUNNING);
        return cliCommandExecutor.execute(command, output).getFirst();
    }

    @Override
    public void cancel() throws JobException {
        future.cancel(true);
    }

    public static void main(String[] args) throws JobException {
        ShellCmdOutput output = new ShellCmdOutput();
        ShellCmd shellCmd = new ShellCmd(args[0], output, args[1], args[2], args[3], false);
        shellCmd.execute();
        
        System.out.println("============================================================================");
        System.out.println(output.getExitCode());
        System.out.println(output.getOutput());
    }
}
