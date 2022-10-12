package com.datastax.mcac;

import com.datastax.mcac.insights.events.InsightsClientStarted;
import com.datastax.mcac.utils.DockerHelper;
import com.datastax.mcac.utils.InsightsTestUtil;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.Parameterized;

import org.junit.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;

public abstract class BaseIntegrationTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected final String version;
    protected static DockerHelper docker;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<String[]> functions()
    {
        String cassandraVersion = System.getProperty("cassandra.version");
        if (cassandraVersion != null && cassandraVersion.startsWith("4.1"))
        {
            String[] versions = new String[]{"4.1"};
            ArrayList<String[]> list = new ArrayList<>(1);
            list.add(versions);
            return list;
        }
        return Lists.newArrayList(
                new String[]{"4.0"},
                new String[]{"3.11"},
                new String[]{"3.0"},
                new String[]{"2.2"}
        );
    }

    protected BaseIntegrationTest(String version)
    {
        this.version = version;
    }

    @Before
    public void setup() throws InterruptedException, IOException
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

        //Copy resources to temp dir based on version
        for (URL url : getTestResources())
        {
            OutputStream os = new FileOutputStream(Paths.get(getTempDir().getPath(), Paths.get(url.getPath()).getFileName().toString()).toFile());
            Resources.copy(url, os);
            os.close();
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

    protected ArrayList<URL> getTestResources()
    {
        return Lists.newArrayList();
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

    File getInsightsDir()
    {
        return Paths.get(getTempDir().getAbsolutePath(), "insights").toFile();
    }

    void waitForInsightsClientStartupEvent() throws IOException
    {
        Assert.assertTrue(InsightsTestUtil.checkInsightLogFor(getInsightsDir(), InsightsClientStarted.NAME) > 0);
    }
}
