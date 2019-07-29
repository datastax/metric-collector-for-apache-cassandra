package com.datastax.mcac;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Configuration
{
    public static final long MAX_METRIC_UPDATE_GAP_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);


    String collectd_root;
    String log_dir = "/tmp/test/log";
    String data_dir = "/tmp/test/data";

    Long data_dir_max_size_in_mb = 5000L;
    Integer metric_sampling_interval_in_seconds = 30;
    Integer upload_interval_in_seconds = (int)MAX_METRIC_UPDATE_GAP_IN_SECONDS;
    String upload_url = "http://foo.com";

    String insights_token;

    List<FilteringRule> filtering_rules = new ArrayList<>();

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
    public long metricUpdateGapInSeconds()
    {
        if (upload_interval_in_seconds > MAX_METRIC_UPDATE_GAP_IN_SECONDS)
            return MAX_METRIC_UPDATE_GAP_IN_SECONDS;
        else
            return upload_interval_in_seconds;
    }

    public boolean isInsightsUploadEnabled()
    {
        return false;
    }

    public boolean isInsightsStreamingEnabled()
    {
        return false;
    }

    public boolean isWriteToDiskEnabled()
    {
        return true;
    }


}
