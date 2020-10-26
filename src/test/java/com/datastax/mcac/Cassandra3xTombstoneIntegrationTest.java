package com.datastax.mcac;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.ReadFailureException;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;

@RunWith(Parameterized.class)
public class Cassandra3xTombstoneIntegrationTest extends BaseIntegrationTest
{
    public Cassandra3xTombstoneIntegrationTest(String version)
    {
        super(version);
    }

    @Override
    protected ArrayList<String> getStartupArgs()
    {
        return Lists.newArrayList(
                "-Dmcac.partition_limit_override_bytes=1",
                "-Dcassandra.config=file:///var/lib/cassandra/cassandra_low_tombstone_thresholds.yaml"
        );
    }

    @Override
    protected ArrayList<URL> getTestResources()
    {
        return Lists.newArrayList(
                Resources.getResource(version + "/cassandra_low_tombstone_thresholds.yaml")
        );
    }

    @Parameterized.Parameters
    public static Iterable<String[]> functions()
    {
        return Lists.newArrayList(new String[]{"3.0"}, new String[]{"3.11"}, new String[]{"4.0"});
    }

    @Test(timeout = 120000)
    public void test() throws IOException
    {
        waitForInsightsClientStartupEvent();

        File rootDir = Paths.get(getTempDir().getAbsolutePath(), "insights").toFile();

        try (Cluster cluster = Cluster.builder()
                .addContactPoint("127.0.0.1")
                .withPoolingOptions(new PoolingOptions().setHeartbeatIntervalSeconds(1))
                .build())
        {
            Session session = cluster.connect();
            session.execute("CREATE KEYSPACE foo with "
                    + "replication={'class': 'SimpleStrategy', 'replication_factor':3} "
                    + "and durable_writes=true");
            session.execute(
                    "CREATE TABLE foo.bar ("
                            + "key int, k2 int, k3 int, value text, "
                            + "PRIMARY KEY((key), k2, k3)) "
                            + "with compaction = {'class': 'LeveledCompactionStrategy'}");

            while (true)
            {
                try
                {
                    writeData(session);
                    docker.waitTillFinished(docker.runCommand("nodetool", "flush"));
                    deleteDataUpToWarnThreshold(session);
                    docker.waitTillFinished(docker.runCommand("nodetool", "flush"));
                    //induce warning
                    selectData(session);
                    deleteDataUpToFailureThreshold(session);
                    docker.waitTillFinished(docker.runCommand("nodetool", "flush"));

                    try
                    {
                        //induce failure
                        selectData(session);
                    }
                    catch (ReadFailureException ignore)
                    {
                        //expected as we have crossed failure threshold
                    }

                    Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "com.datastax.mcac.tombstone_warnings.foo.bar") > 0);
                    Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(rootDir, "com.datastax.mcac.tombstone_failures.foo.bar") > 0);

                    break;
                }
                catch (AssertionError | EOFException ignore)
                {
                }
            }
        }
    }

    private void deleteDataUpToWarnThreshold(Session session)
    {
        int rangeVal = 0;
        for (int i = 1; i <= 200; i++)
        {
            if (i % 2 == 0)
            {
                session.execute("DELETE from foo.bar where key=0 AND k2=" + rangeVal++);
            }
        }
    }

    private void deleteDataUpToFailureThreshold(Session session)
    {
        int rangeVal = 0;
        for (int i = 201; i <= 10000; i++)
        {
            if (i % 2 == 0)
            {
                /*
                 * In order to induce in 3.0, need range tombstones vs. row tombstones
                 * which get masked until 3.11.2
                 */
                session.execute("DELETE from foo.bar where key=0 AND k2=" + rangeVal++);
            }
        }
    }

    private void selectData(Session session)
    {
        session.execute("SELECT key from foo.bar LIMIT 5");
    }

    private void writeData(
            Session session
    )
    {
        int rangeVal = 0;
        for (int i = 1; i <= 1000; i++)
        {
            try
            {
                session.execute(
                        "INSERT into foo.bar(key, k2, k3, value) VALUES (?, ?, ?, ?)", 0, i % 2 == 0 ? rangeVal++ : rangeVal, i, "1"
                );
            }
            catch (Throwable t)
            {
                //Timeout ok
            }
        }
    }
}
