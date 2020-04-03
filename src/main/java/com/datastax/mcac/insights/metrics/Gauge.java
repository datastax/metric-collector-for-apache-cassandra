package com.datastax.mcac.insights.metrics;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.InsightMetadata;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class Gauge extends Metric
{
    @JsonCreator
    public Gauge(
            @JsonProperty("name") String name,
            @JsonProperty("timestamp") Optional<Long> timestamp,
            @JsonProperty("tags") Optional<Map<String, String>> tags,
            @JsonProperty("value") Number value
    )
    {
        super(name, timestamp, tags, InsightMetadata.InsightType.GAUGE, new Data(value));
    }

    public Gauge(
            String name,
            Long timestamp,
            Map<String, String> tags,
            Number value
    )
    {
        super(name, Optional.of(timestamp), Optional.of(tags), InsightMetadata.InsightType.GAUGE, new Data(value));
    }

    public Gauge(
            String name,
            Long timestamp,
            Number value
    )
    {
        super(name, Optional.of(timestamp), Optional.empty(), InsightMetadata.InsightType.GAUGE, new Data(value));
    }

    @JsonIgnore
    public Number getValue()
    {
        Number value = ((Data) data).value;
        return value;
    }

    private static final class Data
    {
        @JsonProperty("value")
        public final Number value;

        @JsonCreator
        public Data(@JsonProperty("value") Number value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return "Data{"
                    + "value=" + value
                    + '}';
        }
    }
}
