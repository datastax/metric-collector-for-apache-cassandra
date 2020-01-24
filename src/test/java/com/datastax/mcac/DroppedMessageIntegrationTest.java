package com.datastax.mcac;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.mcac.insights.events.DroppedMessageInformation;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@RunWith(Parameterized.class)
public class DroppedMessageIntegrationTest extends BaseIntegrationTest
{
    public DroppedMessageIntegrationTest(String version)
    {
        super(version);
    }

    @Override
    protected ArrayList<String> getStartupArgs()
    {
        return Lists.newArrayList(
                "-Dmcac.partition_limit_override_bytes=1",
                "-Dcassandra.config=file:///etc/cassandra/cassandra_low_timeouts.yaml"
        );
    }

    @Test(timeout = 120000)
    public void testDriverMessage()
    {
        waitForInsightsClientStartupEvent();

        File rootDir = Paths.get(
                getTempDir().getAbsolutePath(),
                "insights"
        ).toFile();

        try (Cluster cluster = Cluster.builder()
                .addContactPoint("127.0.0.1")
                .withPoolingOptions(new PoolingOptions().setHeartbeatIntervalSeconds(1))
                .build())
        {
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
            session.execute(
                    "CREATE TABLE foo.bar (key text PRIMARY KEY, value text) with compaction = {'class': "
                            + "'LeveledCompactionStrategy'}");

            String val = StringUtils.rightPad(
                    "1",
                    10000
            );

            while (true)
            {
                readWriteData(
                        session,
                        val
                );

                try
                {
                    InsightsTestUtil.lookForEntryInLog(
                            rootDir,
                            DroppedMessageInformation.NAME,
                            30
                    );
                    break;
                }
                catch (AssertionError ignore)
                {
                }
            }
        }
    }

    private void readWriteData(
            Session session,
            String val
    )
    {
        CompletableFuture write = CompletableFuture.runAsync(
                () ->
                {
                    for (int i = 0; i < 10000; i++)
                    {
                        try
                        {
                            session.executeAsync(
                                    "INSERT into foo.bar(key, value) VALUES (?, ?)",
                                    "" + i,
                                    val
                            );
                        }
                        catch (Exception ignore)
                        {
                        }
                    }
                });

        CompletableFuture read = CompletableFuture.runAsync(
                () ->
                {
                    /*
                     * 3.11 won't allow a write timeout < 10ms, so induce with READ timeout
                     */
                    if (version.equals("3.11"))
                    {
                        for (int i = 0; i < 10000; i++)
                        {
                            try
                            {
                                session.executeAsync(
                                        "SELECT * from foo.bar where key='" + i + "'"
                                ).getUninterruptibly();
                            }
                            catch (Exception ignore)
                            {
                            }
                        }
                    }
                });

        CompletableFuture.allOf(
                write,
                read
        );
    }

}
