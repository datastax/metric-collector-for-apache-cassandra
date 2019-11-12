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
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
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

}
