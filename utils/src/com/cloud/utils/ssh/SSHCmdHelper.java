// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.utils.ssh;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Session;

public class SSHCmdHelper {
    private static final Logger s_logger = Logger.getLogger(SSHCmdHelper.class);

    public static com.trilead.ssh2.Connection acquireAuthorizedConnection(String ip, String username, String password) {
        return acquireAuthorizedConnection(ip, 22, username, password);
    }

    public static com.trilead.ssh2.Connection acquireAuthorizedConnection(String ip, int port, String username, String password) {
        com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(ip, port);
        try {
            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(username, password)) {
                String[] methods = sshConnection.getRemainingAuthMethods(username);
                StringBuffer mStr = new StringBuffer();
                for (int i = 0; i < methods.length; i++) {
                    mStr.append(methods[i]);
                }
                s_logger.warn("SSH authorizes failed, support authorized methods are " + mStr);
                return null;
            }
            return sshConnection;
        } catch (IOException e) {
            s_logger.warn("Get SSH connection failed", e);
            return null;
        }
    }

    public static void releaseSshConnection(com.trilead.ssh2.Connection sshConnection) {
        if (sshConnection != null) {
            sshConnection.close();
        }
    }

    public static boolean sshExecuteCmd(com.trilead.ssh2.Connection sshConnection, String cmd, int nTimes) {
        for (int i = 0; i < nTimes; i++) {
            try {
                if (sshExecuteCmdOneShot(sshConnection, cmd))
                    return true;
            } catch (sshException e) {
                continue;
            }
        }
        return false;
    }

    public static int sshExecuteCmdWithExitCode(com.trilead.ssh2.Connection sshConnection, String cmd) {
        return sshExecuteCmdWithExitCode(sshConnection, cmd, 3);
    }

    public static int sshExecuteCmdWithExitCode(com.trilead.ssh2.Connection sshConnection, String cmd, int nTimes) {
        for (int i = 0; i < nTimes; i++) {
            try {
                return sshExecuteCmdOneShotWithExitCode(sshConnection, cmd);
            } catch (sshException e) {
                continue;
            }
        }
        return -1;
    }

    public static boolean sshExecuteCmd(com.trilead.ssh2.Connection sshConnection, String cmd) {
        return sshExecuteCmd(sshConnection, cmd, 3);
    }

    public static int sshExecuteCmdOneShotWithExitCode(com.trilead.ssh2.Connection sshConnection, String cmd) throws sshException {
        s_logger.debug("Executing cmd: " + cmd);
        Session sshSession = null;
        try {
            sshSession = sshConnection.openSession();
            // There is a bug in Trilead library, wait a second before
            // starting a shell and executing commands, from http://spci.st.ewi.tudelft.nl/chiron/xref/nl/tudelft/swerl/util/SSHConnection.html
            Thread.sleep(1000);

            if (sshSession == null) {
                throw new sshException("Cannot open ssh session");
            }

            sshSession.execCommand(cmd);

            InputStream stdout = sshSession.getStdout();
            InputStream stderr = sshSession.getStderr();

            byte[] buffer = new byte[8192];
            while (true) {
                if (stdout == null || stderr == null) {
                    throw new sshException("stdout or stderr of ssh session is null");
                }

                if ((stdout.available() == 0) && (stderr.available() == 0)) {
                    int conditions = sshSession.waitForCondition(ChannelCondition.STDOUT_DATA | ChannelCondition.STDERR_DATA | ChannelCondition.EOF, 120000);

                    if ((conditions & ChannelCondition.TIMEOUT) != 0) {
                        s_logger.info("Timeout while waiting for data from peer.");
                        break;
                    }

                    if ((conditions & ChannelCondition.EOF) != 0) {
                        if ((conditions & (ChannelCondition.STDOUT_DATA | ChannelCondition.STDERR_DATA)) == 0) {
                            break;
                        }
                    }
                }

                while (stdout.available() > 0) {
                    stdout.read(buffer);
                }

                while (stderr.available() > 0) {
                    stderr.read(buffer);
                }
            }

            if (buffer[0] != 0)
                s_logger.debug(cmd + " output:" + new String(buffer));

            Thread.sleep(1000);
            return sshSession.getExitStatus();
        } catch (Exception e) {
            s_logger.debug("Ssh executed failed", e);
            throw new sshException("Ssh executed failed " + e.getMessage());
        } finally {
            if (sshSession != null)
                sshSession.close();
        }
    }

    public static boolean sshExecuteCmdOneShot(com.trilead.ssh2.Connection sshConnection, String cmd) throws sshException {
        return sshExecuteCmdOneShotWithExitCode(sshConnection, cmd) == 0;
    }
}
