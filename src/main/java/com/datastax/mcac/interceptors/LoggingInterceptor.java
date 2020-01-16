package com.datastax.mcac.interceptors;

import com.datastax.mcac.insights.events.DroppedMessageInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class LoggingInterceptor extends AbstractInterceptor
{
    private static final Pattern DROPPED_MESSAGES_WITH_LATENCY_CONTEXT = Pattern.compile(
            "([_a-zA-Z].*) messages were dropped in last (\\d+) ms: (\\d+) internal and (\\d+) cross node. Mean "
                    + "internal dropped "
                    + "latency: (\\d+) ms and Mean cross-node dropped latency: (\\d+) ms"
    );

    private static final Pattern DROPPED_MESSAGES_WITHOUT_LATENCY_CONTEXT = Pattern.compile(
            "([_a-zA-Z].*) messages were dropped in last (\\d+) ms: (\\d+) for internal timeout and (\\d+) for cross "
                    + "node timeout"
    );

    public static void info(
            @AllArguments Object[] args,
            @SuperCall Callable<?> zuper,
            @FieldValue("name") String name
    )
    {
        try
        {
            zuper.call();

            if (name != null && name.endsWith("MessagingService"))
            {
                if (args.length > 0 && args[0] instanceof String)
                {
                    String message = (String) args[0];
                    if (message.contains("messages were dropped"))
                    {
                        Matcher matcher = DROPPED_MESSAGES_WITH_LATENCY_CONTEXT.matcher(message);
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
                        else
                        {
                            matcher = DROPPED_MESSAGES_WITHOUT_LATENCY_CONTEXT.matcher(message);
                            if (matcher.matches() && matcher.groupCount() == 4)
                            {
                                client.get().report(new DroppedMessageInformation(
                                        matcher.group(1),
                                        Integer.parseInt(matcher.group(2)),
                                        Integer.parseInt(matcher.group(3)),
                                        Integer.parseInt(matcher.group(4)),
                                        null,
                                        null
                                ));
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            /*
             * Work around, any direct imports of Logger leads to class loading issues
             */
            client.get().logError(
                    "Error intercepting dropped messages log",
                    ex
            );
        }
    }

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.hasSuperType(named("org.slf4j.Logger"));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return (builder, typeDescription, classLoader, javaModule) ->
                builder.method(named("info"))
                        .intercept(MethodDelegation.to(LoggingInterceptor.class));
    }
}
