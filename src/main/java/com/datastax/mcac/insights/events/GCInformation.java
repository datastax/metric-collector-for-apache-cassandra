package com.datastax.mcac.insights.events;

import java.lang.management.MemoryUsage;
import java.util.Map;

import com.google.common.collect.Maps;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class GCInformation extends Insight
{
    public static final String NAME = "oss.insights.event.gc_information";

    @JsonCreator
    public GCInformation(@JsonProperty("metadata") InsightMetadata metadata,
            @JsonProperty("data") Data data)
    {
        super(metadata, data);
    }

    public GCInformation(GCRun gcRun)
    {
        super(new InsightMetadata(NAME, System.currentTimeMillis()),
                new Data(gcRun.gcName,
                        gcRun.durationInMillis,
                        usedMemoryMap(gcRun.beforeMemoryUsage),
                        usedMemoryMap(gcRun.afterMemoryUsage),
                        gcRun.promotedBytes,
                        gcRun.youngGcCpuNanos,
                        gcRun.oldGcCpuNanos));
    }

    private static Map<String, Long> usedMemoryMap(Map<String, MemoryUsage> memoryUsageMap)
    {
        Map<String, Long> usedMap = Maps.newHashMap();
        for (Map.Entry<String, MemoryUsage> mu : memoryUsageMap.entrySet())
            usedMap.put(mu.getKey(), mu.getValue().getUsed());
        return usedMap;
    }

    public static class Data
    {

        @JsonProperty("gc_name")
        public final String gcName;

        @JsonProperty("duration")
        public final long duration;

        @JsonProperty("memory_usage_before")
        public final Map<String, Long> memoryUsageBefore;

        @JsonProperty("memory_usage_after")
        public final Map<String, Long> memoryUsageAfter;

        @JsonProperty("promoted_bytes")
        public final long promotedBytes;

        @JsonProperty("young_gc_cpu_nanos")
        public final long youngGcCpuNanos;

        @JsonProperty("old_gc_cpu_nanos")
        public final long oldGcCpuNanos;

        public Data(@JsonProperty("gc_name") final String gcName,
                @JsonProperty("duration") final long duration,
                @JsonProperty("memory_usage_before") final Map<String, Long> memoryUsageBefore,
                @JsonProperty("memory_usage_after") final Map<String, Long> memoryUsageAfter,
                @JsonProperty("promoted_bytes") final long promoted_bytes,
                @JsonProperty("young_gc_cpu_nanos") final long youngGcCpuNanos,
                @JsonProperty("old_gc_cpu_nanos") final long oldGcCpuNanos)
        {
            this.duration = duration;
            this.gcName = gcName;
            this.memoryUsageBefore = memoryUsageBefore;
            this.memoryUsageAfter = memoryUsageAfter;
            this.promotedBytes = promoted_bytes;
            this.youngGcCpuNanos = youngGcCpuNanos;
            this.oldGcCpuNanos = oldGcCpuNanos;
        }
    }

    public static final class GCRun
    {
        public final String gcName;
        public final long durationInMillis;
        public final Map<String, MemoryUsage> beforeMemoryUsage;
        public final Map<String, MemoryUsage> afterMemoryUsage;
        public final boolean isOldGenGC;

        public final long collectedBytes;
        public final long promotedBytes;

        public final long youngGcCpuNanos;
        public final long oldGcCpuNanos;

        public GCRun(String gcName,
                long durationInMillis,
                Map<String, MemoryUsage> beforeMemoryUsage,
                Map<String, MemoryUsage> afterMemoryUsage,
                long collectedBytes,
                long promotedBytes,
                long youngGcCpuNanos,
                long oldGcCpuNanos,
                boolean isOldGenGC)
        {
            this.gcName = gcName;
            this.durationInMillis = durationInMillis;
            this.beforeMemoryUsage = beforeMemoryUsage;
            this.afterMemoryUsage = afterMemoryUsage;
            this.collectedBytes = collectedBytes;
            this.promotedBytes = promotedBytes;
            this.youngGcCpuNanos = youngGcCpuNanos;
            this.oldGcCpuNanos = oldGcCpuNanos;
            this.isOldGenGC = isOldGenGC;
        }

        @Override
        public String toString()
        {
            return "GCRun{" +
                    "gcName='" + gcName + '\'' +
                    ", durationInMillis=" + durationInMillis +
                    ", beforeMemoryUsage=" + beforeMemoryUsage +
                    ", afterMemoryUsage=" + afterMemoryUsage +
                    ", promotedBytes=" + promotedBytes +
                    ", youngGcCpuNanos=" + youngGcCpuNanos +
                    ", oldGcCpuNanos=" + oldGcCpuNanos +
                    ", isOldGenGC=" + isOldGenGC +
                    '}';
        }
    }
}