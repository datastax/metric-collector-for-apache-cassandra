package com.datastax.mcac.interceptors;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.UUID;

public final class ClientConnectionCacheEntry
{
    public final UUID sessionId;
    public final long lastInsightsend;
    public final Map<String, String> clientOptions;

    private ClientConnectionCacheEntry(
            UUID sessionId,
            long lastHeartBeatTs,
            Map<String, String> clientOptions
    )
    {
        this.sessionId = sessionId;
        this.lastInsightsend = lastHeartBeatTs;
        this.clientOptions = clientOptions;
    }

    private ClientConnectionCacheEntry(Builder builder)
    {
        sessionId = builder.sessionId;
        lastInsightsend = builder.lastHeartBeatTs;
        clientOptions = builder.clientOptions;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder newBuilder(ClientConnectionCacheEntry copy)
    {
        Builder builder = new Builder();
        builder.sessionId = copy.sessionId;
        builder.lastHeartBeatTs = copy.lastInsightsend;
        builder.clientOptions = copy.clientOptions;
        return builder;
    }

    public static final class Builder
    {
        private UUID sessionId;
        private long lastHeartBeatTs;
        private Map<String, String> clientOptions;

        private Builder()
        {
        }

        public Builder sessionId(UUID val)
        {
            sessionId = val;
            return this;
        }

        public Builder lastHeartBeatSend(long val)
        {
            lastHeartBeatTs = val;
            return this;
        }

        public Builder clientOptions(Map<String, String> val)
        {
            clientOptions = ImmutableMap.copyOf(val);
            return this;
        }

        public ClientConnectionCacheEntry build()
        {
            return new ClientConnectionCacheEntry(this);
        }
    }
}
