package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.ExceptionInformation;
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

public class ExceptionInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);

    private static final ConcurrentMap<Class, Long> lastException = Maps.newConcurrentMap();

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("JVMStabilityInspector");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("inspectThrowable")).intercept(MethodDelegation.to(ExceptionInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<Void> zuper) throws Throwable
    {
        zuper.call();

        try
        {
            if (allArguments.length == 1 && allArguments[0] instanceof Throwable)
            {
                //Only throw the innermost exception
                Throwable t = (Throwable) allArguments[0];
                Throwable inner = t.getCause() == null ? t : t.getCause();
                if (inner != null)
                {
                    Class rootClass = inner.getClass();
                    long now = System.nanoTime();

                    //Filter the same exception to once every 500ms
                    //to avoid exception storms
                    Long lastSeen = lastException.get(rootClass);
                    if (lastSeen == null || (now - lastSeen) > TimeUnit.MILLISECONDS.toNanos(500))
                    {
                        if (lastSeen == null ? lastException.putIfAbsent(rootClass, now) == now : lastException.replace(rootClass, lastSeen, now))
                        {
                            client.get().report(new ExceptionInformation(t));
                        }
                    }
                }
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem reporting exception: ", t);
        }

        return null;
    }
}