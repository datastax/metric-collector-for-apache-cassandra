package com.datastax.mcac.insights.metrics;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.InsightMetadata;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class Histogram extends Metric
{
    @JsonCreator
    public Histogram(
            @JsonProperty("name") String name,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("tags") Map<String, String> tags,
            @JsonProperty("count") long count,
            @JsonProperty("samplingStats") SamplingStats samplingStats
    )
    {
        super(name, timestamp, tags, InsightMetadata.InsightType.HISTOGRAM, new Data(count, samplingStats));
    }

    private static final class Data
    {
        @JsonProperty("count")
        public final long count;

        @JsonProperty("samplingStats")
        public final SamplingStats samplingStats;

        @JsonCreator
        public Data(
                @JsonProperty("count") long count,
                @JsonProperty("samplingStats") SamplingStats samplingStats
        )
        {
            this.count = count;
            this.samplingStats = samplingStats;
        }

        @Override
        public String toString()
        {
            return "Data{"
                    + "count=" + count
                    + ", samplingStats=" + samplingStats
                    + '}';
        }
    }
}
