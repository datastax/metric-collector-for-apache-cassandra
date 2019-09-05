package com.datastax.mcac.insights;

import java.util.Objects;

import com.datastax.mcac.utils.JacksonUtil;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Instead of providing both `Object o` and `InsightsMetadata meta` to the associated <br>
 * `InsightsClient::report(*)` APIs, you can also just use this wrapper class to provide <br>
 * your data and metadata. <br>
 *
 * <p>The data in `Object insightData`, should be serializable with Jackson defaults, other than we <br>
 * do also configure Java time, and Java 8 Jackson serialization modules so types like `java.util.Optional`, <br>
 * or `java.time.Instant`. <br>
 */
public class Insight
{
    @JsonProperty("metadata")
    public final InsightMetadata metadata;
    @JsonProperty("data")
    public final Object insightData;

    @JsonCreator
    public Insight(
            @JsonProperty("metadata") InsightMetadata metadata,
            @JsonProperty("data") Object data
    )
    {
        Objects.requireNonNull(metadata, "metadata is required");
        this.metadata = metadata;
        /*
         * It's possible this can be null and we are just generating an event with a name/time, but no data fields
         */
        this.insightData = data;
    }

    public Insight withMetadata(InsightMetadata newMetadata)
    {
        return new Insight(newMetadata, this.insightData);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        Insight insight = (Insight) o;
        return Objects.equals(
                metadata,
                insight.metadata
        );
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(metadata);
    }

    @Override
    public String toString()
    {
        try
        {
            return "Insight{"
                    + "metadata=" + JacksonUtil.prettyPrint(metadata)
                    + ", data=" + JacksonUtil.prettyPrint(insightData)
                    + '}';
        }
        catch (Exception ex)
        {
            // if json has issues, still print what we can, never want to not return a string here
            return "Insight{"
                    + "metadata=" + metadata
                    + ", data=" + insightData
                    + '}';
        }
    }
}