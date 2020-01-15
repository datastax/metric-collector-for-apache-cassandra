package com.datastax.mcac.interceptors;

import com.datastax.mcac.insights.events.DroppedMessageInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.cassandra.net.MessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class LoggingInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class.getName());

    private static final Pattern DROPPED_MESSAGES = Pattern.compile(
            "([a-zA-Z].*) messages were dropped in last (\\d+) ms: (\\d+) internal and (\\d+) cross node. Mean "
                    + "internal dropped "
                    + "latency: (\\d+) ms and Mean cross-node dropped latency: (\\d+) ms"
    );

    public static void info(
            @AllArguments Object[] args,
            @SuperCall Callable<?> zuper,
            @This Logger logger
    )
    {
        try
        {
            zuper.call();

            /*
             * TODO we should be able to use instance methods instead, and force
             *
             * instances of LoggingInterceptor to be created that already know
             * if they are for the target class so we aren't doing this work per
             * log message across all classes with Logger instances
             */
            if (logger.getName().equals(MessagingService.class.getName()))
            {
                if (args.length > 0 && args[0] instanceof String)
                {
                    Matcher matcher = DROPPED_MESSAGES.matcher((CharSequence) args[0]);
                    if (matcher.matches() && matcher.groupCount() == 6)
                    {
                        client.get().report(new DroppedMessageInformation(
                                matcher.group(1),
                                Integer.parseInt(matcher.group(2)),
                                Integer.parseInt(matcher.group(3)),
                                Integer.parseInt(matcher.group(4)),
                                Long.parseLong(matcher.group(5)),
                                Long.parseLong(matcher.group(6))
                        ));
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.warn("Error intercepting dropped messages log", ex);
        }
    }

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.hasSuperType(named(Logger.class.getName()));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return (builder, typeDescription, classLoader, javaModule) ->
                builder.method(named("info"))
                        .intercept(MethodDelegation.to(LoggingInterceptor.class));
    }
}
