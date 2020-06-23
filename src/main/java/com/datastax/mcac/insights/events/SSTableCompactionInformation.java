package com.datastax.mcac.insights.events;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class SSTableCompactionInformation
{
    @JsonProperty("filename")
    public final String filename;

    @JsonProperty("level")
    public final int level;

    @JsonProperty("total_rows")
    public final long totalRows;

    @JsonProperty("generation")
    public final int generation;

    @JsonProperty("version")
    public final String version;

    @JsonProperty("size_bytes")
    public final long sizeBytes;

    @JsonProperty("strategy")
    public final String strategy;

    public SSTableCompactionInformation(
            @JsonProperty("filename") final String filename,
            @JsonProperty("level")  final int level,
            @JsonProperty("total_rows") final long totalRows,
            @JsonProperty("generation") final int generation,
            @JsonProperty("version") final String version,
            @JsonProperty("size_bytes") final long sizeBytes,
            @JsonProperty("strategy") final String strategy
    )
    {
        this.filename = filename;
        this.level = level;
        this.totalRows = totalRows;
        this.generation = generation;
        this.version = version;
        this.sizeBytes = sizeBytes;
        this.strategy = strategy;
    }
}
