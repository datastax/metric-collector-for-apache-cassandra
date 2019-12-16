package com.datastax.mcac.interceptors;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.DroppedMessageInformation;
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
import org.apache.cassandra.net.MessagingService;

public class StringFormatInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(StringFormatInterceptor.class);
    private static final String droppedMessagesLine = "%s messages were dropped in last %d ms: %d internal and %d cross node."
            + " Mean internal dropped latency: %d ms and Mean cross-node dropped latency: %d ms";

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith("java.lang.String");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("format")).intercept(MethodDelegation.to(StringFormatInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<String> zuper) throws Throwable
    {
        try
        {
            if (allArguments.length > 1 && allArguments[0] instanceof String)
            {
                String fmtLine = (String) allArguments[0];

                if (fmtLine.equals(droppedMessagesLine))
                {
                    MessagingService.Verb verb = (MessagingService.Verb) allArguments[1];
                    int interval = (int) allArguments[2];
                    int droppedInternal = (int) allArguments[3];
                    int droppedCrossNode = (int) allArguments[4];
                    long internalDroppedLatencyMs = (long) allArguments[5];
                    long crossNodeDroppedLatencyMs = (long) allArguments[6];

                    client.get().report(new DroppedMessageInformation(verb, interval, droppedInternal, droppedCrossNode, internalDroppedLatencyMs, crossNodeDroppedLatencyMs));
                }
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem intercepting String.format ", t);
        }

        return zuper.call();
    }
}
