package com.datastax.mcac.insights.events;

import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.cassandra.net.MessagingService;

public class DroppedMessageInformation extends Insight
{
    public static final String NAME = "oss.insights.event.dropped_messages";

    public DroppedMessageInformation(MessagingService.Verb verb, int interval, int droppedInternal, int droppedCrossNode, long internalDroppedLatencyMs, long crossNodeDroppedLatencyMs)
    {
        super(new InsightMetadata(
                NAME,
                Optional.of(System.currentTimeMillis()),
                Optional.empty(),
                Optional.of(InsightMetadata.InsightType.EVENT),
                Optional.empty()
        ), new Data(verb, interval, droppedInternal, droppedCrossNode, internalDroppedLatencyMs, crossNodeDroppedLatencyMs));
    }

    public static class Data
    {
        @JsonProperty("group_name")
        public final String group;

        @JsonProperty("reporting_interval_seconds")
        public final int reportingIntervalSeconds;

        @JsonProperty("num_internal")
        public final int internalDropped;

        @JsonProperty("num_cross_node")
        public final int crossNodeDropped;

        @JsonProperty("internal_latency_ms")
        public final long internalLatencyMs;

        @JsonProperty("crossnode_latency_ms")
        public final long crossNodeLatencyMs;

        public Data(MessagingService.Verb verb, int interval, int droppedInternal, int droppedCrossNode, long internalDroppedLatencyMs, long crossNodeDroppedLatencyMs)
        {
            this.group = verb.name();
            this.reportingIntervalSeconds = interval;
            this.internalDropped = droppedInternal;
            this.crossNodeDropped = droppedCrossNode;
            this.internalLatencyMs = internalDroppedLatencyMs;
            this.crossNodeLatencyMs = crossNodeDroppedLatencyMs;
        }

    }
}