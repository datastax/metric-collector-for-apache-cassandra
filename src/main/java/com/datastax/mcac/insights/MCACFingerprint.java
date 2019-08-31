package com.datastax.mcac.insights;

/*
 *
 * @author Sebastián Estévez on 8/30/19.
 *
 */


import java.net.InetAddress;
import java.util.*;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class MCACFingerprint extends Insight
{
    public static final String NAME = "dse.insights.event.fingerprint";

    public MCACFingerprint(List<Fingerprint> fingerprintList)
    {
        super(new InsightMetadata(
                NAME,
                //TODO: if we upgrade c* dependency we could use ApproximateTime
                Optional.of(System.currentTimeMillis()),
                Optional.empty(),
                Optional.of(InsightMetadata.InsightType.EVENT),
                Optional.empty()
        ), new Data(fingerprintList));
    }

    public static class Data
    {
        @JsonProperty("fingerprint")
        public final List<Fingerprint> fingerprint;

        @JsonCreator
        public Data(@JsonProperty("fingerprint") List<Fingerprint> fingerprint)
        {
            this.fingerprint = fingerprint;
        }
    }

    public static class Fingerprint
    {

        @JsonProperty
        public final InetAddress peer;
        @JsonProperty
        public final String data_center;
        @JsonProperty
        public final UUID host_id;
        @JsonProperty
        public final InetAddress preferred_ip;
        @JsonProperty
        public final String rack;
        @JsonProperty
        public final String release_version;
        @JsonProperty
        public final String rpc_address;
        @JsonProperty
        public final UUID schema_version;
        @JsonProperty
        public final Set<String> tokens;
        @JsonProperty
        public final boolean reportingHost;


        @JsonCreator
        public Fingerprint(
                @JsonProperty InetAddress peer,
                @JsonProperty String data_center,
                @JsonProperty UUID host_id,
                @JsonProperty InetAddress preferred_ip,
                @JsonProperty String rack,
                @JsonProperty String release_version,
                @JsonProperty String rpc_address,
                @JsonProperty UUID schema_version,
                @JsonProperty Set<String> tokens,
                @JsonProperty boolean reportingHost
                ) {

            this.peer = peer;
            this.data_center = data_center;
            this.host_id = host_id;
            this.preferred_ip = preferred_ip;
            this.rack = rack;
            this.release_version = release_version;
            this.rpc_address = rpc_address;
            this.schema_version = schema_version;
            this.tokens = tokens;
            this.reportingHost = reportingHost;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Fingerprint that = (Fingerprint) o;
            return Objects.equals(peer, that.peer) &&
                    Objects.equals(data_center, that.data_center) &&
                    Objects.equals(host_id, that.host_id) &&
                    Objects.equals(preferred_ip, that.preferred_ip) &&
                    Objects.equals(rack, that.rack) &&
                    Objects.equals(release_version, that.release_version) &&
                    Objects.equals(rpc_address, that.rpc_address) &&
                    Objects.equals(schema_version, that.schema_version) &&
                    Objects.equals(reportingHost, that.reportingHost) &&
                    Objects.equals(tokens, that.tokens);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(peer,data_center,host_id,preferred_ip,rack,release_version,rpc_address,schema_version,tokens,reportingHost);
        }
    }
}
