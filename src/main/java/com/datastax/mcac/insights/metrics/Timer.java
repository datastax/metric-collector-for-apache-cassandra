package com.datastax.mcac.insights.metrics;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.InsightMetadata;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class Timer extends Metric
{
    @JsonCreator
    public Timer(
            @JsonProperty("name") String name,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("tags") Optional<Map<String, String>> tags,
            @JsonProperty("count") long count,
            @JsonProperty("samplingStats") SamplingStats samplingStats,
            @JsonProperty("rateStats") RateStats rateStats,
            @JsonProperty("rateUnit") String rateUnit,
            @JsonProperty("durationUnit") String durationUnit
    )
    {
        super(
                name,
                Optional.of(timestamp),
                tags,
                InsightMetadata.InsightType.TIMER,
                new Data(count, samplingStats, rateStats, rateUnit, durationUnit)
        );
    }

    private static final class Data
    {
        @JsonProperty("count")
        public final long count;
        @JsonProperty("samplingStats")
        public final SamplingStats samplingStats;
        @JsonProperty("rateStats")
        public final RateStats rateStats;
        @JsonProperty("rateUnit")
        public final String rateUnit;
        @JsonProperty("durationUnit")
        public final String durationUnit;

        @Override
        public String toString()
        {
            return "Data{"
                    + "count=" + count
                    + ", samplingStats=" + samplingStats
                    + ", rateStats=" + rateStats
                    + ", rateUnit='" + rateUnit + '\''
                    + ", durationUnit='" + durationUnit + '\''
                    + '}';
        }

        @JsonCreator
        public Data(
                @JsonProperty("count")
                        long count,
                @JsonProperty("samplingStats")
                        SamplingStats samplingStats,
                @JsonProperty("rateStats")
                        RateStats rateStats,
                @JsonProperty("rateUnit")
                        String rateUnit,
                @JsonProperty("durationUnit")
                        String durationUnit)
        {
            this.count = count;
            this.samplingStats = samplingStats;
            this.rateStats = rateStats;
            this.rateUnit = rateUnit;
            this.durationUnit = durationUnit;
        }
    }
}
