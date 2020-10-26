package com.datastax.mcac.interceptors;

import com.codahale.metrics.Counter;
import com.datastax.mcac.UnixSocketClient;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.CFStatement;
import org.apache.cassandra.cql3.statements.ParsedStatement;
import org.apache.cassandra.service.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class TombstoneWarningInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(TombstoneFailureInterceptor.class);

    private static final Pattern TOMBSTONE_WARN_PATTERN =
            Pattern.compile("Read \\d+ live rows and (\\d+) tombstone cells for query (.*) "
                    + "\\(see tombstone_warn_threshold\\).*$");

    private static final Pattern TOMBSTONE_2_2_WARN_PATTERN =
            Pattern.compile("Read \\d+ live and (\\d+) tombstone cells in ([_a-zA-Z].*)\\.([_a-zA-Z].*) for key.*$");


    private final static Map<String, Map<String, Counter>> keyspaceToTableCounters = new ConcurrentHashMap<>();

    public static void warn(@AllArguments Object[] allArguments, @SuperCall Callable<?> zuper) throws Throwable
    {
        zuper.call();

        if (allArguments.length == 1 && allArguments[0] instanceof String)
        {
            String logMessage = (String) allArguments[0];

            if (!logMessage.contains("tombstone_warn_threshold"))
            {
                return;
            }

            try
            {
                Matcher matcher = TOMBSTONE_WARN_PATTERN.matcher(logMessage);
                String tableName = null;
                String keyspaceName = null;

                if (matcher.matches() && matcher.groupCount() == 2)
                {
                    String cqlQuery = matcher.group(2);
                    if (cqlQuery.indexOf(';') > 0)
                        cqlQuery = cqlQuery.substring(0, cqlQuery.indexOf(";"));
                    
                    Object statement = QueryProcessor.class.getMethod("parseStatement", String.class).invoke(null, cqlQuery);

                    Method ks = statement.getClass().getMethod("keyspace");
                    Method table = statement.getClass()
                            .getMethod(StorageService.instance.getReleaseVersion().startsWith("4") ? "name" : "columnFamily");

                    tableName = (String)table.invoke(statement);
                    keyspaceName = (String)ks.invoke(statement);
                }
                else
                {
                    matcher = TOMBSTONE_2_2_WARN_PATTERN.matcher(logMessage);
                    if (matcher.matches() && matcher.groupCount() == 3)
                    {
                        keyspaceName = matcher.group(2);
                        tableName = matcher.group(3);
                    }
                }

                if (!isEmpty(keyspaceName) && !isEmpty(tableName))
                {
                    incrementAssociatedCounter(keyspaceName, tableName);
                }
            }
            catch (Throwable ex)
            {
                logger.error(
                        "Error intercepting tombstone warning message:  ",
                        ex
                );
            }
        }
    }

    private static void incrementAssociatedCounter(String keyspaceName, String tableName)
    {
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
                    "com.datastax.mcac.tombstone_warnings.%s.%s",
                            keyspaceName,
                            tableName
                    ))
            );
            logger.info("Added {}", String.format(
                    "com.datastax.mcac.tombstone_warnings.%s.%s",
                    keyspaceName,
                    tableName
            ));
        }
        Counter counter = tableCounters.get(tableName);
        counter.inc();
    }

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("ClientWarn");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(named("warn")).intercept(MethodDelegation.to(TombstoneWarningInterceptor.class));
            }
        };
    }
}
