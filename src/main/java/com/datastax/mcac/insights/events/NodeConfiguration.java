package com.datastax.mcac.insights.events;

/*
 *
 * @author Sebastián Estévez on 9/3/19.
 *
 */

/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

import java.io.IOError;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;


import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.InsightMetadata;
import com.datastax.mcac.utils.JacksonUtil;
import com.datastax.mcac.utils.ShellUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.apache.cassandra.config.Config;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonRawValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.FBUtilities;


/**
 * Represents static configuration like jvm / yaml
 *
 * intended to be sent on startup
 */
public class NodeConfiguration extends Insight
{
    private static final Logger logger = LoggerFactory.getLogger(NodeConfiguration.class);
    private static final String NAME = "dse.insights.event.node_configuration";
    private static final String MAPPING_VERSION = "oss-node-config-v1";
    private static final ObjectWriter SECURE_WRITER =new ObjectMapper().addMixIn(Object.class, SecureFilterMixIn.class)
            .writer(new SimpleFilterProvider().addFilter("secure filter", new SecurePropertyFilter()));

    @JsonCreator
    public NodeConfiguration(
            @JsonProperty("metadata") InsightMetadata metadata,
            @JsonProperty("data") Data data
    )
    {
        super(
                metadata,
                data
        );
    }

    public NodeConfiguration(Data data)
    {
        super(new InsightMetadata(
                NAME,
                Optional.of(System.currentTimeMillis()),
                Optional.empty(),
                Optional.of(InsightMetadata.InsightType.EVENT),
                Optional.of(MAPPING_VERSION)
        ), data);
    }

    public NodeConfiguration()
    {
        this(new Data());
    }

    public Data getData()
    {
        return (Data) this.data;
    }

    public static class Data
    {

        @JsonProperty("dse_config")
        @JsonRawValue
        public final String dseConfig;

        @JsonProperty("cassandra_config")
        @JsonRawValue
        public final String cassandraConfig;

        @JsonProperty("jvm_version")
        public final String jvmVersion;

        @JsonProperty("jvm_vendor")
        public final String jvmVendor;

        @JsonProperty("jvm_memory_max")
        public final Long jvmMemoryMax;

        @JsonProperty("jvm_final_flags")
        public final List<String> jvmFlags;

        @JsonProperty("jvm_classpath")
        public final String jvmClasspath;

        @JsonProperty("jvm_properties")
        public final Map<String, String> jvmProperties;

        @JsonProperty("jvm_startup_time")
        public final Long jvmStartupTime;

        @JsonProperty("os")
        public final String os;

        @JsonProperty("hostname")
        public final String hostname;

        @JsonProperty("cpu_layout")
        public final List<Map<String, String>> cpuInfo;

        @JsonCreator
        public Data(
                @JsonProperty("dse_config") final Object dseConfig,
                @JsonProperty("cassandra_config") final Object cassandraConfig,
                @JsonProperty("jvm_version") final String jvmVersion,
                @JsonProperty("jvm_vendor") final String jvmVendor,
                @JsonProperty("jvm_memory_max") final Long jvmMemoryMax,
                @JsonProperty("jvm_final_flags") final List<String> jvmFlags,
                @JsonProperty("jvm_classpath") final String jvmClasspath,
                @JsonProperty("jvm_properties") final Map<String, String> jvmProperties,
                @JsonProperty("jvm_startup_time") final Long jvmStartupTime,
                @JsonProperty("os") final String os,
                @JsonProperty("hostname") final String hostname,
                @JsonProperty("cpu_layout") final List<Map<String, String>> cpuInfo)
        {
            this.dseConfig = null;

            String cassandraConf = null;
            try
            {
                cassandraConf = JacksonUtil.writeValueAsString(cassandraConfig);
            }
            catch (JacksonUtil.JacksonUtilException e)
            {
                throw new IllegalArgumentException("Error creating cassandraConfig json", e);
            }

            this.cassandraConfig = cassandraConf;
            this.os = os;
            this.hostname = hostname;
            this.jvmStartupTime = jvmStartupTime;
            this.cpuInfo = cpuInfo;
            this.jvmProperties = jvmProperties;
            this.jvmMemoryMax = jvmMemoryMax;
            this.jvmVersion = jvmVersion;
            this.jvmFlags = jvmFlags;
            this.jvmClasspath = jvmClasspath;
            this.jvmVendor = jvmVendor;
        }

        private Data()
        {
            String cassandraConf = null;
            try {
                Field field = null;
                field = DatabaseDescriptor.class.getDeclaredField("conf");
                field.setAccessible(true); // Suppress Java language access checking
                Config config = (Config) field.get(null);
                cassandraConf = SECURE_WRITER.writeValueAsString(config);
            }
            catch (NoSuchFieldException e) {
                logger.error("DatabaseDescriptor has no config, this should never happen.");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.cassandraConfig = cassandraConf;
            this.dseConfig = null;

            String hostname = "n/a";
            try
            {
                hostname = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException e1)
            {
                logger.info("Could not resolve hostname");
            }
            this.hostname = hostname;

            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

            this.jvmVendor = runtime.getVmVendor();
            this.jvmVersion = runtime.getVmVersion();
            this.jvmClasspath = runtime.getClassPath();
            this.jvmFlags = runtime.getInputArguments();
            this.jvmMemoryMax = Runtime.getRuntime().maxMemory();
            this.jvmProperties = runtime.getSystemProperties();
            this.jvmStartupTime = runtime.getStartTime();

            this.os = System.getProperty("os.name").toLowerCase();

            List<Map<String, String>> tmp = Collections.EMPTY_LIST;
            try
            {
                tmp = ShellUtils.loadCpuMap();
            }
            catch (IOError e)
            {
                if (this.os.contains("linux"))
                    logger.warn("Error reading cpuInfo", e);
            }

            this.cpuInfo = tmp;
        }


    }

    @JsonFilter("secure filter")
    static class SecureFilterMixIn {}

    static class SecurePropertyFilter extends SimpleBeanPropertyFilter {
        private static final Pattern pattern = Pattern.compile("(secret|user|pass)", Pattern.CASE_INSENSITIVE);

        private String createPath(PropertyWriter writer, JsonGenerator jgen)
        {
            StringBuilder path = new StringBuilder();
            path.append(writer.getName());
            JsonStreamContext sc = jgen.getOutputContext();
            if (sc != null)
            {
                sc = sc.getParent();
            }

            while (sc != null)
            {
                if (sc.getCurrentName() != null)
                {
                    if (path.length() > 0)
                    {
                        path.insert(0, ".");
                    }
                    path.insert(0, sc.getCurrentName());
                }
                sc = sc.getParent();
            }
            return path.toString();
        }

        @Override
        public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception
        {
            String path = createPath(writer, jgen);
            if (!pattern.matcher(path).find())
            {
                writer.serializeAsField(pojo, jgen, provider);
            }
            else if (!jgen.canOmitFields())
            {
                writer.serializeAsOmittedField(pojo, jgen, provider);
            }
        }
    }
}

