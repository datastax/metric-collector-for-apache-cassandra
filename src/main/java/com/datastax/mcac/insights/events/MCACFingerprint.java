package com.datastax.mcac.insights.events;

/*
 *
 * @author Sebastián Estévez on 8/30/19.
 *
 */


import java.net.InetAddress;
import java.util.*;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class MCACFingerprint extends Insight
{
    public static final String NAME = "dse.insights.event.ossfingerprint";

    public MCACFingerprint(List<OSSFingerprint> fingerprintList)
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
        @JsonProperty("ossfingerprint")
        public final List<OSSFingerprint> fingerprint;

        @JsonCreator
        public Data(@JsonProperty("ossfingerprint") List<OSSFingerprint> fingerprint)
        {
            this.fingerprint = fingerprint;
        }
    }

    public static class OSSFingerprint
    {
        @JsonProperty
        public final UUID host_id;

        @JsonCreator
        public OSSFingerprint(
                @JsonProperty UUID host_id
                ) {

            this.host_id = host_id;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OSSFingerprint that = (OSSFingerprint) o;
            return
                    Objects.equals(host_id, that.host_id);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(host_id);
        }
    }
}
