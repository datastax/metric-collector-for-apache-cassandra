package com.datastax.mcac;



import java.io.File;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.utils.FBUtilities;


/**
 * Responsible for managing the lifecycle and settings of Collectd and the Insights plugin
 */
public class CollectdController
{
    public static final String OPERATING_SYSTEM = System.getProperty("os.name").toLowerCase();

    public static final boolean isWindows = OPERATING_SYSTEM.contains("windows");
    public static final boolean isLinux = OPERATING_SYSTEM.contains("linux");
    public static final boolean isMacOSX = OPERATING_SYSTEM.contains("mac os x");


    public static final Supplier<CollectdController> instance = Suppliers.memoize(CollectdController::new);
    private static final MustacheFactory mf = new DefaultMustacheFactory();
    private static final Logger logger = LoggerFactory.getLogger(CollectdController.class);
    private static final String errorPreamble = isLinux ? "" : "Collectd is only supported on Linux, ";

    public static final String DSE_OVERRIDE_SOCKET_FILE_PROP = System.getProperty("dse.insights_socket_file_override");

    // http://man7.org/linux/man-pages/man7/unix.7.html
    // note: osx has a limit of 104 so using that as the lower bound
    // -1 to account for the control char
    public static final Integer MAX_SOCKET_NAME = 103;

