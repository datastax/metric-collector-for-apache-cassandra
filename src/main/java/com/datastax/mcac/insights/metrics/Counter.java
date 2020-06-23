package com.datastax.mcac.insights.metrics;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.InsightMetadata;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class Counter extends Metric
{
    @JsonCreator
    public Counter(
            @JsonProperty("name") String name,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("tags") Map<String, String> tags,
            @JsonProperty("count") long count
    )
    {
        super(name, timestamp, tags, InsightMetadata.InsightType.COUNTER, new Data(count));
    }

    private static final class Data
    {

        @JsonProperty("count")
        public final long count;

        @JsonCreator
        public Data(@JsonProperty("count") long count)
        {
            this.count = count;
        }

        @Override
        public String toString()
        {
            return "Data{"
                    + "count=" + count
                    + '}';
        }
    }
}
