package com.datastax.mcac;

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
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
public class EventsIntegrationTest extends BaseIntegrationTest
{
    public EventsIntegrationTest(String version)
    {
        super(version);
    }

    @Test
    public void testEvents()
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

        File rootDir = Paths.get(getTempDir().getAbsolutePath(), "insights").toFile();

        InsightsTestUtil.lookForEntryInLog(rootDir, "driver.startup", 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, NodeConfiguration.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, SchemaInformation.NAME, 30);

        InsightsTestUtil.lookForEntryInLog(rootDir, NodeSystemInformation.NAME, 30);

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
