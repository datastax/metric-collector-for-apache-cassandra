package com.datastax.mcac;

import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

@RunWith(Parameterized.class)
public class Cassandra2xTombstoneIntegrationTest extends BaseIntegrationTest
{
    public Cassandra2xTombstoneIntegrationTest(String version)
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

    @Parameterized.Parameters
    public static Iterable<String[]> functions()
    {
        return Collections.singletonList(
                new String[]{"2.2"}
        );
    }

    @Test(timeout = 120000)
    public void test()
    {
        File rootDir = Paths.get(
                getTempDir().getAbsolutePath(),
                "insights"
        ).toFile();

        while (true)
        {
            try
            {
                /*
                 * NOTE: In C* 2.2 it is more difficult to induce tombstone threshold conditions.
                 *
                 * Luckily for 2.2 if we dial down the thresholds we get these two errors that can
                 * be found in the logs:
                 * - ERROR 01:18:34 Scanned over 101 tombstones in system.schema_columns; ... query aborted (see
                 *   tombstone_failure_threshold);
                 * - WARN WARN  01:20:47 Read 22 live and 45 tombstone cells in system.schema_columns ... (see
                 *   tombstone_warn_threshold)
                 */
                InsightsTestUtil.lookForEntryInLog(
                        rootDir,
                        "com.datastax.mcac.tombstone_warnings.system.schema_columns",
                        30
                );
                InsightsTestUtil.lookForEntryInLog(
                        rootDir,
                        "com.datastax.mcac.tombstone_failures.system.schema_columns",
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
