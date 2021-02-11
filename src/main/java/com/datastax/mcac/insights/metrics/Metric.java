package com.datastax.mcac.insights.metrics;

import java.util.Map;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;

public class Metric extends Insight
{
    public enum MetricType
    {
        COUNTER,
        GAUGE,
        HISTOGRAM,
        METER,
        TIMER
    }

    public Metric(
            String name,
            Long timestamp,
            Map<String, String> tags,
            InsightMetadata.InsightType insightType,
            Object metricData
    )
    {
        super(new InsightMetadata(name, timestamp, tags, insightType, null), metricData);
    }
}
