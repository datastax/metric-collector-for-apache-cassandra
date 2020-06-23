package com.datastax.mcac.insights.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CompactionStartedInformation extends Insight
{
    public static final String NAME = "oss.insights.event.compaction_started";

    @JsonCreator
    public CompactionStartedInformation(
            final UUID id,
            final String keyspace,
            final String table,
            final OperationType type,
            final long totalBytes,
            final boolean isStopRequested,
            final long totalSSTableSizeBytes,
            final List<SSTableCompactionInformation> sstables
    )
    {
        super(new InsightMetadata(
                        NAME,
                        System.currentTimeMillis(),
                        null,
                        InsightMetadata.InsightType.EVENT,
                        "version1"
                ),
                new CompactionStartedInformation.DataVersion1(
                        id,
                        keyspace,
                        table,
                        type,
                        totalBytes,
                        isStopRequested,
                        totalSSTableSizeBytes,
                        sstables
                )
        );
    }

    public static class DataVersion1
    {
        @JsonProperty("id")
        public final UUID id;

        @JsonProperty("keyspace")
        public final String keyspace;

        @JsonProperty("table")
        public final String table;

        @JsonProperty("type")
        public final OperationType type;

        @JsonProperty("total_bytes")
        public final long totalBytes;

        @JsonProperty("is_stop_requested")
        public final boolean isStopRequested;

        @JsonProperty("total_sstable_size_bytes")
        public final long totalSSTableSizeBytes;

        @JsonProperty("sstables")
        public final List<SSTableCompactionInformation> sstables;

        public DataVersion1(
                @JsonProperty("id") final UUID id,
                @JsonProperty("keyspace") final String keyspace,
                @JsonProperty("table") final String table,
                @JsonProperty("type") final OperationType type,
                @JsonProperty("total_bytes") final long totalBytes,
                @JsonProperty("is_stop_requested") final boolean isStopRequested,
                @JsonProperty("total_sstable_size_bytes") final long totalSSTableSizeBytes,
                @JsonProperty("sstables") final List<SSTableCompactionInformation> sstables
        )
        {
            this.id = id;
            this.keyspace = keyspace;
            this.table = table;
            this.type = type;
            this.totalBytes = totalBytes;
            this.isStopRequested = isStopRequested;
            this.totalSSTableSizeBytes = totalSSTableSizeBytes;
            this.sstables = sstables;
        }
    }
}
