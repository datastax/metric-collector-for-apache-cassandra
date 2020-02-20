package com.datastax.mcac.interceptors;

import com.codahale.metrics.Counter;
import com.datastax.mcac.UnixSocketClient;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.implementation.MethodDelegation.to;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TombstoneFailureInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(TombstoneFailureInterceptor.class);

    private final static Map<String, Map<String, Counter>> keyspaceToTableCounters = new ConcurrentHashMap<>();

    public static void construct(@AllArguments Object[] allArguments)
    {
        try
        {
            String keyspaceName = null;
            String tableName = null;
            if (allArguments.length == 5)
            {
                Field ksName = allArguments[2].getClass().getField("ksName");
                Field cfName = allArguments[2].getClass().getField("cfName");
                keyspaceName = (String) ksName.get(allArguments[2]);
                tableName = (String) cfName.get(allArguments[2]);
            }
            else if(allArguments.length == 7)
            {
                //2.x
                keyspaceName = (String) allArguments[2];
                tableName = (String) allArguments[3];
            }
            else
            {
                return;
            }

            if (!keyspaceToTableCounters.containsKey(keyspaceName))
            {
                keyspaceToTableCounters.put(keyspaceName, new ConcurrentHashMap<>());
            }
            Map<String, Counter> tableCounters = keyspaceToTableCounters.get(keyspaceName);
            if (!tableCounters.containsKey(tableName))
            {
                tableCounters.put(
                        tableName,
                        UnixSocketClient.agentAddedMetricsRegistry.counter(String.format(
                                "com.datastax.mcac.tombstone_failures.%s.%s",
                                keyspaceName,
                                tableName
                        ))
                );
            }
            Counter counter = tableCounters.get(tableName);
            counter.inc();
        }
        catch (Exception ex)
        {
            logger.error("Error intercepting TombstoneOverwhelmingException to count tombstone failures:  ", ex);
        }
    }

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("TombstoneOverwhelmingException");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(
                    DynamicType.Builder<?> builder,
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule javaModule
            )
            {
                //return builder.constructor((ElementMatchers.takesArguments(5).or(ElementMatchers.takesArguments(7)))
                return builder.constructor((takesArguments(5).or(takesArguments(7)).and(isConstructor())))
                        .intercept(to(TombstoneFailureInterceptor.class).andThen(SuperMethodCall.INSTANCE));
            }
        };
    }
}
