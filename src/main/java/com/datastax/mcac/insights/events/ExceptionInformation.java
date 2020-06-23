package com.datastax.mcac.insights.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class ExceptionInformation extends Insight
{
    public static final String NAME = "oss.insights.event.exception";

    public ExceptionInformation(Throwable t)
    {
        super(new InsightMetadata(NAME),
                new Data(t));
    }

    public static class Data
    {
        @JsonProperty("exception_causes")
        public final List<InnerData> causes;

        Data(Throwable t)
        {
            causes = new ArrayList<>();

            while (t != null)
            {
                causes.add(new InnerData(t));
                t = t.getCause();
            }
        }
    }

    public static class InnerData
    {
        @JsonProperty("exception_class")
        public final String exceptionClass;

        @JsonProperty("stack_trace")
        public final List<StackTraceElement> stackTrace;

        InnerData(Throwable t)
        {
            this.exceptionClass = t.getClass().getCanonicalName();
            this.stackTrace = Lists.newArrayList(t.getStackTrace());
        }
    }
}