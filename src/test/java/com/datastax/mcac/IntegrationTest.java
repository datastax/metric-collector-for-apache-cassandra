package com.datastax.mcac;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.mcac.insights.events.ClientConnectionInformation;
import com.datastax.mcac.insights.events.CompactionEndedInformation;
import com.datastax.mcac.insights.events.CompactionStartedInformation;
import com.datastax.mcac.insights.events.ExceptionInformation;
import com.datastax.mcac.insights.events.FlushInformation;
import com.datastax.mcac.insights.events.GCInformation;
import com.datastax.mcac.insights.events.LargePartitionInformation;
import com.datastax.mcac.utils.DockerHelper;
import com.datastax.mcac.utils.InsightsTestUtil;

@RunWith(Parameterized.class)
public class IntegrationTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();
    static DockerHelper docker;

    private String version;

    public IntegrationTest(String version)
    {
        this.version = version;
    }

    @Parameterized.Parameters
    public static Iterable<String[]> functions() {
        return Lists.newArrayList(
                new String[]{"2.2"},
                new String[]{"3.0"},
                new String[]{"3.11"});
    }

    @Before
    public void setup()
    {
        try
        {
            temporaryFolder.create();
            docker = new DockerHelper(temporaryFolder.getRoot(), Lists.newArrayList("-Dmcac.partition_limit_override_bytes=1"));
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }

        docker.startCassandra(version);
    }

    @After
    public void teardown()
    {
        try
        {
            docker.stopCassandra();
        }
        finally
        {
            temporaryFolder.delete();
        }
    }

    @Test
    public void testStartupMessage()
    {
        InsightsTestUtil.lookForEntryInLog(Paths.get(temporaryFolder.getRoot().getAbsolutePath(), "insights").toFile(),
                "insights_client_started", 30);
    }


    @Test
    public void testDriverMessage()
    {
        Cluster cluster = null;
        try
        {
            cluster = Cluster.builder()
                    .addContactPoint("127.0.0.1")
                    .withPoolingOptions(new PoolingOptions().setHeartbeatIntervalSeconds(1))
                    .build();
            Session session = cluster.connect();

            session.execute("CALL InsightsRpc.reportInsight('{\n" +
                   "  \"metadata\": {\n" +
                   "    \"name\": \"driver.startup\",\n" +
                   "    \"insightMappingId\": \"v1\",\n" +
                   "    \"insightType\": \"EVENT\",\n" +
                   "    \"timestamp\": 1542285611120,\n" +
                   "    \"tags\": {\n" +
                   "      \"language\": \"nodejs\"\n" +
                   "    }\n" +
                   "  },\n" +
                   "  \"data\": {}" +
                   "}')");


            session.execute("CREATE KEYSPACE foo with replication={'class': 'SimpleStrategy', 'replication_factor':3}");
            session.execute("CREATE TABLE foo.bar (key text PRIMARY KEY, value text)");

            String val = StringUtils.rightPad("1", 10000);
            for (int i = 0; i < 10000; i++)
            {
                session.execute("INSERT into foo.bar(key, value) VALUES (?, ?)", ""+i, val);
            }

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }
        finally
        {
            if (cluster != null) cluster.close();
        }

        File rootDir = Paths.get(temporaryFolder.getRoot().getAbsolutePath(), "insights").toFile();

        InsightsTestUtil.lookForEntryInLog(rootDir, "driver.startup", 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, ClientConnectionInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, ClientConnectionInformation.NAME_HEARTBEAT, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, LargePartitionInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, FlushInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, ExceptionInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, GCInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, CompactionStartedInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, CompactionEndedInformation.NAME, 30);

    }
}
