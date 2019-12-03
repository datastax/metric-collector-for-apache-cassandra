package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.Agent;
import com.datastax.mcac.insights.events.InsightsClientStarted;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

public class CassandraDaemonInterceptor extends AbstractInterceptor
{
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