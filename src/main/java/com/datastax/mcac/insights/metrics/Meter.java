package com.datastax.mcac.insights.metrics;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.InsightMetadata;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public final class Meter extends Metric
{
    @JsonCreator
    public Meter(
            @JsonProperty("name") String name,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("tags") Map<String, String> tags,
            @JsonProperty("count") long count,
            @JsonProperty("rateStats") RateStats rateStats,
            @JsonProperty("rateUnit") String rateUnit
    )
    {
        super(
                name,
                timestamp,
                tags,
                InsightMetadata.InsightType.METER,
                new Data(count, rateStats, rateUnit)
        );
    }

    private static final class Data
    {
        @JsonProperty("count")
        public final long count;
        @JsonProperty("rateStats")
        public final RateStats rateStats;
        @JsonProperty("rateUnit")
        public final String rateUnit;

        @JsonCreator
        public Data(
                @JsonProperty("count")
                        long count,
                @JsonProperty("rateStats")
                        RateStats rateStats,
                @JsonProperty("rateUnit")
                        String rateUnit)
        {
            this.count = count;
            this.rateStats = rateStats;
            this.rateUnit = rateUnit;
        }

        @Override
        public String toString()
        {
            return "Data{"
                    + "count=" + count
                    + ", rateStats=" + rateStats
                    + ", rateUnit='" + rateUnit + '\''
                    + '}';
        }
    }
}
