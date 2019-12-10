package com.datastax.mcac;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.mcac.insights.events.ClientConnectionInformation;
import com.datastax.mcac.utils.DockerHelper;
import com.datastax.mcac.utils.InsightsTestUtil;

public class IntegrationTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();
    static DockerHelper docker;

    @BeforeClass
    public static void setup()
    {
        try
        {
            temporaryFolder.create();
            docker = new DockerHelper(temporaryFolder.getRoot());
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }

        docker.startCassandra();
    }

    @AfterClass
    public static void teardown()
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

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }
        finally
        {
            if (cluster != null) cluster.close();
        }

        InsightsTestUtil.lookForEntryInLog(Paths.get(temporaryFolder.getRoot().getAbsolutePath(), "insights").toFile(),
                "driver.startup", 30);

        InsightsTestUtil.lookForEntryInLog(Paths.get(temporaryFolder.getRoot().getAbsolutePath(), "insights").toFile(),
                ClientConnectionInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(Paths.get(temporaryFolder.getRoot().getAbsolutePath(), "insights").toFile(),
                ClientConnectionInformation.NAME_HEARTBEAT, 30);

    }
}
