package com.datastax.mcac.interceptors;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
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
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    + "\\(see tombstone_warn_threshold\\)");

    private final static Map<String, Map<String, Counter>> keyspaceToTableCounters = new ConcurrentHashMap<>();

    public static void warn(
            @AllArguments Object[] allArguments,
            @SuperCall Callable<?> zuper
    ) throws Throwable
    {
        zuper.call();

        if (allArguments.length == 1 && allArguments[0] instanceof String)
        {
            String logMessage = (String) allArguments[0];

            if (!logMessage.endsWith("tombstone_warn_threshold)"))
            {
                return;
            }

            try
            {
                Matcher matcher = TOMBSTONE_WARN_PATTERN.matcher(logMessage);
                if (matcher.matches() && matcher.groupCount() == 2)
                {
                    int tombstoneCount = Integer.parseInt(matcher.group(1));
                    String cqlQuery = matcher.group(2);
                    ParsedStatement parsedStatement = QueryProcessor.parseStatement(cqlQuery);

                    if (parsedStatement instanceof CFStatement)
                    {
                        CFStatement cfStatement = (CFStatement) parsedStatement;
                        String tableName = cfStatement.columnFamily();
                        String keyspaceName = cfStatement.keyspace();

                        if (isEmpty(tableName))
                        {
                            logger.debug(
                                    "Could not parse table name from statement: {}",
                                    cqlQuery
                            );
                            return;
                        }

                        if (isEmpty(keyspaceName))
                        {
                            logger.debug(
                                    "Could not parse keyspace name from statement: {}",
                                    cqlQuery
                            );
                            return;
                        }

                        if (!keyspaceToTableCounters.containsKey(keyspaceName))
                        {
                            keyspaceToTableCounters.put(
                                    keyspaceName,
                                    new ConcurrentHashMap<>()
                            );
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
                        }
                        Counter counter = tableCounters.get(tableName);
                        counter.inc(tombstoneCount);
                    }
                }
            }
            catch (Exception ex)
            {
                logger.error(
                        "Error intercepting tombstone warning message:  ",
                        ex
                );
            }
        }
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
            public DynamicType.Builder<?> transform(
                    DynamicType.Builder<?> builder,
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule javaModule
            )
            {
                return builder.method(named("warn"))
                        .intercept(MethodDelegation.to(TombstoneWarningInterceptor.class));
            }
        };
    }
}
