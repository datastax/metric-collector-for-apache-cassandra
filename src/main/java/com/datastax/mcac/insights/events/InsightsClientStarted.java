package com.datastax.mcac.insights.events;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.fasterxml.jackson.annotation.JsonCreator;

/*
 * Event published through the insights client to indicate
 * when the insights client was started.
 */
public class InsightsClientStarted extends Insight
{
    public static final String NAME = "dse.insights.event.insights_client_started";

    @JsonCreator
    public InsightsClientStarted()
    {
        super(
                new InsightMetadata(
                        NAME,
                        System.currentTimeMillis()
                ),
                null
        );
    }
}