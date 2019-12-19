package com.datastax.mcac.utils;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;

public class DockerHelper
{
    private DockerClientConfig config;
    private DockerClient dockerClient;
    private String container;
    private File dataDir;
    private List<String> startupArgs;
    private Logger logger = LoggerFactory.getLogger(DockerHelper.class);

    public DockerHelper(File dataDir, List<String> startupArgs) {
        this.config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.dataDir = dataDir;
        this.startupArgs = startupArgs;
    }

    public void startCassandra(String version)
    {
        File dockerFile = new File("./docker/" + version + "/Dockerfile");
        if (!dockerFile.exists())
            throw new RuntimeException("Missing " + dockerFile.getAbsolutePath());

        File baseDir = new File(System.getProperty("dockerFileRoot","."));
        String name = "cassandra";
        List<Integer> ports = Arrays.asList(9042);
        List<String> volumeDescList = Arrays.asList(dataDir.getAbsolutePath() + ":/var/lib/cassandra");
        List<String> envList = null;
        List<String> cmdList = Lists.newArrayList("cassandra", "-f");

        if (startupArgs != null)
            cmdList.addAll(startupArgs);

        this.container = startDocker(dockerFile, baseDir, name, ports, volumeDescList, envList, cmdList);

        waitForPort("localhost",9042, Duration.ofMillis(50000), logger, true);
    }

    public static boolean waitForPort(String hostname, int port, Duration timeout, Logger logger, boolean quiet)
    {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        while(System.nanoTime() < deadlineNanos)
        {
            Cluster cluster = null;
            try
            {
                cluster = Cluster.builder()
                        .addContactPoint("127.0.0.1")
                        .build();
                Session session = cluster.connect();

                ResultSet rs = session.execute("select release_version from system.local");
                Row row = rs.one();
                logger.info("Connected to {}:{} Version {}", hostname, port, row.getString("release_version"));
                return true;
            }
            catch (Throwable t)
            {
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            }
            finally
            {
                if (cluster != null) cluster.close();
            }
        }

        //The port never opened
        if (!quiet)
        {
            logger.warn("Failed to connect to {}:{} after {} sec", hostname, port, timeout.getSeconds());
        }

        return false;
    }

    private String startDocker(File dockerFile, File baseDir, String name, List<Integer> ports, List<String> volumeDescList, List<String> envList, List<String> cmdList)
    {
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd().withStatusFilter(Arrays.asList("exited"));
        listContainersCmd.getFilters().put("name", Arrays.asList(name));
        try
        {
            List<Container> stoppedContainers = listContainersCmd.exec();
            for (Container stoppedContainer : stoppedContainers)
            {
                String id = stoppedContainer.getId();
                logger.info("Removing exited container: " + id);
                dockerClient.removeContainerCmd(id).exec();
            }
        }
        catch (Exception e)
        {
            logger.error("Unable to contact docker, make sure docker is up and try again.");
            logger.error("If docker is installed make sure this user has access to the docker group.");
            logger.error("$ sudo gpasswd -a ${USER} docker && newgrp docker");
            System.exit(1);
        }

        Container containerId = searchContainer(name);
        if (containerId != null)
        {
            return containerId.getId();
        }

        BuildImageResultCallback callback = new BuildImageResultCallback()
        {
            @Override
            public void onNext(BuildResponseItem item)
            {
                //System.out.println("" + item);
                super.onNext(item);
            }
        };

        dockerClient.buildImageCmd()
                .withBaseDirectory(baseDir)
                .withDockerfile(dockerFile)
                .withTags(Sets.newHashSet(name))
                .exec(callback)
                .awaitImageId();

        List<ExposedPort> tcpPorts = new ArrayList<>();
        List<PortBinding> portBindings = new ArrayList<>();
        for (Integer port : ports)
        {
            ExposedPort tcpPort = ExposedPort.tcp(port);
            Ports.Binding binding = new Ports.Binding("0.0.0.0", String.valueOf(port));
            PortBinding pb = new PortBinding(binding, tcpPort);

            tcpPorts.add(tcpPort);
            portBindings.add(pb);
        }

        List<Volume> volumeList = new ArrayList<>();
        List<Bind> volumeBindList = new ArrayList<>();
        for (String volumeDesc : volumeDescList)
        {
            String volFrom = volumeDesc.split(":")[0];
            String volTo = volumeDesc.split(":")[1];
            Volume vol = new Volume(volTo);
            volumeList.add(vol);
            volumeBindList.add(new Bind(volFrom, vol));
        }


        CreateContainerResponse containerResponse;
        if (envList == null)
        {
            containerResponse = dockerClient.createContainerCmd(name)
                    .withCmd(cmdList)
                    .withExposedPorts(tcpPorts)
                    .withHostConfig(
                            new HostConfig()
                                    .withPortBindings(portBindings)
                                    .withPublishAllPorts(true)
                                    .withBinds(volumeBindList)
                    )
                    .withName(name)
                    .exec();
        } else {
            containerResponse = dockerClient.createContainerCmd(name)
                    .withEnv(envList)
                    .withExposedPorts(tcpPorts)
                    .withHostConfig(
                            new HostConfig()
                                    .withPortBindings(portBindings)
                                    .withPublishAllPorts(true)
                                    .withBinds(volumeBindList)
                    )
                    .withName(name)
                    .exec();
        }

        dockerClient.startContainerCmd(containerResponse.getId()).exec();


        dockerClient.logContainerCmd(containerResponse.getId()).withStdOut(true).withStdErr(true).withFollowStream(true).withTailAll().exec(new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item)
            {
                logger.info(new String(item.getPayload()));
            }
        });

        return containerResponse.getId();
    }

    private Container searchContainer(String name)
    {
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd().withStatusFilter(Collections.singletonList("running"));
        listContainersCmd.getFilters().put("name", Arrays.asList(name));
        List<Container> runningContainers = null;
        try {
            runningContainers = listContainersCmd.exec();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Unable to contact docker, make sure docker is up and try again.");
            System.exit(1);
        }

        if (runningContainers.size() >= 1) {
            //Container test = runningContainers.get(0);
            logger.info(String.format("The container %s is already running", name));

            return runningContainers.get(0);
        }
        return null;
    }

    public void stopCassandra()
    {
        if (container != null)
            dockerClient.stopContainerCmd(container).exec();
    }
}
