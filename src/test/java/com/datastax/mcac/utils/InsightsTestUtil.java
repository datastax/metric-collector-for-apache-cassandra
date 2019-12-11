package com.datastax.mcac.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.FileFilterUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

public class InsightsTestUtil
{
    private static final Logger logger = LoggerFactory.getLogger(InsightsTestUtil.class);

    public static void lookForEntryInLog(File dataDir, String entry, int timeoutInSeconds)
    {
        assertTrue(
                dataDir.toString(),
                dataDir.exists()
        );

        File dataFile = getCurrentDataFile(dataDir);

        long start = System.nanoTime();

        try
        {
            String insightsContent = "";
            while (true)
            {
                if ((System.nanoTime() - start) > TimeUnit.SECONDS.toNanos(timeoutInSeconds))
                    throw new AssertionError("Timeout looking for " + entry + " in " + dataFile +
                            insightsContent.substring(0, Math.min(insightsContent.length(), 1024)));

                if (dataFile == null || !dataFile.exists())
                {
                    Uninterruptibles.sleepUninterruptibly(
                            100,
                            TimeUnit.MILLISECONDS
                    );
                    dataFile = getCurrentDataFile(dataDir);
                    continue;
                }

                try
                {
                    insightsContent = FileUtils.readFileToString(
                            dataFile,
                            "ascii"
                    );
                }
                catch (IOException e)
                {
                    continue;
                }

                int idx = insightsContent.indexOf(entry);
                if (idx > 0)
                {
                    logger.info("Found {}: {}", entry, insightsContent.substring(idx, idx + 256));
                    break;
                }
                else
                {
                    dataFile = getCurrentDataFile(dataDir);
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static String matchLineInLog(File dataDir, String pattern, int timeoutInSeconds)
    {
        assertTrue(
                dataDir.toString(),
                dataDir.exists()
        );

        File dataFile = getCurrentDataFile(dataDir);

        long start = System.nanoTime();

        try
        {
            {
                while (true)
                {
                    if ((System.nanoTime() - start) > TimeUnit.SECONDS.toNanos(timeoutInSeconds))
                        throw new AssertionError("Timeout matching " + pattern+ " in ");

                    if (dataFile == null || !dataFile.exists())
                    {
                        Uninterruptibles.sleepUninterruptibly(
                                100,
                                TimeUnit.MILLISECONDS
                        );
                        dataFile = getCurrentDataFile(dataDir);
                        continue;
                    }

                    LineIterator lineIterator = FileUtils.lineIterator(dataFile);
                    while (lineIterator.hasNext())
                    {
                        String line = lineIterator.nextLine();
                        if (line.contains(pattern))
                        {
                            return line;
                        }
                    }

                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static File getCurrentDataFile(File insightsDir)
    {
        List<File> dataFiles = getDataFiles(insightsDir);
        if (dataFiles.size() > 0)
        {
            return dataFiles.get(dataFiles.size() - 1);
        }
        else
        {
            return null;
        }
    }

    public static List<File> getDataFiles(File insightsDir)
    {
        if (insightsDir.exists() && insightsDir.isDirectory())
        {
            return FileUtils.listFiles(
                    insightsDir,
                    FileFilterUtils.prefixFileFilter("insights_"),
                    null).stream().sorted().collect(Collectors.toList());
        }
        else
        {
            return Lists.newArrayList();
        }
    }
}
