package com.datastax.mcac.insights.metrics;

import org.codehaus.jackson.annotate.JsonProperty;

public class SamplingStats
{
    @JsonProperty("min")
    public final long min;
    @JsonProperty("max")
    public final long max;
    @JsonProperty("mean")
    public final double mean;
    @JsonProperty("median")
    public final double median;
    @JsonProperty("p75")
    public final double p75;
    @JsonProperty("p95")
    public final double p95;
    @JsonProperty("p98")
    public final double p98;
    @JsonProperty("p99")
    public final double p99;
    @JsonProperty("p999")
    public final double p999;
    @JsonProperty("std_dev")
    public final double stdDev;

    public SamplingStats(
            long min,
            long max,
            double mean,
            double median,
            double p75,
            double p95,
            double p98,
            double p99,
            double p999,
            double stdDev)
    {
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
        this.p75 = p75;
        this.p95 = p95;
        this.p98 = p98;
        this.p99 = p99;
        this.p999 = p999;
        this.stdDev = stdDev;
    }

    @Override
    public String toString()
    {
        return "SamplingStats{"
                + "min=" + min
                + ", max=" + max
                + ", mean=" + mean
                + ", median=" + median
                + ", p75=" + p75
                + ", p95=" + p95
                + ", p98=" + p98
                + ", p99=" + p99
                + ", p999=" + p999
                + ", stdDev=" + stdDev
                + '}';
    }
}
