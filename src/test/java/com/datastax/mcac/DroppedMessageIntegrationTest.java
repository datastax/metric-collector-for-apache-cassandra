package com.datastax.mcac;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.mcac.insights.events.DroppedMessageInformation;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    public void test() throws Exception
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
                    Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(
                            rootDir,
                            DroppedMessageInformation.NAME
                    ) > 0);
                    break;
                }
                catch (AssertionError ignore)
                {
                    Uninterruptibles.sleepUninterruptibly(
                            100,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
        }
    }

    private void readWriteData(
            Session session,
            String val
    ) throws ExecutionException, InterruptedException
    {
        List<CompletableFuture> completableFutures = new ArrayList<>();
        CompletableFuture write = CompletableFuture.runAsync(
                () ->
                {
                    for (int i = 0; i < 10000; i++)
                    {
                        try
                        {
                            session.execute(
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
        completableFutures.add(write);

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
                                session.execute(
                                        "SELECT * from foo.bar where key='" + i + "'"
                                );
                            }
                            catch (Exception ignore)
                            {
                            }
                        }
                    }
                });

        CompletableFuture.allOf(read, write).get();
    }
}
