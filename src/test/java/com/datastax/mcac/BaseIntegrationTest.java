package com.datastax.mcac;

import com.datastax.mcac.utils.DockerHelper;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

public abstract class BaseIntegrationTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected final String version;
    protected static DockerHelper docker;

    @Parameterized.Parameters
    public static Iterable<String[]> functions()
    {
        return Lists.newArrayList(
                new String[]{"2.2"},
                new String[]{"3.0"},
                new String[]{"3.11"}
        );
    }

    protected BaseIntegrationTest(String version)
    {
        this.version = version;
    }

    @Before
    public void setup() throws InterruptedException
    {
        try
        {
            temporaryFolder.create();
            docker = new DockerHelper(
                    getTempDir(),
                    Lists.newArrayList(getStartupArgs())
            );
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }

        docker.startCassandra(version);
    }

    @After
    public void teardown()
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

    protected ArrayList<String> getStartupArgs()
    {
        return Lists.newArrayList(
                "-Dmcac.partition_limit_override_bytes=1"
        );
    }

    protected File getTempDir()
    {
        String os = System.getProperty("os.name");
        File tempDir = temporaryFolder.getRoot();
        if (os.equalsIgnoreCase("mac os x"))
        {
            tempDir = new File("/private", tempDir.getPath());
        }
        return tempDir;
    }

    protected void waitForInsightsClientStartupEvent()
    {
        InsightsTestUtil.lookForEntryInLog(
                Paths.get(getTempDir().getAbsolutePath(), "insights").toFile(),
                "insights_client_started", 30);
    }
}
