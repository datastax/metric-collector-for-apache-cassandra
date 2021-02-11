package com.datastax.mcac.interceptors;

import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Metric;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.datastax.mcac.insights.events.InsightsClientStarted;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;

public class CassandraDaemonInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(CassandraDaemonInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".CassandraDaemon");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("start")).intercept(MethodDelegation.to(CassandraDaemonInterceptor.class));
            }
        };
    }

    public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
        zuper.call();

        try
        {
            addJvmMetrics();

            logger.info("Starting DataStax Metric Collector for Apache Cassandra " + Resources.toString(Resources.getResource("build_version"), Charset.defaultCharset()));
            client.get().start();

            //Client may drop initial messages during startup, so try a few times
            for (int i = 0; i < 3; i++)
            {
                if (client.get().report(new InsightsClientStarted()))
                    break;

                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            }

            //Hook into things that have hooks
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                client.get().close();
            }));
        }
        catch (Exception e)
        {
            logger.warn("Problem starting DataStax Metric Collector for Apache Cassandra", e);
        }
    }

    private static void addJvmMetrics()
    {
        try
        {
            Map<String, Metric> metrics = new HashMap<>();
            metrics.put("jvm.buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
            metrics.put("jvm.gc", new GarbageCollectorMetricSet());
            metrics.put("jvm.memory", new MemoryUsageGaugeSet());
            metrics.put("jvm.fd.usage", new FileDescriptorRatioGauge());
            //Add in the JVM metrics
            //ignore IllegalArgumentException thrown if metrics already exist
            for(Map.Entry<String, Metric> entry: metrics.entrySet())
            {
                try
                {
                    CassandraMetricsRegistry.Metrics.register(entry.getKey(), entry.getValue());
                }
                catch (IllegalArgumentException ex)
                {
                    logger.info(ex.toString());
                }
            }
        }
        catch (Throwable t)
        {
            logger.info("Error adding jvm metrics", t);
        }
    }
}