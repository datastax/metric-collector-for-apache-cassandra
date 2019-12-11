package com.datastax.mcac.insights.events;

import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.dht.Murmur3Partitioner;

public class LargePartitionInformation extends Insight
{
    public static final String NAME = "oss.insights.event.large_partition";

    public LargePartitionInformation(String keyspace, String table, DecoratedKey key, long size)
    {
        super(new InsightMetadata(
                        NAME,
                        Optional.of(System.currentTimeMillis()),
                        Optional.empty(),
                        Optional.of(InsightMetadata.InsightType.EVENT),
                        Optional.empty()),
                new Data(keyspace, table,
                        key.getToken() instanceof Murmur3Partitioner.LongToken ? (Long) key.getToken().getTokenValue() : Long.MIN_VALUE,
                        size));
    }

    static class Data
    {
        @JsonProperty("keyspace")
        final String keyspace;

        @JsonProperty("table")
        final String table;

        @JsonProperty("partition_token")
        final Long token;

        @JsonProperty("length_in_bytes")
        final Long bytes;

        @JsonProperty("threshold_in_bytes")
        final Long threshold;

        public Data(String keyspace, String table, Long token, Long bytes)
        {
            this.keyspace = keyspace;
            this.table = table;
            this.token = token;
            this.bytes = bytes;

            Long partitionWarnThreshold = DatabaseDescriptor.getCompactionLargePartitionWarningThreshold();
            this.threshold = partitionWarnThreshold == null || partitionWarnThreshold < 0 ? -1 : partitionWarnThreshold * 1024L * 1024L;
        }
    }
}
