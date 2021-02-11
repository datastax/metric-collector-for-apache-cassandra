package com.datastax.mcac.insights.metrics;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class RateStats
{
    @JsonProperty("m1Rate")
    public final double m1Rate;
    @JsonProperty("m5Rate")
    public final double m5Rate;
    @JsonProperty("m15Rate")
    public final double m15Rate;
    @JsonProperty("meanRate")
    public final double meanRate;

    public RateStats(double m1Rate, double m5Rate, double m15Rate, double meanRate)
    {
        this.m1Rate = m1Rate;
        this.m5Rate = m5Rate;
        this.m15Rate = m15Rate;
        this.meanRate = meanRate;
    }

    @Override
    public String toString()
    {
        return "RateStats{"
                + "m1Rate=" + m1Rate
                + ", m5Rate=" + m5Rate
                + ", m15Rate=" + m15Rate
                + ", meanRate=" + meanRate
                + '}';
    }
}
