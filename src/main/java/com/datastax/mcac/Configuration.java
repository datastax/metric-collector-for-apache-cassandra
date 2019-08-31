package com.datastax.mcac;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Configuration
{
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

    public static final long MAX_METRIC_UPDATE_GAP_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);


    String collectd_root;
    String log_dir = "/tmp/test/log";
    String data_dir = "/tmp/test/data";
    String token_dir = "/tmp/";

    Long data_dir_max_size_in_mb = 5000L;
    Integer metric_sampling_interval_in_seconds = 30;
    Integer upload_interval_in_seconds = (int)MAX_METRIC_UPDATE_GAP_IN_SECONDS;
    String upload_url = "https://collector-riptano-insights-test.insights.dsexternal.org";
    //String upload_url = "https://collector-riptano-insights-stage.insights.dsexternal.org";


    String insights_token;

    List<FilteringRule> filtering_rules = new ArrayList<>();

    private boolean insightsUploadEnabled;
    private boolean writeToDiskEnabled;
    private boolean insightsStreamingEnabled;

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
        return insightsUploadEnabled;
    }

    public boolean isInsightsStreamingEnabled()
    {
        return insightsStreamingEnabled;
    }

    public boolean isWriteToDiskEnabled()
    {
        return writeToDiskEnabled;
    }


    public void setInsightsUploadEnabled(boolean insightsUploadEnabled) {
        this.insightsUploadEnabled = insightsUploadEnabled;
    }

    public void setWriteToDiskEnabled(boolean writeToDiskEnabled) {
        this.writeToDiskEnabled = writeToDiskEnabled;
    }

    public void setInsightsStreamingEnabled(boolean insightsStreamingEnabled) {
        this.insightsStreamingEnabled = insightsStreamingEnabled;
    }
}
