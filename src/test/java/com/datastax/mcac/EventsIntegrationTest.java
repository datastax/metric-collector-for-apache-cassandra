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

        //Test filtering
        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "jvm.fd.usage") == 0);
        // Check that we're getting some table metrics
        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "org.apache.cassandra.metrics.keyspace.write_latency.foo") > 0);
    }
}