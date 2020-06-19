package com.datastax.mcac.insights.events;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public class DroppedMessageInformation extends Insight
{
    public static final String NAME = "oss.insights.event.dropped_messages";

    public DroppedMessageInformation(
            String groupName,
            int interval,
            int droppedInternal,
            int droppedCrossNode,
            Long internalDroppedLatencyMs,
            Long crossNodeDroppedLatencyMs
    )
    {
        super(
                new InsightMetadata(
                        NAME,
                        Optional.of(System.currentTimeMillis()),
                        Optional.empty(),
                        Optional.of(InsightMetadata.InsightType.EVENT),
                        Optional.empty()
                ),
                new Data(
                        groupName,
                        interval,
                        droppedInternal,
                        droppedCrossNode,
                        internalDroppedLatencyMs,
                        crossNodeDroppedLatencyMs
                )
        );
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
        public final Long internalLatencyMs;

        @JsonProperty("crossnode_latency_ms")
        public final Long crossNodeLatencyMs;

        public Data(
                String groupName,
                int interval,
                int droppedInternal,
                int droppedCrossNode,
                Long internalDroppedLatencyMs,
                Long crossNodeDroppedLatencyMs
        )
        {
            this.group = groupName;
            this.reportingIntervalSeconds = interval;
            this.internalDropped = droppedInternal;
            this.crossNodeDropped = droppedCrossNode;
            this.internalLatencyMs = internalDroppedLatencyMs;
            this.crossNodeLatencyMs = crossNodeDroppedLatencyMs;
        }
    }
}