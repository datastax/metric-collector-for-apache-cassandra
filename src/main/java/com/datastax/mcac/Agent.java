package com.datastax.mcac;

import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.events.InsightsClientStarted;
import com.datastax.mcac.utils.JacksonUtil;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.transport.messages.ResultMessage;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.LoggerFactory;

public class Agent {

    private static final Supplier<UnixSocketClient> client = Suppliers.memoize(() -> new UnixSocketClient());

    public static void premain(String arg, Instrumentation inst) throws Exception {

        File temp = Files.createTempDirectory("tmp").toFile();
        Map<TypeDescription, byte[]> injected = new HashMap<>();
        injected.put(new TypeDescription.ForLoadedType(CassandraDaemonInterceptor.class), ClassFileLocator.ForClassLoader.read(CassandraDaemonInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(QueryHandlerInterceptor.class), ClassFileLocator.ForClassLoader.read(QueryHandlerInterceptor.class));

        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(injected);

        new AgentBuilder.Default()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut()) //For debug
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .enableBootstrapInjection(inst, temp)
                .type(ElementMatchers.nameEndsWith(".CassandraDaemon"))
                .transform(new AgentBuilder.Transformer()
                {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("start")).intercept(MethodDelegation.to(CassandraDaemonInterceptor.class));
                    }
                })
                .type(ElementMatchers.isSubTypeOf(QueryHandler.class))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("process")).intercept(MethodDelegation.to(QueryHandlerInterceptor.class));
                    }
                })
                .installOn(inst);
    }

    public static class CassandraDaemonInterceptor
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

            //Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                client.get().close();
            }));
        }
    }

    public static class QueryHandlerInterceptor
    {
        static final String prefix = "CALL InsightsRpc.reportInsight(";

        @RuntimeType
        public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<ResultMessage> zuper) throws Throwable {
            if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof String)
            {
                String query = (String) allArguments[0];
                //LoggerFactory.getLogger(Agent.class).info("Intercepted {}", query);

                int jsonStart = query.indexOf(prefix);
                if (jsonStart >= 0)
                {
                    int jsonEnd = query.lastIndexOf(")");
                    if (jsonEnd > jsonStart)
                    {
                        String json = query.substring(jsonStart + prefix.length() + 1, jsonEnd - 1);
                        if (sendInsight(json))
                        {
                            return new ResultMessage.Void();
                        }
                    }
                }
            }

            return zuper.call();
        }

        static boolean sendInsight(String json)
        {
            Insight insight;
            try
            {
                insight = JacksonUtil.getObjectMapper().readValue(
                        json,
                        Insight.class);

                return client.get().report(insight);
            }
            catch (Exception e)
            {
                String errorMessage = String.format("Error converting JSON to a valid Insight.  JSON received: %s, Error: ", json);
                LoggerFactory.getLogger(Agent.class).warn(errorMessage, e);
            }

            return false;
        }
    }

}