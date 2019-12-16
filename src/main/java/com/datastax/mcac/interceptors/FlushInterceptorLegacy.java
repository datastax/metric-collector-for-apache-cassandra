package com.datastax.mcac.interceptors;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.FlushInformation;
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
import org.apache.cassandra.db.Memtable;
import org.apache.cassandra.io.sstable.format.SSTableReader;

public class FlushInterceptorLegacy extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(FlushInterceptorLegacy.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("Memtable");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("flush")).intercept(MethodDelegation.to(FlushInterceptorLegacy.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@This Object instance, @SuperCall Callable<Object> zuper) throws Throwable
    {
        long start = System.currentTimeMillis();
        Object rObj = zuper.call();

        try
        {
            long duration = System.currentTimeMillis() - start;
            Collection<SSTableReader> result;
            if (rObj instanceof Collection)
                result = (Collection<SSTableReader>) rObj;
            else
                result = Collections.singletonList((SSTableReader) rObj);

            client.get().report(new FlushInformation((Memtable) instance, result, duration, result.isEmpty()));
        }
        catch (Throwable t)
        {
            logger.info("Problem collecting flush information", t);
        }

        return rObj;
    }
}
