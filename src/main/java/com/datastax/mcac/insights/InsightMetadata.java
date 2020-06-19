package com.datastax.mcac.insights;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class InsightMetadata
{
    public enum InsightType
    {
        EVENT,
        GAUGE,
        COUNTER,
        HISTOGRAM,
        TIMER,
        METER,
        LOG;
    }


    @JsonProperty("name")
    public final String name;
    @JsonProperty("timestamp")
    public final Optional<Long> timestamp;
    @JsonProperty("tags")
    public final Map<String, String> tags;
    @JsonProperty("insightType")
    public final Optional<InsightType> insightType;
    @JsonProperty("insightMappingId")
    public Optional<String> insightMappingId;

    /**
     * InsightsMetadata base constructor which all other constructors map to.
     *
     * @param name        - name of the insight you are providing that should be unique within <br>
     *                    your application's namespace(configured at the `InsightsClient` level <br>
     * @param timestamp   - Optionally providing timestamp in long form, if not provided the `timestamp` will be <br>
     *                    set to `now` <br>
     * @param tags        - Optionally tags can be provided, these often help reduce the burden on lots of unique names,
     *                    such as cpu_steal -> with a tag of cpuNum-> 0/1/2/etc vs. cpu0_steal, cpu1_steal, etc <br>
     * @param insightType - Optionally provide the type of insight, if not specified this will be set to EVENT <br>
     *
     * @param insightMappingId - Optionally provide a unique mapping identifier associated with this insight.
     *                           This identifier will be used to lookup any mapping logic to be done before
     *                           the event is stored. Example: collectd-v1 <br>
     */
    @JsonCreator
    public InsightMetadata(
            @JsonProperty("name")
                    String name,
            @JsonProperty("timestamp")
                    Optional<Long> timestamp,
            @JsonProperty("tags")
                    Optional<Map<String, String>> tags,
            @JsonProperty("insightType")
                    Optional<InsightType> insightType,
            @JsonProperty("insightMappingId")
                    Optional<String> insightMappingId
    )
    {
        /*
         * Technical Note:  JsonCreator is specified using Optionals for this constructor as we do not want null <br>
         * fields serialized at all if they are not present.  Otherwise they would be serialized <br>
         * with "field" : null <br>
         *
         * There is also only one constructor with Json Annotations as there should only be one that Jackson <br>
         * uses that is the superset of fields.  Otherwise Jackson will fail to find the right constructor <br>
         */

        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is required");

        this.name = name;
        this.timestamp = Optional.of(timestamp.filter(ts -> ts > 0).orElse(Instant.now().toEpochMilli()));
        this.tags = tags.orElseGet(HashMap::new); //kept mutable since we want to add some default tags
        this.insightType = Optional.of(insightType.orElse(InsightType.EVENT));
        this.insightMappingId = insightMappingId;
    }

    /**
     * InsightsMetadata constructor.
     *
     * @param name      - name of the insight you are providing that should be unique within <br>
     *                  your application's namespace(configured at the `InsightsClient` level <br>
     * @param timestamp - timestamp associated with the event. <br>
     * @param tags      - a set of name/value pairs to more uniquely identify an event without creating many <br>
     *                  unique event names.  Especially useful for metrics use cases <br>
     */
    public InsightMetadata(
            String name,
            Long timestamp,
            Map<String, String> tags
    )
    {
        this(name, Optional.ofNullable(timestamp), Optional.ofNullable(tags), Optional.empty(), Optional.empty());
    }

    /**
     * InsightsMetadata constructor.
     *
     * @param name - name of the event.  This should duplicate any namespace provided <br>
     *             when configuring the InsightsClient <br>
     * @param tags - a set of name/value pairs to more uniquely identify an event without creating many <br>
     *             unique event names.  Especially useful for metrics use cases <br>
     */
    public InsightMetadata(
            String name,
            Map<String, String> tags
    )
    {
        this(name, Optional.empty(), Optional.ofNullable(tags), Optional.empty(), Optional.empty());
    }

    /**
     * InsightsMetadata constructor.
     *
     * @param name      - name of the insight you are providing that should be unique within <br>
     *                  your application's namespace(configured at the `InsightsClient` level <br>
     * @param timestamp - timestamp associated with the event. <br>
     */
    public InsightMetadata(
            String name,
            Long timestamp
    )
    {
        this(name, Optional.ofNullable(timestamp), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * InsightsMetadata constructor.
     *
     * @param name - name of the insight you are providing that should be unique within <br>
     *             your application's namespace(configured at the `InsightsClient` level <br>
     */
    public InsightMetadata(String name)
    {
        this(name, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public InsightMetadata withTags(Map<String, String> newTags)
    {
        return new InsightMetadata(this.name, this.timestamp, Optional.of(newTags), insightType, insightMappingId);
    }

    public InsightMetadata withName(String newName)
    {
        return new InsightMetadata(newName, this.timestamp, Optional.of(tags), insightType, insightMappingId);
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
        InsightMetadata that = (InsightMetadata) o;
        return Objects.equals(
                name,
                that.name
        )
                && Objects.equals(
                timestamp,
                that.timestamp
        )
                && Objects.equals(
                tags,
                that.tags
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
                name,
                timestamp,
                tags
        );
    }

    @Override
    public String toString()
    {
        return "InsightMetadata{"
                + "name='" + name + '\''
                + ", timestamp=" + timestamp
                + ", tags=" + tags
                + ", insightType=" + insightType
                + ", insightMappingId=" + insightMappingId
                + '}';
    }
}
