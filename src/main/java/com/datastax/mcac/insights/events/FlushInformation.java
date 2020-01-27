package com.datastax.mcac.insights.events;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.cassandra.db.Memtable;
import org.apache.cassandra.io.sstable.format.SSTableReader;

public class FlushInformation extends Insight
{
    public static final String NAME = "oss.insights.event.flush";

    public FlushInformation(Memtable memtable, Collection<SSTableReader> sstables, long durationInMillis, boolean isTruncate)
    {
        super(new InsightMetadata(
                NAME,
                Optional.of(System.currentTimeMillis()),
                Optional.empty(),
                Optional.of(InsightMetadata.InsightType.EVENT),
                Optional.empty()
        ), new Data(
                memtable,
                sstables,
                durationInMillis,
                isTruncate));
    }

    public static class Data
    {
        @JsonProperty("keyspace")
        public final String keyspace;

        @JsonProperty("table")
        public final String table;

        @JsonProperty("is_truncate")
        public final boolean truncate;

        @JsonProperty("memtable_live_data_size")
        public final long memtableLiveDataSize;

        @JsonProperty("flush_duration_millis")
        public final long flushDurationMillis;

        @JsonProperty("sstables_flushed")
        public final int sstablesFlushed;

        @JsonProperty("sstable_disk_size")
        public final long sstableBytesOnDisk;

        @JsonProperty("partitions_flushed")
        public final long partitonsFlushed;

        public Data(Memtable memtable, Collection<SSTableReader> sstables, long durationInMillis, boolean isTruncate)
        {
            this.keyspace = memtable.cfs.keyspace.getName();
            this.table = memtable.cfs.name;
            this.truncate = isTruncate;
            this.memtableLiveDataSize = memtable.getLiveDataSize();
            this.flushDurationMillis = durationInMillis;

            long totalBytesOnDisk = 0;
            int numSSTables = 0;
            long totalPartitonsFlushed = 0;
            for (SSTableReader sstable : sstables)
            {
                if (sstable != null)
                {
                    totalPartitonsFlushed += sstable.estimatedKeys();
                    totalBytesOnDisk += sstable.bytesOnDisk();
                    ++numSSTables;
                }
            }

            this.partitonsFlushed = totalPartitonsFlushed;
            this.sstableBytesOnDisk = totalBytesOnDisk;
            this.sstablesFlushed = numSSTables;
        }
    }
}
