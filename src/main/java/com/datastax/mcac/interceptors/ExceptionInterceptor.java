package com.datastax.mcac.interceptors;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class ExceptionInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);

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
                if (t.getCause() == null)
                {
                    client.get().report(new ExceptionInformation(t));
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