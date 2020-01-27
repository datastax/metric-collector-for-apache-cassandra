package com.datastax.mcac.interceptors;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.CompactionEndedInformation;
import com.datastax.mcac.insights.events.SSTableCompactionInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.db.compaction.CompactionInfo;

public class CompactionEndedInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(CompactionEndedInformation.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".CompactionIterator").or(ElementMatchers.nameEndsWith(".CompactionIterable"));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.nameContainsIgnoreCase("getMergedRowCounts")).intercept(MethodDelegation.to(CompactionEndedInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@This Object instance, @SuperCall Callable<Object> zuper) throws Throwable
    {
        long[] mergedRows = (long[]) zuper.call();

        try
        {
            CompactionInfo ci = (CompactionInfo) instance.getClass().getMethod("getCompactionInfo").invoke(instance);

            long totalRows = 0L;
            for (int i = 0; i < mergedRows.length; i++)
                totalRows += mergedRows[i];

            List<SSTableCompactionInformation> sstables = Collections.emptyList();

            client.get().report(new CompactionEndedInformation(
                    ci.compactionId(),
                    ci.getKeyspace(),
                    ci.getColumnFamily(),
                    ci.getTaskType(),
                    ci.getCompleted(),
                    ci.getTotal(),
                    false,
                    totalRows,
                    0L, //Where oh where will I find this...
                    sstables));

        }
        catch (Throwable t)
        {
            logger.info("Problem collection compaction end report", t);
        }

        return mergedRows;
    }
}
