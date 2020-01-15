package com.datastax.mcac.interceptors;

import java.util.Collection;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.FlushInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.db.Memtable;
import org.apache.cassandra.io.sstable.format.SSTableReader;

public class FlushInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(FlushInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("ColumnFamilyStore$Flush$1").or(ElementMatchers.nameEndsWith("ColumnFamilyStore$Flush"));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("flushMemtable"))
                        .intercept(MethodDelegation.to(FlushInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<Collection<SSTableReader>> zuper) throws Throwable
    {
        long start = System.currentTimeMillis();
        Collection<SSTableReader> result = zuper.call();
        if (allArguments.length > 0)
        {
            try
            {
                Memtable memtable = (Memtable) allArguments[0];
                long duration = System.currentTimeMillis() - start;

                client.get().report(new FlushInformation(memtable, result, duration, result.isEmpty()));
            }
            catch (Throwable t)
            {
                logger.info("Problem collecting flush information", t);
            }
        }

        return result;
    }
}
