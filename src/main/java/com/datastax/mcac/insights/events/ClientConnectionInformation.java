package com.datastax.mcac.insights.events;

import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.apache.cassandra.service.ClientState;
import org.codehaus.jackson.annotate.JsonProperty;

public class ClientConnectionInformation extends Insight
{
    public static final String CQL_VERSION = "CQL_VERSION";
    public static final String COMPRESSION = "COMPRESSION";
    public static final String PROTOCOL_VERSIONS = "PROTOCOL_VERSIONS";
    public static final String CLIENT_ID = "CLIENT_ID";
    public static final String APPLICATION_NAME = "APPLICATION_NAME";
    public static final String APPLICATION_VERSION = "APPLICATION_VERSION";
    public static final String DRIVER_NAME = "DRIVER_NAME";
    public static final String DRIVER_VERSION = "DRIVER_VERSION";
    public static final String PAGE_UNIT = "PAGE_UNIT";
    public static final String SERVER_VERSION = "SERVER_VERSION";
    public static final String PRODUCT_TYPE = "PRODUCT_TYPE";

    public static final String NAME = "oss.insights.event.client_connected";
    public static final String NAME_HEARTBEAT = "oss.insights.event.client_heartbeat";

    public ClientConnectionInformation(ClientState clientState, Map<String, String> options, boolean isHeartbeat)
    {
        super(new InsightMetadata(
                        isHeartbeat ? NAME_HEARTBEAT : NAME,
                        Optional.of(System.currentTimeMillis()),
                        Optional.empty(),
                        Optional.of(InsightMetadata.InsightType.EVENT),
                        Optional.empty()),
                new Data(clientState, options));
    }

    static class Data
    {
        @JsonProperty("compression")
        public final String compression;

        @JsonProperty("client_id")
        public final String clientId;

        @JsonProperty("application_name")
        public final String applicationName;

        @JsonProperty("application_version")
        public final String applicationVersion;

        @JsonProperty("driver_name")
        public final String driverName;

        @JsonProperty("driver_version")
        public final String driverVersion;

        @JsonProperty("keyspace")
        public final String keyspace;

        @JsonProperty("remote_ip")
        public final String remoteIp;

        Data(ClientState clientState, Map<String, String> options)
        {
            this.compression = options.get(COMPRESSION);
            this.clientId = options.get(CLIENT_ID);
            this.applicationName = options.get(APPLICATION_NAME);
            this.applicationVersion = options.get(APPLICATION_VERSION);
            this.driverName = options.get(DRIVER_NAME);
            this.driverVersion = options.get(DRIVER_VERSION);

            this.keyspace = clientState.getRawKeyspace();
            this.remoteIp = clientState.getRemoteAddress() == null ? null : clientState.getRemoteAddress().getAddress().toString();
        }
    }
}
