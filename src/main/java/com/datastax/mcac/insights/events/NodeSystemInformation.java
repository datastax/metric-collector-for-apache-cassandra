package com.datastax.mcac.insights.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.datastax.mcac.utils.AgentVersionSupplier;
import com.datastax.mcac.utils.JacksonUtil;
import com.google.common.base.Joiner;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import static org.apache.cassandra.cql3.QueryProcessor.executeInternal;




/**
 * Insight that holds all the meta-info we can get our hands on.
 *
 * This class is optimised for fast writes, not intended to be used
 * directly by DSE other than tests.
 */
public class NodeSystemInformation extends Insight
{
    private static final Logger logger = LoggerFactory.getLogger(NodeSystemInformation.class);

    public static final String NAME = "oss.insights.event.node_system_information";
    private static final String MAPPING_VERSION = "oss-node-config-v1";

    public static final String LOCAL = "local";
    public static final String PEERS = "peers";

    @JsonCreator
    public NodeSystemInformation(
            @JsonProperty("metadata") InsightMetadata metadata,
            @JsonProperty("data") Data data
    )
    {
        super(
                metadata,
                data
        );
    }

    public NodeSystemInformation(Data data)
    {
        super(new InsightMetadata(
                NAME,
                Optional.of(System.currentTimeMillis()),
                Optional.empty(),
                Optional.of(InsightMetadata.InsightType.EVENT),
                Optional.of(MAPPING_VERSION)
        ), data);
    }

    public NodeSystemInformation()
    {
        this(new Data());
    }

    @JsonIgnore
    public Data getData()
    {
        return (Data) this.data;
    }

    public static class Data
    {
        @JsonProperty("oss_agent_version")
        public final String ossAgentVersion;

        @JsonProperty("local_info")
        @JsonRawValue()
        public final String localInfo;

        @JsonProperty("peers_info")
        @JsonRawValue
        public final String peersInfo;

        @JsonCreator
        public Data(
                @JsonProperty("oss_agent_version") final String ossAgentVersion,
                @JsonProperty("local_info") final Object localInfo,
                @JsonProperty("peers_info") final Object peersInfo
        )
        {
            this.ossAgentVersion = ossAgentVersion;
            try
            {
                this.localInfo = JacksonUtil.writeValueAsString(localInfo);
            }
            catch (JacksonUtil.JacksonUtilException e)
            {
                throw new IllegalArgumentException("Error creating localInfo json", e);
            }

            try
            {
                this.peersInfo = JacksonUtil.writeValueAsString(peersInfo);
            }
            catch (JacksonUtil.JacksonUtilException e)
            {
                throw new IllegalArgumentException("Error creating peersInfo json", e);
            }
        }

        private Data()
        {
            this.ossAgentVersion = AgentVersionSupplier.getAgentVersion();
            String req = "SELECT JSON * from system." + LOCAL + " where key = '" + LOCAL + "'";
            this.localInfo =  executeInternal(req).one().getString("[json]");

            req = "SELECT JSON host_id from system." + PEERS;
            UntypedResultSet peersTableJSON =  executeInternal(req);
            List<String> peers = new ArrayList<>(peersTableJSON.size());
            for (UntypedResultSet.Row row : peersTableJSON)
            {
                peers.add(row.getString("[json]"));
            }
            this.peersInfo = "[" + Joiner.on(",").join(peers) + "]";
        }


        @JsonIgnore
        public List<Map<String, Object>> getPeersInfoList()
        {
            try
            {
                return JacksonUtil.readValue(peersInfo, List.class);
            }
            catch (JacksonUtil.JacksonUtilException e)
            {
                throw new RuntimeException(e);
            }
        }

        @JsonIgnore
        public Map<String, Object> getLocalInfoMap()
        {
            try
            {
                return JacksonUtil.convertJsonToMap(localInfo);
            }
            catch (JacksonUtil.JacksonUtilException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}

