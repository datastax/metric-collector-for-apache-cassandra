package com.datastax.mcac.insights.events;

import java.net.InetAddress;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class GossipChangeInformation extends Insight
{
    public static final String NAME = "oss.insights.event.gossip_change";

    public GossipChangeInformation(Data data)
    {
        super(new InsightMetadata(NAME), data);
    }

    public GossipChangeInformation(GossipEventType eventType, InetAddress ipAddress)
    {
        this(new Data(eventType, ipAddress, Gossiper.instance.getEndpointStateForEndpoint(ipAddress)));
    }

    public enum GossipEventType
    {
        JOINED,
        REMOVED,
        ALIVE,
        DEAD,
        RESTARTED
    }

    public static class Data
    {
        @JsonProperty("event_type")
        public final GossipEventType eventType;

        @JsonProperty("endpoint_state")
        public final Map<ApplicationState, VersionedValue> endpointState;

        @JsonProperty("endpoint_address")
        public final String ipAddress;

        @JsonCreator
        public Data(
                @JsonProperty("event_type") GossipEventType eventType,
                @JsonProperty("endpoint_address") String ipAddress,
                @JsonProperty("endpoint_state") Map<ApplicationState, VersionedValue> endpointState)
        {
            this.eventType = eventType;
            this.endpointState = endpointState;
            this.ipAddress = ipAddress;
        }

        public Data(GossipEventType eventType, InetAddress ipAddress, EndpointState state)
        {
            this.eventType = eventType;
            this.ipAddress = ipAddress.getHostAddress();
            this.endpointState = new EnumMap<>(ApplicationState.class);

            if (state != null)
            {
                for (Map.Entry<ApplicationState, VersionedValue> e : state.states())
                {
                    endpointState.put(e.getKey(), e.getValue());
                }
            }
        }
    }
}
