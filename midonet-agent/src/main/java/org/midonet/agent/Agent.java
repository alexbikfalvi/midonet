/*
 * Copyright 2016 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.agent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Agent {

    private final static Agent instance = new Agent();
    private final static Logger log = LoggerFactory.getLogger(Logging.ROOT);

    private static final int MIDOLMAN_ERROR_CODE_MISSING_CONFIG_FILE = 1;
    private static final int MIDOLMAN_ERROR_CODE_UNHANDLED_EXCEPTION = 6001;

    private Agent() {
    }

    public static void main(String[] args) {
        try {
            instance.run(args);
        } catch (Throwable e) {
            log.error("Unhandled exception in main method", e);
            dumpStacks();
            System.exit(MIDOLMAN_ERROR_CODE_UNHANDLED_EXCEPTION);
        }
    }

    private void run(String[] args) throws IOException {
        setUncaughtExceptionHandler();
        initialize(args);

        log.info("Running manual GC to tenure pre-allocated objects");
        System.gc();
    }

    private static void dumpStacks() {
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
        for (Thread thread : traces.keySet()) {
            System.err.print("\"" + thread.getName() + "\" ");
            if (thread.isDaemon())
                System.err.print("daemon ");
            System.err.print(String.format(
                "priority=%x tid=%x %s [%x]\n",
                thread.getPriority(), thread.getId(), thread.getState(),
                System.identityHashCode(thread)));

            StackTraceElement[] trace = traces.get(thread);
            for (StackTraceElement e : trace) {
                System.err.println("\tat " + e.toString());
            }
        }
    }

    private void setUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
            log.error("Unhandled exception: ", e);
            dumpStacks();
            System.exit(MIDOLMAN_ERROR_CODE_UNHANDLED_EXCEPTION);
        });
    }

    private void initialize(String[] args) throws IOException {
        log.info("Starting MidoNet Agent...");

        // Log git commit information.
        logGitProperties();

        // Log command line and JVM info.
        logArgumentsAndJvm(args);

        log.info("Adding shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread("shutdown") {
            @Override
            public void run() {
                shutdownHook();
            }
        });
    }

    private void logGitProperties() {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("git.properties");

        if (stream == null) {
            log.info("GIT properties not available");
            return;
        }

        Properties properties = new Properties();
        try {
            properties.load(stream);
        } catch (IOException e) {
            log.warn("Loading GIT properties failed", e);
            return;
        }

        log.info("Branch: {}", properties.get("git.branch"));
        log.info("Commit time: {}", properties.get("git.commit.time"));
        log.info("Commit identifier: {}", properties.get("git.commit.id"));
        log.info("Commit user: {}", properties.get("git.commit.user.name"));
        log.info("Build time: {}", properties.get("git.build.time"));
        log.info("Build user: {}", properties.get("git.build.user.name"));
    }

    private void logArgumentsAndJvm(String[] args) {
        log.info("Command-line arguments: {}", Arrays.toString(args));

        log.info("JVM options:");
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        for(String argument : arguments){
            log.info("  {}", argument);
        }
    }

    private void shutdownHook() {
        log.info("Shutting down MidoNet Agent...");
    }

    private void exitMissingConfigFile(String configFilePath) {
        log.error("MidoNet Agent config file {} missing: exiting",
                  configFilePath);
        System.exit(MIDOLMAN_ERROR_CODE_MISSING_CONFIG_FILE);
    }

}
