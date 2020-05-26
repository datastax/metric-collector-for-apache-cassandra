package com.datastax.mcac;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
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
import com.datastax.mcac.insights.events.NodeConfiguration;
import com.datastax.mcac.insights.events.NodeSystemInformation;
import com.datastax.mcac.insights.events.SchemaInformation;
import com.datastax.mcac.utils.InsightsTestUtil;

@RunWith(Parameterized.class)
public class EventsIntegrationTest extends BaseIntegrationTest
{
    public EventsIntegrationTest(String version)
    {
        super(version);
    }

    @Test
    public void testDriverMessage() throws IOException
    {
        waitForInsightsClientStartupEvent();

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
            session.execute("CREATE TABLE foo.bar (key text PRIMARY KEY, value text) with compaction = {'class': 'LeveledCompactionStrategy'}");

            String val = StringUtils.rightPad("1", 10000);
            for (int i = 0; i < 10000; i++)
            {
                session.execute(
                        "INSERT into foo.bar(key, value) VALUES (?, ?)",
                        "" + i,
                        val
                );
            }

            //docker.waitTillFinished(docker.runCommand("nodetool", "compact"));

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }
        finally
        {
            if (cluster != null) cluster.close();
        }

        File rootDir = getInsightsDir();

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "driver.startup") > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, NodeConfiguration.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, SchemaInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, NodeSystemInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, ClientConnectionInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, ClientConnectionInformation.NAME_HEARTBEAT) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, LargePartitionInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, FlushInformation.NAME) > 0);

        if (version != "4.0")
            Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, ExceptionInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, GCInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, CompactionStartedInformation.NAME) > 0);

        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, CompactionEndedInformation.NAME) > 0);

        //Test filtering
        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "jvm.fd.usage") == 0);
    }
}
