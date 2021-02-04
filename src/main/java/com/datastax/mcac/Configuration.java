package com.datastax.mcac;

import org.apache.cassandra.config.DatabaseDescriptor;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class Configuration
{
    public static final long MAX_METRIC_UPDATE_GAP_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    public static final long MAX_EVENT_INTERVAL = (int) TimeUnit.MINUTES.toSeconds(5);

    public String log_dir = System.getProperty("cassandra.logdir", System.getProperty("mcac.collectd.logdir", "/tmp"));

    public String data_dir = getDataDir();
    public String token_dir = "/tmp/";

    public Long data_dir_max_size_in_mb = 5000L;

    public Integer metric_sampling_interval_in_seconds = 30;

    public Integer upload_interval_in_seconds = (int)MAX_METRIC_UPDATE_GAP_IN_SECONDS;

    public Integer event_interval_in_seconds = (int)MAX_EVENT_INTERVAL;

    public String upload_url;

    String insights_token;

    public List<FilteringRule> filtering_rules = new ArrayList<>();

    public boolean insights_upload_enabled = false;

    public boolean write_to_disk_enabled = true;

    public boolean insights_streaming_enabled = false;

    /**
     * This serves the opposite purpose of metric_sampling_interval_in_seconds
     * which is intended for real-time reporting of metrics on a
     * high frequency.
     *
     * In the case of insights if we ship data every hour we want at *least* one
     * set of metrics every 5 minutes (to fill out the UI)
     *
     * Reporting more often would be wasteful (unless the customer lowers the upload
     * interval or starts streaming)
     */
    public long eventUpdateGapInSeconds()
    {
        if (upload_interval_in_seconds > MAX_METRIC_UPDATE_GAP_IN_SECONDS)
            return MAX_METRIC_UPDATE_GAP_IN_SECONDS;
        else
            return upload_interval_in_seconds;
    }

    public long metricUpdateGapInSeconds()
    {
        if (upload_interval_in_seconds > MAX_METRIC_UPDATE_GAP_IN_SECONDS)
            return MAX_METRIC_UPDATE_GAP_IN_SECONDS;
        else
            return upload_interval_in_seconds;
    }

    private static final String getDataDir()
    {
        try
        {
            if (DatabaseDescriptor.isDaemonInitialized())
            {
               Method method =  DatabaseDescriptor.class.getMethod("getCommitLogLocation");
               Object clDir = method.invoke(null);

               File location = clDir instanceof File ? (File) clDir : new File((String) clDir);
               return location.toPath().getParent().resolve("mcac_data").normalize().toFile().getAbsolutePath();
            }
        }
        catch (Throwable e)
        {
            //Use default below
        }

        return System.getProperty("cassandra.storagedir", "/tmp") + "/mcac_data";
    }
}