    public static final Supplier<String> defaultSocketFile = Suppliers.memoize(() -> {
        File f = null;
        try
        {
            f = File.createTempFile("ds-", ".sock");
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
        String socketFile = f.getAbsolutePath();

        if (socketFile.length() > MAX_SOCKET_NAME)
        {
            String tmp = socketFile.substring(0, MAX_SOCKET_NAME);
            logger.warn("The unix socket ({}) path is greater than the unix standard limit, cropping to {}", socketFile, tmp);
            socketFile = tmp;
        }

        return socketFile;
    });


    private CollectdConfig collectdConfig;
    private ScribeConfig scribeConfig;
    private ProcessState currentState;

    private int collectdPid;

    private CollectdController()
    {
        currentState = ProcessState.INIT;
        collectdConfig = null;
        scribeConfig = null;
        collectdPid = 0;
    }

    public enum ProcessState
    {
        INIT,
        UNKNOWN,
        STARTED,
        STOPPED,
        BROKEN
    }

    private synchronized int findCollectdPid()
    {
        assert collectdConfig != null;

        try
        {
            if (!Files.exists(Paths.get(collectdConfig.pidFile)))
            {
                //Handle case when the pid file was deleted but collectd is still running
                //Restart the process
                if (collectdPid > 0)
                    stop();

                return -1;
            }

            List<String> pidFileLines = Files.readAllLines(Paths.get(collectdConfig.pidFile));
            if (pidFileLines.size() == 0 || StringUtils.isEmpty(pidFileLines.get(0)))
            {
                return -1;
            }

            int pid = Integer.valueOf(pidFileLines.get(0));
            boolean pidIsCollectd;

                pidIsCollectd = ShellUtils.<Boolean>executeShellWithHandlers(
                        String.format("/bin/ps -p %d -o command=", pid),
                        (input, err) -> {

                            String processName = input.readLine().toLowerCase();
                            if (processName.contains("collectd"))
                                return true;

                            return false;
                        },
                        (exitCode, err) -> false);


            return pidIsCollectd ? pid : -1;
        }
        catch (Exception e)
        {
            if (currentState != ProcessState.INIT)
                logger.warn("Collectd PID check error", e);

            //Bad / No pid
            return -1;
        }
    }

    public Optional<String> collectdRoot()
    {
        try
        {
            String collectdRoot = Paths.get(CollectdController.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParent().toAbsolutePath().toString() + "/collectd";

            if (!Files.isDirectory(Paths.get(collectdRoot)))
            {
                logger.error(
                        "{}Collectd root directory wrong. Please fix in datastax-metric-collector.yaml : {}",
                        errorPreamble, Paths.get(collectdRoot).toAbsolutePath()
                );

                return Optional.empty();
            }

            return Optional.of(collectdRoot);
        }
        catch (URISyntaxException e)
        {
            logger.info("Mangled jar location", e);
            return Optional.empty();
        }
    }

    public Optional<File> findCollectd()
    {
        try
        {
            String collectdRoot = Paths.get(CollectdController.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParent().toAbsolutePath().toString() + "/collectd";


            Path fullPath = Paths.get(collectdRoot, "usr", "sbin", "collectd_wrapper");

            if (!Files.exists(fullPath))
            {
                logger.error(
                        "{}Collectd root found but {} is missing",
                        errorPreamble, fullPath.toAbsolutePath()
                );

                return Optional.empty();
            }

            if (!Files.isExecutable(fullPath))
            {
                logger.error(
                        "{}{} is not executable",
                        errorPreamble, fullPath.toAbsolutePath()
                );

                return Optional.empty();
            }

            return Optional.of(fullPath.toFile());
        }
        catch (URISyntaxException e)
        {
            logger.info("Mangled jar location", e);
            return Optional.empty();
        }
    }

    private CollectdConfig generateCollectdConf(
            String socketFile,
            Configuration configuration
    )
    {
        collectdConfig = new CollectdConfig.Builder()
                .withCollectdRoot(collectdRoot().get())
                .withSocketFile(socketFile)
                .withLogDir(configuration.log_dir)
                .build();

        return collectdConfig;
    }

    private ScribeConfig generateScribeConf(Configuration configuration)
    {
        assert collectdConfig != null;

        try
        {
            ScribeConfig.Builder builder = new ScribeConfig.Builder()
                    .withInsightsUploadEnabled(configuration.isInsightsUploadEnabled())
                    .withInsightsStreamingEnabled(configuration.isInsightsStreamingEnabled())
                    .withWriteToDiskEnabled(configuration.isWriteToDiskEnabled())
                    .withDataDir(configuration.data_dir)
                    .withServiceURL(new URL(configuration.upload_url))
                    .withUploadInterval(configuration.upload_interval_in_seconds)
                    .withMaxDirSizeBytes(configuration.data_dir_max_size_in_mb * 1000000L)
                    .withMetricUpdateGapInSeconds(configuration.metricUpdateGapInSeconds())
                    .withExistingConfigFile(collectdConfig.scribeConfigFile);

            Optional<String> token = Optional.ofNullable(configuration.insights_token);
            if (token.isPresent())
                builder = builder.withToken(token.get());

            scribeConfig = builder.build();

            return scribeConfig;
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public synchronized ProcessState start(String socketFile, Configuration insightsConfig)
    {

        if (DSE_OVERRIDE_SOCKET_FILE_PROP != null)
        {
            currentState = ProcessState.STARTED;
            collectdPid = -1;
            return currentState;
        }

        CollectdConfig collectdConfig = generateCollectdConf(socketFile, insightsConfig);
        collectdPid = findCollectdPid();

        //Check if collectd is already running, if so it's an old version then
        if (collectdPid > 0)
        {
            stop();
            assert currentState == ProcessState.STOPPED;
        }

        Optional<File> collectdExe = findCollectd();

        if (!collectdExe.isPresent())
        {
            currentState = ProcessState.BROKEN;
            return currentState;
        }

        try
        {
            if (collectdConfig != null)
            {
                collectdConfig.generate();
            }

            ScribeConfig scribeConfig = generateScribeConf(insightsConfig);
            if (scribeConfig != null)
            {
                scribeConfig.generate();
            }

            boolean success = ShellUtils.executeShellWithHandlers(
                    collectdExe.get().getAbsolutePath() + " -C " + collectdConfig.configFile.getAbsolutePath()
                            + " -P " + collectdConfig.pidFile,
                    (input, output) -> {
                        //Wait for upto 5 seconds for the pidFile to be created
                        long start = System.nanoTime();
                        while ((System.nanoTime() - start) < TimeUnit.SECONDS.toNanos(5))
                        {
                            if (findCollectdPid() > 0)
                                return true;

                            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                        }

                        return false;
                    },
                    (exitCode, error) -> {
                        String line;
                        while ((line = error.readLine()) != null)
                            logger.warn(line);

                        logger.warn("Exit code = {}", exitCode);
                        return false;
                    },
                    //Pass along the collectd basedir and avoid preloading jemalloc
                    ImmutableMap.of("BASEDIR", new File(collectdConfig.collectdRoot).getAbsolutePath(),
                            "LD_PRELOAD", "", "DYLD_INSERT_LIBRARIES", "")
            );

            if (success)
            {
                collectdPid = findCollectdPid();
                currentState = collectdPid > 0 ? ProcessState.STARTED : ProcessState.BROKEN;
            }
            else
            {
                logger.error("Collectd start failed");
                currentState = ProcessState.UNKNOWN;
            }
        }
        catch (IOException e)
        {
            logger.error("Exception starting collectd", e);
            currentState = ProcessState.UNKNOWN;
        }

        return currentState;
    }

    public boolean healthCheck()
    {
        if (DSE_OVERRIDE_SOCKET_FILE_PROP != null)
            return true;

        logger.debug("Current State = {}", currentState);
        return currentState == ProcessState.STARTED && collectdPid > 0 && collectdPid == findCollectdPid();
    }

    public synchronized ProcessState stop()
    {
        if (currentState == ProcessState.STOPPED || collectdPid < 1)
        {
            return currentState;
        }

        try
        {
            logger.info("Stopping collectd");

            boolean killed = ShellUtils.executeShellWithHandlers(
                    String.format(
                            "kill %d",
                            collectdPid
                    ),
                    (input, err) -> true,
                    (exitcode, err) -> false
            );
            currentState = killed ? ProcessState.STOPPED : ProcessState.UNKNOWN;
            collectdPid = -1;
        }
        catch (IOException e)
        {
            logger.error("Error stopping collectd", e);
            currentState = ProcessState.UNKNOWN;
        }

        return currentState;
    }

    public synchronized ProcessState reloadPlugin(Configuration newConfig)
    {
        if(currentState != ProcessState.STARTED){
            logger.trace("not reloading, plugin is not started");
            return currentState;
        }

        try
        {
            logger.info("Generating new scribe config");
            scribeConfig = generateScribeConf(newConfig);
            scribeConfig.generate();
        }
        catch (IOException e)
        {
            logger.error("Error reloading", e);
            currentState = ProcessState.UNKNOWN;
        }

        if (currentState != ProcessState.INIT)
            logger.debug("Insights reloaded {}", currentState);

        return currentState;
    }

    static class CollectdConfig
    {
        private static String COLLECTD_CONF_TEMPLATE = "collectd.conf.tmpl";
        private static final Mustache collectdConf = mf.compile(COLLECTD_CONF_TEMPLATE);


        public final String collectdRoot;
        public final String logDir;
        public final String pidFile;
        public final String socketFile;
        public final Boolean isMac = isMacOSX;
        public final String hostName = FBUtilities.getBroadcastAddress().getHostAddress();
        public final String dataCenter = DatabaseDescriptor.getEndpointSnitch().getDatacenter(FBUtilities.getBroadcastAddress());
        public final String rack = DatabaseDescriptor.getEndpointSnitch().getDatacenter(FBUtilities.getBroadcastAddress());
        public final String cluster = DatabaseDescriptor.getClusterName();
        public final File configFile;
        public final File scribeConfigFile;


        private CollectdConfig(String collectdRoot, String logDir, String socketFile)
        {
            this.collectdRoot = collectdRoot;
            this.logDir = logDir;
            this.pidFile = Paths.get(logDir, "ds-collectd.pid").toFile().getAbsolutePath();
            this.socketFile = socketFile;

            try
            {
                this.configFile = Files.createTempFile(
                        "ds-collectd-",
                        ".conf",
                        PosixFilePermissions.asFileAttribute(ImmutableSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        ))
                ).toFile();

                this.scribeConfigFile = Files.createTempFile(
                        "ds-collectd-insights-",
                        ".conf",
                        PosixFilePermissions.asFileAttribute(ImmutableSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        ))
                ).toFile();
                this.scribeConfigFile.deleteOnExit();
                this.configFile.deleteOnExit();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        synchronized File generate() throws IOException
        {
            try (FileWriter writer = new FileWriter(configFile))
            {
                collectdConf.execute(writer, this);
            }

            return configFile;
        }

        static class Builder
        {
            private String collectdRoot;
            private String logDir;
            private String socketFile;

            Builder withCollectdRoot(String collectdRoot)
            {
                Path path = Paths.get(collectdRoot);
                if (!Files.isDirectory(path))
                    throw new RuntimeException("collectdRoot missing " + collectdRoot);

                this.collectdRoot = path.toFile().getAbsolutePath();
                return this;
            }

            Builder withLogDir(String logDir)
            {
                assert logDir != null;

                FileUtils.createDirectory(logDir);

                Path path = Paths.get(logDir);

                if (!Files.isWritable(path))
                    throw new RuntimeException("logDir not writable " + logDir);

                this.logDir = path.toFile().getAbsolutePath();
                return this;
            }


            Builder withSocketFile(String socketFile)
            {
                assert socketFile != null;

                // http://man7.org/linux/man-pages/man7/unix.7.html
                if (socketFile.length() >= MAX_SOCKET_NAME)
                {
                    String tmp = socketFile.substring(0, MAX_SOCKET_NAME);
                    logger.warn("The unix socket ({}) path is greater than the unix standard limit, cropping to {}", socketFile, tmp);
                    socketFile = tmp;
                }

                this.socketFile = socketFile;
                return this;
            }

            CollectdConfig build()
            {
                return new CollectdConfig(collectdRoot, logDir, socketFile);
            }
        }
    }

    static class ScribeConfig
    {
        private static String SCRIBE_CONF_TEMPLATE = "scribe.conf.tmpl";
        private static final Mustache scribeConf = mf.compile(SCRIBE_CONF_TEMPLATE);

        public final boolean insightsUploadEnabled;
        public final boolean insightsStreamingEnabled;
        public final boolean writeToDiskEnabled;
        public final Integer port;
        public final String host;
        public final String httpPath;
        public final Integer uploadIntervalSec;
        public final Long metricUpdateGapInSec;
        public final Long maxDataDirSizeBytes;
        public final String token;
        public final String dataDir;
        public final Boolean isSSL;
        public final File configFile;
        public final String caCertFile = "etc/ssl/certs/ca-certificates.crt";


        private ScribeConfig(
                boolean insightsUploadEnabled,
                boolean insightsStreamingEnabled,
                boolean writeToDiskEnabled,
                Integer port,
                String host,
                String httpPath,
                Boolean isSSL,
                Integer uploadIntervalSec,
                Long metricUpdateGapInSec,
                Long maxDataDirSizeBytes,
                String token,
                String dataDir,
                File existingConfigFile
        )
        {
            this.insightsUploadEnabled = insightsUploadEnabled || insightsStreamingEnabled;
            this.insightsStreamingEnabled = insightsStreamingEnabled;
            this.writeToDiskEnabled = writeToDiskEnabled;
            this.port = port;
            this.host = host;
            this.httpPath = httpPath;
            this.isSSL = isSSL;
            this.uploadIntervalSec = uploadIntervalSec;
            this.metricUpdateGapInSec = metricUpdateGapInSec;
            this.maxDataDirSizeBytes = maxDataDirSizeBytes;
            this.dataDir = dataDir;
            this.token = token;
            this.configFile = existingConfigFile;
        }

        synchronized File generate() throws IOException
        {
            assert configFile != null;

            try (FileWriter writer = new FileWriter(configFile))
            {
                scribeConf.execute(writer, this);
            }

            return configFile;
        }

        static class Builder
        {
            private URL serviceUrl;
            private Integer intervalSec;
            private Long maxDataDirSizeBytes;
            private Long metricUpdateGapInSec;
            private String dataDir;
            private String token;
            private File existingConfigFile;
            private boolean insightsUploadEnabled;
            private boolean insightsStreamingEnabled;
            private boolean writeToDiskEnabled;

            Builder withServiceURL(URL serviceUrl)
            {
                this.serviceUrl = serviceUrl;
                return this;
            }

            Builder withToken(String token)
            {
                this.token = token;
                return this;
            }

            Builder withDataDir(String dataDir)
            {
                Path path = Paths.get(dataDir);

                FileUtils.createDirectory(dataDir);

                if (!Files.isWritable(path))
                    throw new RuntimeException("dataDir not writable " + dataDir);

                this.dataDir = path.toFile().getAbsolutePath();

                return this;
            }

            Builder withExistingConfigFile(File existingConfigFile)
            {
                this.existingConfigFile = existingConfigFile;

                return this;
            }

            Builder withUploadInterval(Integer intervalSec)
            {
                assert intervalSec >= 0;
                this.intervalSec = intervalSec;
                return this;
            }

            Builder withMaxDirSizeBytes(Long sizeInBytes)
            {
                assert sizeInBytes > 1 << 20 : "Size must be > 1mb";
                this.maxDataDirSizeBytes = sizeInBytes;

                return this;
            }

            Builder withMetricUpdateGapInSeconds(Long metricUpdateGapInSec)
            {
                assert metricUpdateGapInSec >= 0;
                this.metricUpdateGapInSec = metricUpdateGapInSec;

                return this;
            }

            Builder withInsightsUploadEnabled(boolean insightsUploadEnabled)
            {
                this.insightsUploadEnabled = insightsUploadEnabled;
                return this;
            }

            Builder withInsightsStreamingEnabled(boolean insightsStreamingEnabled)
            {
                this.insightsStreamingEnabled = insightsStreamingEnabled;
                return this;
            }

            Builder withWriteToDiskEnabled(boolean writeToDiskEnabled)
            {
                this.writeToDiskEnabled = writeToDiskEnabled;
                return this;
            }

            ScribeConfig build()
            {
                int port = serviceUrl.getPort() == -1 ? serviceUrl.getDefaultPort() : serviceUrl.getPort();
                String host = serviceUrl.getHost();
                boolean isSSL = serviceUrl.getProtocol().equalsIgnoreCase("https");

                insightsUploadEnabled = insightsUploadEnabled || insightsStreamingEnabled;

                if (insightsUploadEnabled)
                {
                    try
                    {
                        //Vet hostname
                        InetAddress.getByName(host).getHostAddress();
                    }
                    catch (UnknownHostException e)
                    {
                        //FIXME: These checks should happen in scribe
                        //Avoiding an NPE for now..
                        logger.warn("Insights service url {} is unreachable, no insights will be sent."
                                + " This can be fixed with dsetool insights_config --upload_url", serviceUrl.toString());
                        host = null;
                    }
                }

                return new ScribeConfig(
                        insightsUploadEnabled,
                        insightsStreamingEnabled,
                        writeToDiskEnabled,
                        port,
                        host,
                        serviceUrl.getPath(),
                        isSSL,
                        intervalSec,
                        metricUpdateGapInSec,
                        maxDataDirSizeBytes,
                        token,
                        dataDir,
                        existingConfigFile
                );
            }
        }
    }
}

