package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.Agent;
import com.datastax.mcac.insights.events.InsightsClientStarted;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class CassandraDaemonInterceptor extends AbstractInterceptor
{
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

        LoggerFactory.getLogger(Agent.class).info("Starting DataStax Metric Collector for Apache Cassandra v0.1");
        client.get().start();

        //Client may drop initial messages during startup, so try a few times
        for (int i = 0; i < 3; i++)
        {
            if (client.get().report(new InsightsClientStarted()))
                break;

            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        }

        //Shutdown hook for client
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.get().close();
        }));
    }
}