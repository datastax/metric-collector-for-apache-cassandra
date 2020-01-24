package com.datastax.mcac.interceptors;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.ReadFailureException;
import com.datastax.mcac.BaseIntegrationTest;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;

@RunWith(Parameterized.class)
public class TombstoneIntegrationTest extends BaseIntegrationTest
{
    public TombstoneIntegrationTest(String version)
    {
        super(version);
    }

    @Override
    protected ArrayList<String> getStartupArgs()
    {
        return Lists.newArrayList(
                "-Dmcac.partition_limit_override_bytes=1",
                "-Dcassandra.config=file:///etc/cassandra/cassandra_low_tombstone_thresholds.yaml"
        );
    }

    @Test(timeout = 120000)
    public void test()
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
                    "CREATE TABLE foo.bar ("
                            + "key int, k2 int, value text, "
                            + "PRIMARY KEY(key, k2)) "
                            + "with compaction = {'class': 'LeveledCompactionStrategy'}");

            while (true)
            {
                try
                {
                    writeData(session);
                    docker.waitTillFinished(docker.runCommand(
                            "nodetool",
                            "flush"
                    ));
                    deleteDataUpToWarnThreshold(session);
                    docker.waitTillFinished(docker.runCommand(
                            "nodetool",
                            "flush"
                    ));
                    //induce warning
                    selectData(session);
                    deleteDataUpToFailureThreshold(session);
                    docker.waitTillFinished(docker.runCommand(
                            "nodetool",
                            "flush"
                    ));
                    try
                    {
                        //induce failure
                        selectData(session);
                    }
                    catch (ReadFailureException ignore)
                    {
                        //expected as we have crossed failure threshold
                    }

                    InsightsTestUtil.lookForEntryInLog(
                            rootDir,
                            "com.datastax.mcac.tombstone_warnings.foo.bar",
                            30
                    );
                    InsightsTestUtil.lookForEntryInLog(
                            rootDir,
                            "com.datastax.mcac.tombstone_failures.foo.bar",
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

    private void deleteDataUpToWarnThreshold(Session session)
    {
        for (int i = 0; i < 100; i++)
        {
            session.execute("DELETE from foo.bar where key=0 AND k2=" + i);
        }
    }

    private void deleteDataUpToFailureThreshold(Session session)
    {
        for (int i = 101; i < 10000; i++)
        {
            session.execute("DELETE from foo.bar where key=0 AND k2=" + i);
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
        for (int i = 0; i < 10000; i++)
        {
            session.execute(
                    "INSERT into foo.bar(key, k2, value) VALUES (?, ?, ?)",
                    0,
                    i,
                    "1"
            );
        }
    }
}
