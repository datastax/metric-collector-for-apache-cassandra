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
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.io.sstable.format.big.BigTableWriter;

public class LargePartitionInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(LargePartitionInterceptor.class);
    private static final Long partitionLimitOverride = Long.getLong("mcac.partition_limit_override_bytes", -1L);

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

                long limit = partitionLimitOverride <= 0 ? DatabaseDescriptor.getCompactionLargePartitionWarningThreshold() : partitionLimitOverride;

                if (rowSize > limit)
                {
                    client.get().report(new LargePartitionInformation(
                            writer.metadata.ksName,
                            writer.metadata.cfName,
                            (DecoratedKey) allArguments[0],
                            rowSize,
                            partitionLimitOverride
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
