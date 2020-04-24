package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.LargePartitionInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.io.sstable.format.big.BigTableWriter;
import org.apache.cassandra.utils.FBUtilities;

public class LargePartitionInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(LargePartitionInterceptor.class);
    private static final Boolean lock = true;
    private static Long paritionLimit = null;

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".BigTableWriter");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("maybeLogLargePartitionWarning")).intercept(MethodDelegation.to(LargePartitionInterceptor.class));
            }
        };
    }

    private static long getParitionLimit()
    {
        if (paritionLimit != null)
            return paritionLimit;

        synchronized (lock)
        {
            if (paritionLimit != null)
                return paritionLimit;

            Long limit = Long.getLong("mcac.partition_limit_override_bytes", -1L);
            if (limit < 0)
            {
                try
                {
                    Config config = DatabaseDescriptor.loadConfig();
                    Object value = FBUtilities.getProtectedField(Config.class, "compaction_large_partition_warning_threshold_mb").get(config);

                    try
                    {
                        limit = ((Number) value).longValue() * 1024L * 1024L;
                    }
                    catch (Throwable t)
                    {
                        limit = (long) value;
                    }
                }
                catch (Throwable t)
                {
                    logger.info("Error accessing partition limit", t);
                    limit = Long.MAX_VALUE;
                }
            }

            paritionLimit = limit;
        }

        return paritionLimit;
    }

    @RuntimeType
    public static void intercept(@This Object instance, @AllArguments Object[] allArguments, @SuperCall Callable<Void> zuper) throws Throwable
    {
        zuper.call();

        try
        {
            if (allArguments.length == 2 && allArguments[0] != null && allArguments[0] instanceof DecoratedKey)
            {
                BigTableWriter writer = (BigTableWriter) instance;

                long rowSize = (long) allArguments[1];
                if (rowSize > getParitionLimit())
                {
                    client.get().report(new LargePartitionInformation(
                            writer.descriptor.ksname,
                            writer.descriptor.cfname,
                            (DecoratedKey) allArguments[0],
                            rowSize,
                            paritionLimit
                    ));
                }
            }
        }
        catch (Throwable e)
        {
            logger.info("Problem recording large partition", e);
        }
    }
}
