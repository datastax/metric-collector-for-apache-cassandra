package com.datastax.mcac.interceptors;

import com.datastax.mcac.insights.events.DroppedMessageInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class DroppedMessageLoggingAdvice extends AbstractInterceptor
{
    /*
     * Needs to be visible to redefined loggers
     */
    public static final Pattern DROPPED_MESSAGES_WITH_LATENCY_CONTEXT = Pattern.compile(
            "([_a-zA-Z].*) messages were dropped in last (\\d+) ms: (\\d+) internal and (\\d+) cross node. Mean "
                    + "internal dropped "
                    + "latency: (\\d+) ms and Mean cross-node dropped latency: (\\d+) ms"
    );

    /*
     * Needs to be visible to redefined loggers
     */
    public static final Pattern DROPPED_MESSAGES_WITHOUT_LATENCY_CONTEXT = Pattern.compile(
            "([_a-zA-Z].*) messages were dropped in last (\\d+) ms: (\\d+) for internal timeout and (\\d+) for cross "
                    + "node timeout"
    );

    @Advice.OnMethodEnter
    private static void enterInfo(@Advice.Argument(0) String message)
    {
        try
        {
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
        return ElementMatchers.hasSuperType(named("org.slf4j.Logger")).and(not(isInterface()));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return (builder, typeDescription, classLoader, module) -> builder
                .visit(Advice.to(DroppedMessageLoggingAdvice.class)
                        .on(named("info").and(takesArguments(1)).and(takesArguments(String.class))));
    }
}
